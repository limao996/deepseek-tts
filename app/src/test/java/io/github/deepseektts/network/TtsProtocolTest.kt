package io.github.deepseektts.network
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
class TtsProtocolTest {
 @Test fun encodesEscapesAndUnicode() { val text="你好\n\"world\" 🐳"; val obj=JsonParser.parseString(TtsProtocol.textMessage(text)).asJsonObject; assertEquals(text,obj["text"].asString); assertEquals("text",obj["event"].asString) }
 @Test fun countsCodePointsAndHan() { assertEquals(8,TtsProtocol.billedCharacters("你好ab🐳!")) }
 @Test fun acceptsLimit() { TtsProtocol.textMessage("你".repeat(5000)) }
 @Test(expected=IllegalArgumentException::class) fun rejectsEmpty() { TtsProtocol.textMessage("  ") }
 @Test(expected=IllegalArgumentException::class) fun rejectsBilledOverflow() { TtsProtocol.textMessage("你".repeat(5001)) }
 @Test fun escapedPayloadStaysWithinByteLimit() { assertTrue(TtsProtocol.textMessage("\u0000".repeat(10000)).toByteArray().size <= TtsProtocol.MAX_REQUEST_BYTES) }
 @Test fun requiresExplicitSuccess() { assertTrue(TtsProtocol.parseFinish("{\"event\":\"finish\",\"code\":\"success\"}").success); assertFalse(TtsProtocol.parseFinish("{\"event\":\"finish\",\"code\":\"service_error\"}").success) }
 @Test(expected=IllegalArgumentException::class) fun rejectsUnrecognizedEvent() { TtsProtocol.parseFinish("{\"event\":\"unknown\",\"code\":\"success\"}") }
 @Test(expected=IllegalArgumentException::class) fun rejectsMissingStatus() { TtsProtocol.parseFinish("{\"event\":\"finish\"}") }
 @Test fun chunkingKeepsShortTextIntact() { assertEquals(listOf("你好"), TtsProtocol.chunkForPlayback("你好", 4000)) }
 @Test fun chunkingRespectsFrameworkCapAndKeepsAllText() { val text="第一句。".repeat(1500); val chunks=TtsProtocol.chunkForPlayback(text,4000); assertTrue(chunks.size>1); assertTrue(chunks.all{it.length<=4000}); assertEquals(text.replace(" ",""), chunks.joinToString("").replace(" ","")) }
 @Test fun chunkingNeverSplitsSurrogatePairs() { val text="🐳".repeat(6); val chunks=TtsProtocol.chunkForPlayback(text,3); chunks.forEach{c->c.forEachIndexed{i,ch-> if(Character.isLowSurrogate(ch)) assertTrue(Character.isHighSurrogate(c[i-1])) } }; assertEquals(text, chunks.joinToString("")) }
 @Test fun chunkingMakesOfficialLimitReachable() { val text="字".repeat(5000); assertEquals(10000, TtsProtocol.billedCharacters(text)); TtsProtocol.textMessage(text); assertTrue(TtsProtocol.chunkForPlayback(text,4000).size>=2) }
}
