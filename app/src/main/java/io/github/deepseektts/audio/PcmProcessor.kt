package io.github.deepseektts.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import io.github.deepseektts.network.TtsProtocol
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** One utterance, one processor: independent speed and pitch, always 24 kHz PCM16 mono. */
@UnstableApi
class PcmProcessor(speed: Float, pitch: Float, private val emit: (ByteArray) -> Unit) {
    private val framer = PcmFramer()
    private val sonic = SonicAudioProcessor().apply {
        setSpeed(speed.coerceIn(0.5f, 2f))
        setPitch(pitch.coerceIn(0.5f, 2f))
        configure(AudioProcessor.AudioFormat(TtsProtocol.SAMPLE_RATE, 1, C.ENCODING_PCM_16BIT))
        flush()
    }
    private val active = sonic.isActive
    fun accept(bytes: ByteArray) {
        val framed = framer.accept(bytes)
        if (framed.isEmpty()) return
        if (!active) { emit(framed); return }
        val input = ByteBuffer.allocateDirect(framed.size).order(ByteOrder.nativeOrder()).put(framed)
        input.flip()
        sonic.queueInput(input)
        drain()
    }
    fun finish() {
        framer.finish()
        if (!active) return
        sonic.queueEndOfStream()
        drain()
        check(sonic.isEnded) { "音频处理尚未结束" }
    }
    private fun drain() {
        val output = sonic.output
        if (output.hasRemaining()) emit(ByteArray(output.remaining()).also(output::get))
    }
    fun release() = sonic.reset()
}
