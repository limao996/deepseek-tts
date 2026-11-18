package io.github.deepseektts.audio
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
@UnstableApi
class PcmProcessorTest {
 @Test fun unityRateIsBitExact() { val input=ByteArray(4800){it.toByte()};val output=mutableListOf<Byte>();val p=PcmProcessor(1f,1f){output.addAll(it.toList())};try{input.toList().chunked(77).forEach{p.accept(it.toByteArray())};p.finish();assertArrayEquals(input,output.toByteArray())}finally{p.release()} }
 @Test fun doubleSpeedStreamsAndHalvesDuration() {val b=ByteBuffer.allocate(48000).order(ByteOrder.LITTLE_ENDIAN);repeat(24000){b.putShort((sin(2*PI*440*it/24000)*8000).toInt().toShort())};var output=0;val p=PcmProcessor(2f,1f){assertEquals(0,it.size%2);output+=it.size};try{b.array().toList().chunked(1021).forEach{p.accept(it.toByteArray())};assertTrue(output>0);p.finish();assertTrue(output in 20000..28000)}finally{p.release()} }
}
