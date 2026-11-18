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
class SettingsPersistenceTest {
    @Test fun nonSecretControlsPersistWithoutPressingSave() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as Application
        val store = EngineSettingsStore(app)
        val original = store.load()
        try {
            store.save(EngineSettings(apiKey = original.apiKey))
            lateinit var vm: TtsUiViewModel
            instrumentation.runOnMainSync { vm = TtsUiViewModel(app) }
            fun await(predicate: (io.github.deepseektts.ui.SettingsUiState) -> Boolean) =
                runBlocking { withTimeout(10_000) { vm.state.first(predicate) } }
            await { it.loaded }

            instrumentation.runOnMainSync {
                vm.update { it.copy(speechRate = 1.6f, filterMarkdown = false, allowThirdPartyRequests = true) }
            }
            instrumentation.runOnMainSync { vm.persistDrafts() }
            await { !it.saving }
            await { runCatching { store.load().speechRate == 1.6f }.getOrDefault(false) }

            val persisted = store.load()
            assertEquals(1.6f, persisted.speechRate, 0f)
            assertFalse(persisted.filterMarkdown)
            assertTrue(persisted.allowThirdPartyRequests)
            assertFalse("Autosave must clear the dirty flag", vm.state.value.dirty)

            // A fresh instance represents relaunching the app.
            lateinit var relaunched: TtsUiViewModel
            instrumentation.runOnMainSync { relaunched = TtsUiViewModel(app) }
            val restored = runBlocking { withTimeout(10_000) { relaunched.state.first { it.loaded } } }
            assertEquals(1.6f, restored.settings.speechRate, 0f)
            assertFalse(restored.settings.filterMarkdown)
        } finally {
            store.save(original)
        }
    }
}
