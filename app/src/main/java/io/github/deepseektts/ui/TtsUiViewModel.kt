package io.github.deepseektts.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.deepseektts.data.EngineSettings
import io.github.deepseektts.data.EngineSettingsStore
import io.github.deepseektts.data.SettingsStorageException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** UI state never contains a readable API key. The cached credential stays private to this ViewModel. */
data class SettingsUiState(
    val settings: EngineSettings = EngineSettings(),
    val loaded: Boolean = false,
    val saving: Boolean = false,
    val savedKeyAvailable: Boolean = false,
    val storageRecoveryRequired: Boolean = false,
    val dirty: Boolean = false,
    val feedback: UiFeedback? = null,
)

data class UiFeedback(val id: Long, val message: String, val isError: Boolean, val clearKeyInput: Boolean = false)

class TtsUiViewModel(application: Application) : AndroidViewModel(application) {
    private val store = EngineSettingsStore(application.applicationContext)
    private val mutableState = kotlinx.coroutines.flow.MutableStateFlow(SettingsUiState())
    val state: kotlinx.coroutines.flow.StateFlow<SettingsUiState> = mutableState
    private var cachedApiKey: String? = null
    private var savedSnapshot = EngineSettings()
    private var nextFeedbackId = 0L

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                try { LoadResult.Success(store.load()) }
                catch (error: SettingsStorageException) { LoadResult.Recovery(error.message ?: "无法读取已保存密钥") }
                catch (_: Exception) { LoadResult.Recovery("无法读取设置。可输入新的 API Key，或清除密钥后重新保存。") }
            }
            when (loaded) {
                is LoadResult.Success -> {
                    cachedApiKey = loaded.settings.apiKey
                    savedSnapshot = loaded.settings.copy(apiKey = "")
                    mutableState.value = SettingsUiState(
                        settings = loaded.settings.copy(apiKey = ""),
                        loaded = true,
                        savedKeyAvailable = loaded.settings.apiKey.isNotBlank(),
                    )
                }
                is LoadResult.Recovery -> {
                    cachedApiKey = null
                    mutableState.value = SettingsUiState(
                        settings = EngineSettings(), loaded = true, storageRecoveryRequired = true,
                        feedback = notice(loaded.message, isError = true),
                    )
                }
            }
        }
    }

    fun update(transform: (EngineSettings) -> EngineSettings) {
        val current = mutableState.value
        if (!current.loaded || current.saving) return
        val draft = transform(current.settings).copy(apiKey = "")
        mutableState.value = current.copy(settings = draft, dirty = draft != savedSnapshot, feedback = null)
    }

    /** Save only the voice onto the last saved snapshot; unrelated form drafts stay unsaved. */
    fun selectVoice(voiceId: String) {
        val current = mutableState.value
        if (!current.loaded || current.saving) return
        if (cachedApiKey == null) {
            mutableState.value = current.copy(feedback = notice("密钥无法读取。请打开设置重新输入 API Key，或明确清除密钥后再保存音色。", true))
            return
        }
        val selected = if (voiceId == "tide") "tide" else "stella"
        persist(
            savedSnapshot.copy(voiceId = selected), cachedApiKey.orEmpty(),
            draft = current.settings.copy(voiceId = selected), clearInput = false, message = "音色已保存",
        )
    }

    fun save(keyInput: String) {
        val current = mutableState.value
        if (!current.loaded || current.saving) return
        if (current.storageRecoveryRequired && keyInput.isBlank()) {
            mutableState.value = current.copy(feedback = notice("请输入新的 API Key，或选择「清除 API Key」后再保存。", true))
            return
        }
        val key = keyInput.trim().ifEmpty { cachedApiKey.orEmpty() }
        // Explicit Save commits all form drafts atomically.
        persist(current.settings, key, draft = current.settings, clearInput = keyInput.isNotBlank(), message = if (keyInput.isNotBlank()) "API Key 已安全保存，原文不会显示。" else "设置已保存。")
    }

    fun clearKey() {
        val current = mutableState.value
        if (!current.loaded || current.saving) return
        // Clearing credentials must not commit rate, pitch, or preference drafts.
        persist(savedSnapshot, "", draft = current.settings, clearInput = true, message = "API Key 已清除；需要重新保存后才能试听或合成。")
    }

    fun dismissFeedback() {
        mutableState.value = mutableState.value.copy(feedback = null)
    }

    private fun persist(settings: EngineSettings, apiKey: String, draft: EngineSettings, clearInput: Boolean, message: String) {
        val current = mutableState.value
        if (current.saving) return
        val safeDraft = draft.copy(apiKey = "")
        mutableState.value = current.copy(settings = safeDraft, saving = true, dirty = safeDraft != savedSnapshot, feedback = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try { store.save(settings.copy(apiKey = apiKey)); null }
                catch (_: IllegalArgumentException) { "API Key 格式无效，请检查输入后重试。" }
                catch (_: Exception) { "保存失败；原有设置保持不变，请检查输入后重试。" }
            }
            if (result == null) {
                cachedApiKey = apiKey
                savedSnapshot = settings.copy(apiKey = "")
                val retainedDraft = mutableState.value.settings.copy(apiKey = "")
                mutableState.value = mutableState.value.copy(
                    settings = retainedDraft, saving = false, savedKeyAvailable = apiKey.isNotBlank(),
                    storageRecoveryRequired = false, dirty = retainedDraft != savedSnapshot,
                    feedback = notice(message, isError = false, clearKeyInput = clearInput),
                )
            } else {
                mutableState.value = mutableState.value.copy(saving = false, feedback = notice(result, isError = true))
            }
        }
    }

    private fun notice(message: String, isError: Boolean, clearKeyInput: Boolean = false) =
        UiFeedback(++nextFeedbackId, message, isError, clearKeyInput)

    private sealed interface LoadResult {
        data class Success(val settings: EngineSettings) : LoadResult
        data class Recovery(val message: String) : LoadResult
    }
}
