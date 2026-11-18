package io.github.deepseektts

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.deepseektts.data.EngineSettingsStore
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsUiTest {
 @get:Rule val compose = createAndroidComposeRule<MainActivity>()
 private lateinit var store: EngineSettingsStore
 private lateinit var original: io.github.deepseektts.data.EngineSettings
 @Before fun prepare() { store = EngineSettingsStore(compose.activity); original = store.load() }
 @After fun restore() { store.save(original) }
 @Test fun settingsAcceptsSavesAndClearsCredentialWithoutCallingCloud() {
  compose.onNodeWithText("设置").performClick()
  compose.onNodeWithText("DeepSeek API Key").assertExists()
  val label = if (original.apiKey.isBlank()) "API Key" else "新 API Key（留空保留现有）"
  compose.onNodeWithText(label).performTextInput("test-local-not-a-real-key")
  compose.onNodeWithText("保存设置").performClick()
  compose.waitUntil(10_000) { runCatching { store.load().apiKey == "test-local-not-a-real-key" }.getOrDefault(false) }
  compose.onNodeWithText("清除 API Key").performClick()
  compose.waitUntil(10_000) { runCatching { store.load().apiKey.isEmpty() }.getOrDefault(false) }
 }
}
