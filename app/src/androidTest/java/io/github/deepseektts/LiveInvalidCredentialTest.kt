package io.github.deepseektts

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.deepseektts.data.EngineSettingsStore
import io.github.deepseektts.engine.EnginePhase
import io.github.deepseektts.engine.EngineStatusBus
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Explicit opt-in: sends only a fixed test phrase and a deliberately invalid key to DeepSeek. */
@RunWith(AndroidJUnit4::class)
class LiveInvalidCredentialTest {
    @Test fun officialEndpointAuthenticationFailureReachesSystemClient() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveInvalidCredential") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val store = EngineSettingsStore(context)
        val original = store.load()
        var tts: TextToSpeech? = null
        val initialized = CountDownLatch(1)
        val initResult = AtomicInteger(Int.MIN_VALUE)
        try {
            store.save(original.copy(apiKey = "intentionally-invalid-audit-key", allowThirdPartyRequests = true))
            instrumentation.runOnMainSync {
                tts = TextToSpeech(context, { initResult.set(it); initialized.countDown() }, context.packageName)
            }
            assertTrue("TTS initialization timed out", initialized.await(15, TimeUnit.SECONDS))
            assertEquals(TextToSpeech.SUCCESS, initResult.get())
            val engine = requireNotNull(tts)
            assertTrue(engine.setLanguage(Locale.SIMPLIFIED_CHINESE) >= TextToSpeech.LANG_AVAILABLE)
            val voice = engine.voices.firstOrNull { it.name == "stella|zh-CN" }
            assertNotNull("Expected target engine voice", voice)
            assertEquals(TextToSpeech.SUCCESS, engine.setVoice(voice))
            val done = CountDownLatch(1)
            val status = AtomicInteger(Int.MIN_VALUE)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String) = Unit
                override fun onDone(id: String) { status.set(TextToSpeech.SUCCESS); done.countDown() }
                @Deprecated("platform callback") override fun onError(id: String) { status.set(TextToSpeech.ERROR); done.countDown() }
                override fun onError(id: String, code: Int) { status.set(code); done.countDown() }
            })
            assertEquals(TextToSpeech.SUCCESS, engine.speak("测试", TextToSpeech.QUEUE_FLUSH, Bundle(), "live-invalid-credential"))
            assertTrue("Live invalid-key test timed out", done.await(70, TimeUnit.SECONDS))
            assertEquals("Expected network authentication error", TextToSpeech.ERROR_NETWORK, status.get())
            assertEquals(EnginePhase.ERROR, EngineStatusBus.state.value.phase)
            assertTrue("Expected authentication feedback, not transport failure", EngineStatusBus.state.value.message.contains("API Key"))
        } finally {
            instrumentation.runOnMainSync { tts?.shutdown() }
            store.save(original)
        }
    }
}
