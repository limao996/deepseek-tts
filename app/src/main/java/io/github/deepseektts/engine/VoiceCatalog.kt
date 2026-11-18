package io.github.deepseektts.engine

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

object VoiceCatalog {
    val locales: List<Locale> = listOf("zh-CN", "yue-HK", "en-US", "ja-JP", "ko-KR", "ms-MY", "ar-SA", "id-ID", "th-TH", "vi-VN").map(Locale::forLanguageTag)
    val voices: List<Voice> = locales.flatMap { locale ->
        listOf("stella", "tide").map { voice ->
            Voice("$voice|${locale.toLanguageTag()}", locale, Voice.QUALITY_VERY_HIGH, Voice.LATENCY_HIGH, true, setOf(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS))
        }
    }
    fun availability(language: String, country: String): Int {
        val locale = locales.firstOrNull { language.equals(it.language, true) || language.equals(it.isO3Language, true) || (language == "in" && it.language == "id") }
            ?: return TextToSpeech.LANG_NOT_SUPPORTED
        return if (country.isEmpty()) TextToSpeech.LANG_AVAILABLE
        else if (country.equals(locale.country, true) || country.equals(locale.isO3Country, true)) TextToSpeech.LANG_COUNTRY_AVAILABLE
        else TextToSpeech.LANG_AVAILABLE
    }
    fun find(name: String?): Voice? = voices.firstOrNull { it.name == name }
}
