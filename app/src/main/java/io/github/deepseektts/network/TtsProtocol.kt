package io.github.deepseektts.network

import com.google.gson.JsonObject
import com.google.gson.JsonParser

object TtsProtocol {
    const val SAMPLE_RATE = 24_000
    const val MAX_BILLED_CHARACTERS = 10_000
    const val MAX_REQUEST_BYTES = 64 * 1024
    const val FINISH_TEXT = "{\"event\":\"finish_text\"}"

    fun textMessage(text: String): String {
        require(text.isNotBlank()) { "请输入需要朗读的文字" }
        require(billedCharacters(text) <= MAX_BILLED_CHARACTERS) { "文字超过单次 10,000 计费字符限制" }
        val json = JsonObject().apply { addProperty("event", "text"); addProperty("text", text) }.toString()
        require(json.toByteArray(Charsets.UTF_8).size + FINISH_TEXT.length <= MAX_REQUEST_BYTES) { "文字超过 64 KiB 请求限制" }
        return json
    }

    fun billedCharacters(text: String): Int {
        var total = 0
        var offset = 0
        while (offset < text.length) {
            val cp = text.codePointAt(offset)
            total += if (Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN) 2 else 1
            offset += Character.charCount(cp)
        }
        return total
    }

    fun parseFinish(message: String): Finish {
        val obj = JsonParser.parseString(message).asJsonObject
        require(obj.get("event")?.asString == "finish") { "服务端返回了未知事件" }
        val code = requireNotNull(obj.get("code")?.asString) { "合成结果缺少状态码" }
        return Finish(code == "success", code)
    }
    data class Finish(val success: Boolean, val code: String)
}
