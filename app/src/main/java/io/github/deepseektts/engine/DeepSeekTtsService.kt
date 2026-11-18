package io.github.deepseektts.engine

import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.speech.tts.Voice
import androidx.media3.common.util.UnstableApi
import io.github.deepseektts.audio.PcmProcessor
import io.github.deepseektts.audio.PlaybackRate
import io.github.deepseektts.billing.UsageRecorder
import io.github.deepseektts.data.EngineSettingsStore
import io.github.deepseektts.network.DeepSeekStreamSession
import io.github.deepseektts.network.TtsProtocol
import okhttp3.OkHttpClient
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@UnstableApi
class DeepSeekTtsService : TextToSpeechService() {
    private val active = AtomicReference<Control?>(null)
    private val lifecycleLock = Any()
    @Volatile private var destroyed = false
    // Lazy: field initializers run before the service is attached to a Context.
    private val usage by lazy { UsageRecorder(this) }
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS).retryOnConnectionFailure(false).build()
    @Volatile private var currentLanguage = arrayOf("zho", "CHN", "")

    override fun onGetLanguage(): Array<String> = currentLanguage.copyOf()
    override fun onIsLanguageAvailable(lang: String, country: String, variant: String): Int = VoiceCatalog.availability(lang, country)
    override fun onLoadLanguage(lang: String, country: String, variant: String): Int {
        val result = onIsLanguageAvailable(lang, country, variant)
        if (result >= TextToSpeech.LANG_AVAILABLE) currentLanguage = arrayOf(lang, country, variant)
        return result
    }
    override fun onGetVoices(): MutableList<Voice> = VoiceCatalog.voices.toMutableList()
    override fun onGetDefaultVoiceNameFor(lang: String, country: String, variant: String): String? {
        if (onIsLanguageAvailable(lang, country, variant) < 0) return null
        val locale = VoiceCatalog.locales.firstOrNull { lang == it.language || lang == it.isO3Language } ?: Locale.SIMPLIFIED_CHINESE
        val selected = try { EngineSettingsStore(this).load().voiceId } catch (_: Exception) { "stella" }
        return "$selected|${locale.toLanguageTag()}"
    }
    override fun onIsValidVoiceName(voiceName: String): Int = if (VoiceCatalog.find(voiceName) != null) TextToSpeech.SUCCESS else TextToSpeech.ERROR
    override fun onLoadVoice(voiceName: String): Int = onIsValidVoiceName(voiceName)

    override fun onStop() {
        // WebSocket cancellation can close TLS; keep it off Android's main service thread.
        active.get()?.let { pending -> Thread({ pending.cancel() }, "tts-network-cancel").start() }
    }
    override fun onDestroy() {
        // Closing a pooled TLS socket can write close_notify; Android forbids this on the main thread.
        val pending = synchronized(lifecycleLock) {
            destroyed = true
            active.getAndSet(null)
        }
        Thread({
            try {
                pending?.cancel()
                client.connectionPool.evictAll()
            } finally {
                client.dispatcher.executorService.shutdown()
            }
        }, "tts-network-cleanup").start()
        super.onDestroy()
    }

    override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
        val control = Control()
        val accepted = synchronized(lifecycleLock) {
            if (destroyed) false
            else {
                active.getAndSet(control)?.cancel()
                true
            }
        }
        if (!accepted) {
            callback.error(TextToSpeech.ERROR_SERVICE)
            callback.done()
            return
        }
        val startNs = System.nanoTime()
        var firstAudioMs: Long? = null
        var audioBytes = 0L
        var processor: PcmProcessor? = null
        fun status(phase: EnginePhase, message: String) {
            if (active.get() === control) EngineStatusBus.publish(EngineStatus(phase, message, firstAudioMs, audioBytes, System.currentTimeMillis()))
        }
        try {
            if (control.cancelled) throw Stopped()
            val settings = EngineSettingsStore(this).load()
            // getCallerUid() only exists from API 29; older platforms cannot identify the caller.
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                EngineAccess.denyReason(request.callerUid, applicationInfo.uid, settings.allowThirdPartyRequests)
                    ?.let { throw EngineFailure(it, TextToSpeech.ERROR_SERVICE) }
            }
            if (settings.apiKey.isBlank()) throw EngineFailure("请先打开鲸语 TTS 设置并保存 API Key", TextToSpeech.ERROR_NOT_INSTALLED_YET)
            if (request.params.getBoolean(TextToSpeech.Engine.KEY_FEATURE_EMBEDDED_SYNTHESIS, false))
                throw EngineFailure("此音色需要联网，不支持离线合成", TextToSpeech.ERROR_INVALID_REQUEST)
            if (onIsLanguageAvailable(request.language, request.country, request.variant) < 0)
                throw EngineFailure("暂不支持该语言", TextToSpeech.ERROR_INVALID_REQUEST)
            val text = request.charSequenceText.toString()
            // Only the API's own per-request limit applies; the app never caps daily usage.
            TtsProtocol.textMessage(text)
            if (control.cancelled) throw Stopped()
            val session = DeepSeekStreamSession(client)
            control.attach(session)
            status(EnginePhase.CONNECTING, "正在连接 DeepSeek…")
            val voiceId = VoiceCatalog.find(request.voiceName)?.name?.substringBefore('|') ?: settings.voiceId
            session.start(settings.apiKey, voiceId, settings.filterMarkdown, text)
            if (callback.start(TtsProtocol.SAMPLE_RATE, AudioFormat.ENCODING_PCM_16BIT, 1) != TextToSpeech.SUCCESS) throw Stopped()
            val maximum = (callback.maxBufferSize / 2) * 2
            if (maximum < 2) throw EngineFailure("系统音频缓冲区不可用", TextToSpeech.ERROR_OUTPUT)
            // Android reports caller rate/pitch as percentages (100 = platform default). Third-party
            // readers usually send 100, so the values configured in this app must act as the baseline,
            // otherwise the user's speed/pitch settings never reach third-party playback.
            val ownCall = android.os.Build.VERSION.SDK_INT < 29 || request.callerUid == applicationInfo.uid
            val speed = PlaybackRate.effective(request.speechRate, settings.speechRate, applyBaseline = !ownCall)
            val pitch = PlaybackRate.effective(request.pitch, settings.pitch, applyBaseline = !ownCall)
            processor = PcmProcessor(speed, pitch) { bytes ->
                var offset = 0
                while (offset < bytes.size) {
                    if (control.cancelled) throw Stopped()
                    val size = minOf(maximum, bytes.size - offset)
                    if (callback.audioAvailable(bytes, offset, size) != TextToSpeech.SUCCESS) throw Stopped()
                    offset += size
                }
            }
            var completed = false
            while (!completed) {
                if (control.cancelled) throw Stopped()
                when (val event = session.next()) {
                    is DeepSeekStreamSession.Event.Audio -> {
                        if (event.bytes.isNotEmpty()) {
                            if (firstAudioMs == null) firstAudioMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)
                            audioBytes += event.bytes.size
                            processor.accept(event.bytes)
                            status(EnginePhase.STREAMING, "正在流式合成")
                        }
                    }
                    DeepSeekStreamSession.Event.Complete -> completed = true
                    DeepSeekStreamSession.Event.Cancelled -> throw Stopped()
                    is DeepSeekStreamSession.Event.Failure -> throw failure(event.reason)
                }
            }
            if (audioBytes == 0L) throw EngineFailure("服务没有返回可播放的音频，请调整文本后重试", TextToSpeech.ERROR_SYNTHESIS)
            // Record only after the service confirmed success; failed attempts are never billed.
            runCatching { usage.record(TtsProtocol.billedCharacters(text)) }
            processor.finish()
            if (control.cancelled) throw Stopped()
            status(EnginePhase.COMPLETED, "音频合成完成")
        } catch (_: Stopped) {
            status(EnginePhase.CANCELLED, "已停止朗读")
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            status(EnginePhase.CANCELLED, "已停止朗读")
        } catch (error: EngineFailure) {
            status(EnginePhase.ERROR, error.message ?: "合成失败")
            callback.error(error.code)
        } catch (error: IllegalArgumentException) {
            status(EnginePhase.ERROR, "输入无效，请检查文本与 API Key 设置")
            callback.error(TextToSpeech.ERROR_INVALID_REQUEST)
        } catch (_: Exception) {
            // Never surface raw network exceptions: they may include credentials or text.
            status(EnginePhase.ERROR, "合成失败，请检查设置与网络后重试")
            callback.error(TextToSpeech.ERROR_SYNTHESIS)
        } finally {
            control.cancel()
            processor?.release()
            callback.done() // Android contract requires done even after error.
            active.compareAndSet(control, null)
        }
    }

    private fun failure(reason: DeepSeekStreamSession.Reason): EngineFailure = when (reason) {
        DeepSeekStreamSession.Reason.AUTH -> EngineFailure("API Key 无效或无权限，请在设置中更新", TextToSpeech.ERROR_NETWORK)
        DeepSeekStreamSession.Reason.RATE_LIMIT -> EngineFailure("请求过于频繁，请稍后手动重试", TextToSpeech.ERROR_SERVICE)
        DeepSeekStreamSession.Reason.TIMEOUT -> EngineFailure("等待音频超时，请检查网络后重试", TextToSpeech.ERROR_NETWORK_TIMEOUT)
        DeepSeekStreamSession.Reason.INPUT -> EngineFailure("服务无法朗读该文本，请调整内容", TextToSpeech.ERROR_INVALID_REQUEST)
        DeepSeekStreamSession.Reason.CONTENT_FILTER -> EngineFailure("文本未通过内容审核，请调整内容", TextToSpeech.ERROR_INVALID_REQUEST)
        DeepSeekStreamSession.Reason.NETWORK -> EngineFailure("网络连接中断，请检查网络后重试", TextToSpeech.ERROR_NETWORK)
        DeepSeekStreamSession.Reason.SERVICE -> EngineFailure("合成服务暂不可用，请稍后重试", TextToSpeech.ERROR_SERVICE)
        DeepSeekStreamSession.Reason.PROTOCOL -> EngineFailure("音频流提前结束或响应异常，请重试", TextToSpeech.ERROR_SYNTHESIS)
        DeepSeekStreamSession.Reason.BUFFER_OVERFLOW -> EngineFailure("音频缓冲区已满，已停止；请缩短文本后重试", TextToSpeech.ERROR_OUTPUT)
    }
    private class EngineFailure(message: String, val code: Int) : Exception(message)
    private class Stopped : Exception()
    private class Control {
        @Volatile var cancelled = false
        private var session: DeepSeekStreamSession? = null
        @Synchronized fun attach(value: DeepSeekStreamSession) { session = value; if (cancelled) value.cancel() }
        @Synchronized fun cancel() { cancelled = true; session?.cancel() }
    }
}
