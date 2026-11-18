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
 @Test fun quickActionsDoNotPersistUnrelatedDrafts() {
  val instrumentation=InstrumentationRegistry.getInstrumentation()
  val app=instrumentation.targetContext.applicationContext as Application
  val store=EngineSettingsStore(app);val original=store.load()
  try {
   store.save(EngineSettings())
   lateinit var vm:TtsUiViewModel
   instrumentation.runOnMainSync { vm=TtsUiViewModel(app) }
   fun awaitState(predicate:(io.github.deepseektts.ui.SettingsUiState)->Boolean)=runBlocking { withTimeout(10_000) { vm.state.first(predicate) } }
   awaitState { it.loaded }
   instrumentation.runOnMainSync { vm.update { it.copy(speechRate=1.7f,filterMarkdown=false) }; vm.selectVoice("tide") }
   awaitState { !it.saving && it.settings.voiceId=="tide" }
   val afterVoice=store.load();assertEquals("tide",afterVoice.voiceId);assertEquals(1f,afterVoice.speechRate,0f);assertTrue(afterVoice.filterMarkdown);assertTrue(vm.state.value.dirty)
   instrumentation.runOnMainSync { vm.clearKey() }
   awaitState { !it.saving && it.feedback?.message?.contains("已清除")==true }
   assertEquals(1f,store.load().speechRate,0f);assertTrue(store.load().filterMarkdown);assertTrue(vm.state.value.dirty)
   instrumentation.runOnMainSync { vm.save("") }
   awaitState { !it.saving && !it.dirty }
   assertEquals(1.7f,store.load().speechRate,0f);assertFalse(store.load().filterMarkdown)
  } finally {store.save(original)}
 }
}
