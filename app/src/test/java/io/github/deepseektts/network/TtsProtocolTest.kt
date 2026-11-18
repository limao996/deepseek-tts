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
}
