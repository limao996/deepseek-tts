package io.github.deepseektts.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.deepseektts.data.EngineSettings
import io.github.deepseektts.data.EngineSettingsStore
import io.github.deepseektts.BuildConfig
import io.github.deepseektts.data.SettingsStorageException
import io.github.deepseektts.update.AppLinks
import io.github.deepseektts.update.SharedPreferencesUpdateState
import io.github.deepseektts.update.UpdateChecker
import io.github.deepseektts.update.UpdateResult
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
    val updateMessage: String? = null,
    val updateBusy: Boolean = false,
    val updateUrl: String? = null,
    val usage: io.github.deepseektts.billing.UsageSnapshot? = null,
)

data class UiFeedback(val id: Long, val message: String, val isError: Boolean, val clearKeyInput: Boolean = false)

class TtsUiViewModel(application: Application) : AndroidViewModel(application) {
    private val store = EngineSettingsStore(application.applicationContext)
    private val mutableState = kotlinx.coroutines.flow.MutableStateFlow(SettingsUiState())
    val state: kotlinx.coroutines.flow.StateFlow<SettingsUiState> = mutableState
    private var cachedApiKey: String? = null
    private var savedSnapshot = EngineSettings()
    private var nextFeedbackId = 0L
    private val updateState = SharedPreferencesUpdateState(application.applicationContext)
    private val updateChecker = UpdateChecker(okHttpClient(), updateState, BuildConfig.VERSION_NAME)
    private var lastAutomaticCheckMs = 0L
    private val usageRecorder = io.github.deepseektts.billing.UsageRecorder(application.applicationContext)

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
        // Draft edits stay allowed while a save is in flight; the pending write converges afterwards.
        if (!current.loaded) return
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

    /** Non-secret controls save as soon as the user commits a change. */
    fun persistDrafts() {
        val current = mutableState.value
        if (!current.loaded || current.saving || current.storageRecoveryRequired) return
        persist(current.settings, cachedApiKey.orEmpty(), draft = current.settings, clearInput = false, message = "", provideFeedback = false)
    }

    fun clearKey() {
        val current = mutableState.value
        if (!current.loaded || current.saving) return
        // Clearing credentials must not commit rate, pitch, or preference drafts.
        persist(savedSnapshot, "", draft = current.settings, clearInput = true, message = "API Key 已清除；需要重新保存后才能试听或合成。")
    }

    /** One automatic check per process; the checker itself caches for 6 hours on disk. */
    fun checkForUpdatesOnStart() {
        if (lastAutomaticCheckMs != 0L) return
        lastAutomaticCheckMs = System.currentTimeMillis()
        runUpdateCheck(manual = false)
    }

    fun checkForUpdatesManually() = runUpdateCheck(manual = true)

    private fun runUpdateCheck(manual: Boolean) {
        val current = mutableState.value
        if (current.updateBusy) return
        mutableState.value = current.copy(updateBusy = true, updateMessage = mutableState.value.updateMessage)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { updateChecker.check(manual) }.getOrElse { UpdateResult.Failed("更新检查失败，请稍后重试。") } }
            val message: String
            var url: String? = mutableState.value.updateUrl
            when (result) {
                is UpdateResult.Available -> {
                    message = "发现新版本 " + result.latest + "（当前 " + result.current + "）。"
                    url = result.releaseUrl
                }
                is UpdateResult.UpToDate -> {
                    message = if (manual) "已是最新版本 " + result.current + "。" else "当前为最新版本。"
                }
                is UpdateResult.RateLimited -> {
                    val minutes = (result.retryAfterMs / 60_000L).coerceAtLeast(1L)
                    message = "GitHub 访问受限，已暂停自动检查约 " + minutes + " 分钟；可稍后打开仓库手动查看。"
                    url = AppLinks.RELEASES
                }
                is UpdateResult.Failed -> {
                    message = result.message
                }
            }
            mutableState.value = mutableState.value.copy(updateBusy = false, updateMessage = message, updateUrl = url)
        }
    }

    /** Reloads locally recorded usage; called when the UI becomes visible. */
    fun refreshUsage() {
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) { runCatching { usageRecorder.snapshot() }.getOrNull() } ?: return@launch
            mutableState.value = mutableState.value.copy(usage = snapshot)
        }
    }

    fun clearUsageHistory() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val prefs = getApplication<Application>().getSharedPreferences("usage_history", android.content.Context.MODE_PRIVATE)
                prefs.edit().clear().commit()
            }
            refreshUsage()
        }
    }

    fun dismissFeedback() {
        mutableState.value = mutableState.value.copy(feedback = null)
    }

    private fun persist(settings: EngineSettings, apiKey: String, draft: EngineSettings, clearInput: Boolean, message: String, provideFeedback: Boolean = true) {
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
                    feedback = if (provideFeedback) notice(message, isError = false, clearKeyInput = clearInput) else mutableState.value.feedback,
                )
                // Edits made during the write are persisted by the next autosave round.
                if (mutableState.value.settings != savedSnapshot) persistDrafts()
            } else {
                mutableState.value = mutableState.value.copy(saving = false, feedback = notice(result, isError = true))
            }
        }
    }

    private fun notice(message: String, isError: Boolean, clearKeyInput: Boolean = false) =
        UiFeedback(++nextFeedbackId, message, isError, clearKeyInput)

    private fun okHttpClient() = okhttp3.OkHttpClient.Builder()
        .retryOnConnectionFailure(false)
        .build()

    private sealed interface LoadResult {
        data class Success(val settings: EngineSettings) : LoadResult
        data class Recovery(val message: String) : LoadResult
    }
}
