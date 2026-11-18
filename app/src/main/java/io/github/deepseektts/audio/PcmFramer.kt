package io.github.deepseektts.audio

/** WebSocket boundaries need not coincide with 16-bit sample boundaries. */
class PcmFramer {
    private var pending: Byte? = null
    fun accept(input: ByteArray): ByteArray {
        val previous = pending
        val combined = if (previous == null) input else byteArrayOf(previous) + input
        val size = combined.size - combined.size % 2
        pending = if (size < combined.size) combined.last() else null
        return combined.copyOf(size)
    }
    fun finish() { check(pending == null) { "音频在半个 PCM 采样处中断" } }
}
