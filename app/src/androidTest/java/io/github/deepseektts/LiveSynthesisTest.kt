package io.github.deepseektts

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.deepseektts.data.EngineSettingsStore
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Explicit opt-in live synthesis. Reads a short-lived key file pushed to the app's
 * external files dir, and never writes the credential into source or logs.
 */
@RunWith(AndroidJUnit4::class)
class LiveSynthesisTest {
    @Test fun bothVoicesProduceStreamedAudio() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveSynthesis") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val keyFile = File(context.getExternalFilesDir(null), "live_key")
        assumeTrue("Live key file is missing", keyFile.isFile)
        val key = keyFile.readText().trim()
        assumeTrue("Live key file is empty", key.isNotBlank())
        val store = EngineSettingsStore(context)
        val original = store.load()
        var tts: TextToSpeech? = null
        val initialized = CountDownLatch(1)
        val initResult = AtomicInteger(Int.MIN_VALUE)
        try {
            store.save(original.copy(apiKey = key))
            instrumentation.runOnMainSync {
                tts = TextToSpeech(context, { initResult.set(it); initialized.countDown() }, context.packageName)
            }
            assertTrue("TTS initialization timed out", initialized.await(15, TimeUnit.SECONDS))
            assertEquals(TextToSpeech.SUCCESS, initResult.get())
            val engine = requireNotNull(tts)
            assertTrue(engine.setLanguage(Locale.SIMPLIFIED_CHINESE) >= TextToSpeech.LANG_AVAILABLE)
            val errors = AtomicInteger(0)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String) = Unit
                override fun onDone(id: String) = Unit
                @Deprecated("platform callback") override fun onError(id: String) { errors.incrementAndGet() }
                override fun onError(id: String, code: Int) { errors.incrementAndGet() }
            })
            for (voiceName in listOf("stella|zh-CN", "tide|zh-CN")) {
                val voice = engine.voices.firstOrNull { it.name == voiceName }
                assertNotNull("Missing voice " + voiceName, voice)
                assertEquals(TextToSpeech.SUCCESS, engine.setVoice(voice))
                val done = CountDownLatch(1)
                val ok = AtomicInteger(Int.MIN_VALUE)
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String) = Unit
                    override fun onDone(id: String) { ok.set(1); done.countDown() }
                    @Deprecated("platform callback") override fun onError(id: String) { ok.set(0); done.countDown() }
                    override fun onError(id: String, code: Int) { ok.set(0); done.countDown() }
                })
                val started = System.nanoTime()
                assertEquals(TextToSpeech.SUCCESS, engine.speak("你好，这是一次真实语音测试。", TextToSpeech.QUEUE_FLUSH, Bundle(), "live-" + voiceName))
                assertTrue("Synthesis timed out for " + voiceName, done.await(90, TimeUnit.SECONDS))
                assertEquals("Synthesis failed for " + voiceName, 1, ok.get())
                val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
                assertTrue("Synthesis suspiciously fast: " + elapsedMs + "ms", elapsedMs >= 300)
                val engineState = io.github.deepseektts.engine.EngineStatusBus.state.value
                assertEquals(io.github.deepseektts.engine.EnginePhase.COMPLETED, engineState.phase)
                assertTrue("Expected streamed audio bytes", engineState.audioBytes > 0)
            }
            assertEquals("No synthesis errors expected", 0, errors.get())
        } finally {
            instrumentation.runOnMainSync { tts?.shutdown() }
            store.save(original)
            keyFile.delete()
        }
    }
}
