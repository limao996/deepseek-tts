package io.github.deepseektts.engine

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech

class CheckVoiceDataActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val voices = ArrayList(VoiceCatalog.locales.map { "${it.isO3Language}-${it.isO3Country}" })
        setResult(TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, Intent()
            .putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, voices)
            .putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, arrayListOf()))
        finish()
    }
}
class InstallVoiceDataActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent("io.github.deepseektts.OPEN_SETTINGS").setClassName(packageName, "$packageName.MainActivity"))
        finish()
    }
}
class SampleTextActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(TextToSpeech.LANG_AVAILABLE, Intent().putExtra(TextToSpeech.Engine.EXTRA_SAMPLE_TEXT, "这是一段示例文本，用于预览当前语音引擎的朗读效果。"))
        finish()
    }
}
