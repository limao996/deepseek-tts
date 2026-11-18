package io.github.deepseektts.audio
import org.junit.Assert.*
import org.junit.Test
class PcmFramerTest {
 @Test fun preservesOddChunks() { val framer=PcmFramer(); assertArrayEquals(byteArrayOf(),framer.accept(byteArrayOf(1))); assertArrayEquals(byteArrayOf(1,2,3,4),framer.accept(byteArrayOf(2,3,4))); framer.finish() }
 @Test fun emptyChunkDoesNotDropPendingByte() { val framer=PcmFramer(); framer.accept(byteArrayOf(1)); assertTrue(framer.accept(byteArrayOf()).isEmpty()); assertArrayEquals(byteArrayOf(1,2),framer.accept(byteArrayOf(2))); framer.finish() }
 @Test(expected=IllegalStateException::class) fun rejectsTruncatedSample() { val framer=PcmFramer(); framer.accept(byteArrayOf(1,2,3)); framer.finish() }
 @Test fun preservesAllSamplesAcrossArbitraryBoundaries() { val input=ByteArray(24000){it.toByte()}; val framer=PcmFramer(); val output=input.toList().chunked(73).flatMap{framer.accept(it.toByteArray()).toList()}.toByteArray(); framer.finish(); assertArrayEquals(input,output) }
}
