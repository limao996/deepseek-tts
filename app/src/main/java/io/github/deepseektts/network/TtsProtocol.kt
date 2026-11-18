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

    /**
     * Splits text so it can be passed to [android.speech.tts.TextToSpeech.speak], which accepts
     * at most [android.speech.tts.TextToSpeech.getMaxSpeechInputLength] characters per call
     * (4,000 on Android). The engine itself accepts the official limit of 10,000 billed
     * characters and 64 KiB per request, so chunking keeps that full range usable.
     *
     * Splits on sentence punctuation when possible and never breaks a surrogate pair.
     */
    fun chunkForPlayback(text: String, maxChars: Int): List<String> {
        require(maxChars >= 2) { "分块长度至少为 2" }
        if (text.length <= maxChars) return listOf(text)
        val chunks = ArrayList<String>()
        var start = 0
        while (start < text.length) {
            if (text.length - start <= maxChars) {
                chunks += text.substring(start).trim()
                break
            }
            var end = start + maxChars
            if (Character.isHighSurrogate(text[end - 1])) end -= 1
            val boundary = lastSentenceBoundary(text, start, end)
            val cut = if (boundary > start) boundary else end
            val piece = text.substring(start, cut).trim()
            if (piece.isNotEmpty()) chunks += piece
            start = cut
            while (start < text.length && text[start].isWhitespace()) start += 1
        }
        return chunks.filter { it.isNotEmpty() }
    }

    private fun lastSentenceBoundary(text: String, start: Int, end: Int): Int {
        for (i in end - 1 downTo start) {
            if (text[i] in SENTENCE_ENDINGS) return i + 1
        }
        return start
    }

    private const val SENTENCE_ENDINGS = "。！？!?；;\n"

    fun parseFinish(message: String): Finish {
        val obj = JsonParser.parseString(message).asJsonObject
        require(obj.get("event")?.asString == "finish") { "服务端返回了未知事件" }
        val code = requireNotNull(obj.get("code")?.asString) { "合成结果缺少状态码" }
        return Finish(code == "success", code)
    }
    data class Finish(val success: Boolean, val code: String)
}
