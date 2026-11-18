package io.github.deepseektts

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.deepseektts.data.EngineSettings
import io.github.deepseektts.data.EngineSettingsStore
import io.github.deepseektts.ui.TtsUiViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsDraftTest {
    /** Non-secret controls autosave; quick actions must not roll saved values backwards. */
    @Test fun autosavedControlsSurviveQuickActions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as Application
        val store = EngineSettingsStore(app)
        val original = store.load()
        try {
            store.save(EngineSettings(apiKey = original.apiKey))
            lateinit var vm: TtsUiViewModel
            instrumentation.runOnMainSync { vm = TtsUiViewModel(app) }
            fun awaitState(predicate: (io.github.deepseektts.ui.SettingsUiState) -> Boolean) =
                runBlocking { withTimeout(10_000) { vm.state.first(predicate) } }
            awaitState { it.loaded }

            instrumentation.runOnMainSync { vm.update { it.copy(speechRate = 1.7f, filterMarkdown = false) } }
            instrumentation.runOnMainSync { vm.persistDrafts() }
            awaitState { !it.saving && !it.dirty }
            val afterAutosave = store.load()
            assertEquals(1.7f, afterAutosave.speechRate, 0f)
            assertFalse(afterAutosave.filterMarkdown)

            instrumentation.runOnMainSync { vm.selectVoice("tide") }
            awaitState { !it.saving && it.settings.voiceId == "tide" }
            val afterVoice = store.load()
            assertEquals("tide", afterVoice.voiceId)
            assertEquals("Voice switch must not roll back saved rate", 1.7f, afterVoice.speechRate, 0f)
            assertFalse(afterVoice.filterMarkdown)

            instrumentation.runOnMainSync { vm.clearKey() }
            awaitState { !it.saving && it.feedback?.message?.contains("已清除") == true }
            val afterClear = store.load()
            assertEquals("", afterClear.apiKey)
            assertEquals(1.7f, afterClear.speechRate, 0f)
        } finally {
            store.save(original)
        }
    }
}
