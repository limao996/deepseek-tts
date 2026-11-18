package io.github.deepseektts.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Listener only enqueues bytes. Consumers own audio callbacks and the synthesis thread. */
class DeepSeekStreamSession(
    private val client: OkHttpClient,
    private val endpoint: String = "wss://api.deepseek.com/v1/tools/tts",
    private val idleTimeoutMs: Long = TimeUnit.SECONDS.toMillis(60),
    private val maxSessionMs: Long = TimeUnit.MINUTES.toMillis(30),
) {
    sealed interface Event {
        data class Audio(val bytes: ByteArray) : Event
        data object Complete : Event
        data object Cancelled : Event
        data class Failure(val reason: Reason) : Event
    }
    enum class Reason { AUTH, RATE_LIMIT, NETWORK, TIMEOUT, SERVICE, INPUT, CONTENT_FILTER, PROTOCOL, BUFFER_OVERFLOW }
    private val queue = ArrayBlockingQueue<ByteArray>(96)
    private val terminal = AtomicReference<Event?>(null)
    private val socket = AtomicReference<WebSocket?>(null)
    private val lock = Any()
    @Volatile private var lastActivity = System.nanoTime()
    @Volatile private var textFinished = false
    private var started = System.nanoTime()

    fun start(apiKey: String, voiceId: String, markdown: Boolean, text: String) {
        val message = TtsProtocol.textMessage(text)
        require(apiKey.isNotBlank() && apiKey.length <= 4096 && apiKey.all { it.code in 33..126 }) { "请先保存有效的 API Key" }
        started = System.nanoTime()
        lastActivity = started
        val separator = if ('?' in endpoint) "&" else "?"
        val voice = if (voiceId == "tide") "tide" else "stella"
        val request = Request.Builder().url("$endpoint${separator}voice_id=$voice&format=pcm&filter_markdown=$markdown")
            .header("Authorization", "Bearer $apiKey").build()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                synchronized(lock) {
                    if (terminal.get() != null) { webSocket.cancel(); return }
                    lastActivity = System.nanoTime()
                    if (!webSocket.send(message) || !webSocket.send(TtsProtocol.FINISH_TEXT)) fail(Reason.NETWORK)
                    else textFinished = true
                }
            }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (terminal.get() != null) return
                // The API does not guarantee binary-message sizes; retain only small queue blocks.
                var offset = 0
                try {
                    while (offset < bytes.size) {
                        if (terminal.get() != null) return
                        val end = minOf(offset + 16_384, bytes.size)
                        val block = bytes.substring(offset, end).toByteArray()
                        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45)
                        while (!queue.offer(block, 100, TimeUnit.MILLISECONDS)) {
                            if (terminal.get() != null) return
                            if (System.nanoTime() >= deadline) { fail(Reason.BUFFER_OVERFLOW); return }
                        }
                        lastActivity = System.nanoTime()
                        offset = end
                    }
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    fail(Reason.NETWORK)
                }
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val finish = TtsProtocol.parseFinish(text)
                    if (finish.success) {
                        synchronized(lock) { terminal.compareAndSet(null, Event.Complete) }
                        webSocket.close(1000, null)
                    } else fail(when (finish.code) {
                        "invalid_input" -> Reason.INPUT
                        "content_filter" -> Reason.CONTENT_FILTER
                        "input_timeout" -> Reason.TIMEOUT
                        else -> Reason.SERVICE
                    })
                } catch (_: Exception) { fail(Reason.PROTOCOL) }
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                if (terminal.get() == null) fail(Reason.PROTOCOL)
                webSocket.close(code, null)
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (terminal.get() == null) fail(Reason.PROTOCOL)
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val reason = when (response?.code) {
                    401, 403 -> Reason.AUTH
                    429 -> Reason.RATE_LIMIT
                    400 -> Reason.INPUT
                    503 -> Reason.SERVICE
                    504 -> Reason.TIMEOUT
                    else -> Reason.NETWORK
                }
                response?.close()
                fail(reason)
            }
        }
        // Linearize cancellation with socket creation: a session stopped before start never connects.
        synchronized(lock) {
            if (terminal.get() != null) return
            socket.set(client.newWebSocket(request, listener))
        }
    }

    /** Blocks only the dedicated Android synthesis thread; never called on the UI thread. */
    fun next(): Event {
        while (true) {
            when (val end = terminal.get()) {
                is Event.Failure, Event.Cancelled -> return end
                else -> Unit
            }
            queue.poll(100, TimeUnit.MILLISECONDS)?.let { return Event.Audio(it) }
            terminal.get()?.let { end ->
                // Completion can race the timed poll; drain bytes enqueued just before finish.
                if (end == Event.Complete) queue.poll()?.let { return Event.Audio(it) }
                return end
            }
            val now = System.nanoTime()
            // DeepSeek guarantees a 60s idle-input limit and 30-minute session limit,
            // but does not guarantee that the first audio frame arrives within 45s.
            // Once finish_text is sent, rely on the documented overall session limit.
            if ((!textFinished && TimeUnit.NANOSECONDS.toMillis(now - lastActivity) >= idleTimeoutMs) ||
                TimeUnit.NANOSECONDS.toMillis(now - started) >= maxSessionMs) fail(Reason.TIMEOUT)
        }
    }

    fun cancel() {
        synchronized(lock) { terminal.set(Event.Cancelled); queue.clear() }
        socket.get()?.cancel()
    }

    private fun fail(reason: Reason) {
        synchronized(lock) {
            if (!terminal.compareAndSet(null, Event.Failure(reason))) return
            queue.clear()
        }
        socket.get()?.cancel()
    }
}
