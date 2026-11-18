package io.github.deepseektts

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.deepseektts.data.EngineSettings
import io.github.deepseektts.engine.EnginePhase
import io.github.deepseektts.engine.EngineStatusBus
import io.github.deepseektts.network.TtsProtocol
import io.github.deepseektts.ui.TtsUiViewModel
import io.github.deepseektts.ui.WhaleMark
import io.github.deepseektts.ui.WhaleTheme
import java.util.Locale
import java.util.UUID

private const val SETTINGS_ACTION = "io.github.deepseektts.OPEN_SETTINGS"
private const val SAMPLE = "你好，我是鲸语。把文字交给海风，让每一句话都自然流淌。"

class MainActivity : ComponentActivity() {
    private var settingsPage by mutableStateOf(false)
    private var confirmExternalConsent by mutableStateOf(false)
    private var tts: TextToSpeech? = null
    private var ttsReady by mutableStateOf(false)
    private var ttsFailed by mutableStateOf(false)
    private var utteranceId by mutableStateOf<String?>(null)
    private var previewMessage by mutableStateOf("准备试听")
    private var previewBusy by mutableStateOf(false)
    private var defaultEngineName by mutableStateOf<String?>(null)
    private var hasResumed = false
    private var ttsInitializing = false
    private var initializationRequest = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsPage = savedInstanceState?.getBoolean("settingsPage") ?: isSettingsIntent(intent)
        sampleText = savedInstanceState?.getString("sampleText") ?: SAMPLE
        confirmExternalConsent = savedInstanceState?.getBoolean("confirmExternalConsent") ?: false
        ensurePreviewTts()
        setContent {
            val vm: TtsUiViewModel = viewModel()
            val ui by vm.state.collectAsState()
            WhaleTheme(ui.settings.dynamicColor) {
                val dark = isSystemInDarkTheme()
                androidx.compose.runtime.SideEffect {
                    @Suppress("DEPRECATION")
                    window.statusBarColor = android.graphics.Color.TRANSPARENT
                    @Suppress("DEPRECATION")
                    window.navigationBarColor = android.graphics.Color.TRANSPARENT
                    if (android.os.Build.VERSION.SDK_INT >= 30) {
                        val light = if (dark) 0 else android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                        window.insetsController?.setSystemBarsAppearance(light, android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
                    }
                }
                WhaleApp(
                    settingsPage = settingsPage,
                    savedSettings = ui.settings,
                    keyConfigured = ui.savedKeyAvailable,
                    recoveryRequired = ui.storageRecoveryRequired,
                    loaded = ui.loaded,
                    saving = ui.saving,
                    settingsDirty = ui.dirty,
                    feedback = ui.feedback?.message,
                    feedbackIsError = ui.feedback?.isError == true,
                    feedbackId = ui.feedback?.id,
                    clearKeyInput = ui.feedback?.clearKeyInput == true,
                    onClearFeedback = vm::dismissFeedback,
                    onNavigate = { settingsPage = it; stopPreview() },
                    onSettingChange = vm::update,
                    onThirdPartyConsentRequest = { confirmExternalConsent = true },
                    onThirdPartyConsentDismiss = { confirmExternalConsent = false },
                    showExternalConsentDialog = confirmExternalConsent,
                    onSave = { vm.save(it) },
                    onClearKey = { vm.clearKey() },
                    onVoiceSelect = vm::selectVoice,
                    onOpenSystemSettings = ::openSystemSettings,
                    defaultEngineName = defaultEngineName,
                    sample = sampleText,
                    onSampleChange = { sampleText = it },
                    onPlay = { voice, rate, pitch -> playPreview(sampleText, voice, rate, pitch) },
                    onStop = ::stopPreview,
                    ttsReady = ttsReady,
                    ttsFailed = ttsFailed,
                    previewBusy = previewBusy,
                    previewMessage = previewMessage,
                )
            }
        }
    }

    private var sampleText by mutableStateOf(SAMPLE)

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("settingsPage", settingsPage)
        outState.putString("sampleText", sampleText)
        outState.putBoolean("confirmExternalConsent", confirmExternalConsent)
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isSettingsIntent(intent)) { stopPreview(); settingsPage = true }
    }

    override fun onResume() {
        super.onResume()
        // Retry only on a later resume, not twice during the initial onCreate/onResume pair.
        if (hasResumed && ttsFailed && tts == null && !ttsInitializing) ensurePreviewTts()
        hasResumed = true
        refreshDefaultEngine()
    }

    override fun onStop() {
        stopPreview()
        super.onStop()
    }

    override fun onDestroy() {
        initializationRequest++
        ttsInitializing = false
        ttsReady = false
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    private fun isSettingsIntent(value: Intent?): Boolean =
        value?.action == SETTINGS_ACTION || value?.component?.className?.endsWith("EngineSettingsActivity") == true

    private fun refreshDefaultEngine() {
        defaultEngineName = if (ttsReady) runCatching { tts?.defaultEngine }.getOrNull() else null
    }

    private fun ensurePreviewTts() {
        if (tts != null || ttsInitializing) return
        ttsFailed = false
        ttsReady = false
        ttsInitializing = true
        val request = ++initializationRequest
        tts = TextToSpeech(this, { result ->
            // Always post: constructor-time failure must run after the instance is assigned.
            android.os.Handler(mainLooper).post {
                if (request != initializationRequest || isDestroyed) return@post
                ttsInitializing = false
                ttsReady = result == TextToSpeech.SUCCESS
                ttsFailed = !ttsReady
                if (ttsReady) {
                    tts?.language = Locale.SIMPLIFIED_CHINESE
                    refreshDefaultEngine()
                } else {
                    tts?.shutdown()
                    tts = null
                    defaultEngineName = null
                    previewMessage = "鲸语系统引擎未就绪，请打开系统文字转语音设置后返回重试。"
                }
            }
        }, packageName)
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                runOnUiThread { if (id != null && id == utteranceId) { previewBusy = true; previewMessage = "正在试听…" } }
            }
            override fun onDone(id: String?) {
                runOnUiThread { if (id != null && id == utteranceId) { utteranceId = null; previewBusy = false; previewMessage = "试听播放完成" } }
            }
            @Deprecated("Use onError with code")
            override fun onError(id: String?) = onError(id, -1)
            override fun onError(id: String?, errorCode: Int) {
                runOnUiThread {
                    if (id == null || id != utteranceId) return@runOnUiThread
                    utteranceId = null
                    previewBusy = false
                    val engine = EngineStatusBus.state.value
                    previewMessage = if (engine.phase == EnginePhase.ERROR) engine.message else when (errorCode) {
                        TextToSpeech.ERROR_NETWORK -> "网络连接失败，请检查网络后重试。"
                        TextToSpeech.ERROR_NETWORK_TIMEOUT -> "网络超时，请稍后重试。"
                        TextToSpeech.ERROR_SERVICE -> "语音服务不可用，请检查 API Key 与设置。"
                        TextToSpeech.ERROR_INVALID_REQUEST -> "语音请求无效，请调整文本或设置。"
                        TextToSpeech.ERROR_OUTPUT -> "音频输出失败，请检查系统音频输出。"
                        else -> "试听播放失败，请检查 API Key、网络与设置后重试。"
                    }
                }
            }
            override fun onStop(id: String?, interrupted: Boolean) {
                runOnUiThread { if (id != null && id == utteranceId) { utteranceId = null; previewBusy = false; previewMessage = "试听已停止" } }
            }
        })
    }

    private fun playPreview(text: String, voice: String, speechRate: Float, pitch: Float) {
        val clean = text.trim()
        if (clean.isEmpty()) { previewMessage = "请输入试听文字。"; return }
        runCatching { TtsProtocol.textMessage(clean) }.exceptionOrNull()?.let { previewMessage = it.message ?: "文本超出限制"; return }
        if (!ttsReady) { previewMessage = "本机鲸语引擎尚未就绪。"; return }
        stopPreview()
        val engine = tts ?: return
        val voiceName = voice + "|zh-CN"
        val selectedVoice = runCatching { engine.voices?.firstOrNull { it.name == voiceName } }.getOrNull()
        if (selectedVoice == null) {
            previewMessage = "所选音色暂不可用。请打开系统文字转语音设置，检查鲸语引擎后重试；本次未提交文本。"
            return
        }
        if (runCatching { engine.setVoice(selectedVoice) }.getOrDefault(TextToSpeech.ERROR) != TextToSpeech.SUCCESS) {
            previewMessage = "无法启用所选音色。请在系统文字转语音设置中检查鲸语引擎后重试；本次未提交文本。"
            return
        }
        engine.setSpeechRate(speechRate.coerceIn(0.5f, 2f))
        engine.setPitch(pitch.coerceIn(0.5f, 2f))
        val id = UUID.randomUUID().toString()
        utteranceId = id
        previewBusy = true
        previewMessage = "正在请求鲸语引擎…"
        if (engine.speak(clean, TextToSpeech.QUEUE_FLUSH, Bundle(), id) != TextToSpeech.SUCCESS) {
            utteranceId = null
            previewBusy = false
            previewMessage = "无法开始试听，请检查系统语音引擎。"
        }
    }

    private fun stopPreview() {
        if (utteranceId != null) tts?.stop()
        utteranceId = null
        previewBusy = false
        previewMessage = "试听已停止"
    }

    private fun openSystemSettings() {
        stopPreview()
        val intent = Intent("com.android.settings.TTS_SETTINGS")
        try {
            if (intent.resolveActivity(packageManager) != null) startActivity(intent)
            else {
                val fallback = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                if (fallback.resolveActivity(packageManager) != null) {
                    startActivity(fallback)
                    previewMessage = "未找到专用 TTS 设置页，已打开系统辅助功能设置。"
                } else previewMessage = "请在系统设置中搜索「文字转语音输出」。"
            }
        } catch (_: Exception) { previewMessage = "无法打开系统 TTS 设置，请在系统设置中搜索「文字转语音输出」。" }
    }
}

@Composable
private fun WhaleApp(
    settingsPage: Boolean,
    savedSettings: EngineSettings,
    keyConfigured: Boolean,
    recoveryRequired: Boolean,
    loaded: Boolean,
    saving: Boolean,
    settingsDirty: Boolean,
    feedback: String?,
    feedbackIsError: Boolean,
    feedbackId: Long?,
    clearKeyInput: Boolean,
    onClearFeedback: () -> Unit,
    onNavigate: (Boolean) -> Unit,
    onSettingChange: ((EngineSettings) -> EngineSettings) -> Unit,
    onThirdPartyConsentRequest: () -> Unit,
    onThirdPartyConsentDismiss: () -> Unit,
    showExternalConsentDialog: Boolean,
    onSave: (String) -> Unit,
    onClearKey: () -> Unit,
    onVoiceSelect: (String) -> Unit,
    onOpenSystemSettings: () -> Unit,
    defaultEngineName: String?,
    sample: String,
    onSampleChange: (String) -> Unit,
    onPlay: (String, Float, Float) -> Unit,
    onStop: () -> Unit,
    ttsReady: Boolean,
    ttsFailed: Boolean,
    previewBusy: Boolean,
    previewMessage: String,
) {
    val engineStatus by EngineStatusBus.state.collectAsState()
    var keyText by remember { mutableStateOf("") }
    var revealKey by rememberSaveable { mutableStateOf(false) }
    var about by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(feedbackId) {
        if (clearKeyInput) { keyText = ""; revealKey = false }
    }
    BackHandler(settingsPage) { onNavigate(false) }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    WhaleMark(size = 48.dp)
                    Column { Text(if (settingsPage) "设置" else "鲸语", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("DeepSeek 流式 TTS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if (settingsPage) TextButton(onClick = { onNavigate(false) }, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回"); Text("返回") }
                else OutlinedButton(onClick = { onNavigate(true) }, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Rounded.Settings, contentDescription = null); Text("设置") }
            }
            if (settingsPage) {
                Text("让文字，自然流淌", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                ApiKeySection(
                    keyConfigured, recoveryRequired, keyText, { keyText = it }, revealKey, { revealKey = !revealKey },
                    loaded, saving, { onSave(keyText) }, onClearKey, feedback, feedbackIsError, onClearFeedback,
                )
                Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("声音调节", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        FactorSetting("语速", savedSettings.speechRate) { v -> onSettingChange { it.copy(speechRate = v) } }
                        FactorSetting("音调", savedSettings.pitch) { v -> onSettingChange { it.copy(pitch = v) } }
                        Preference("过滤 Markdown", "朗读时跳过 Markdown 标记", savedSettings.filterMarkdown) { v -> onSettingChange { it.copy(filterMarkdown = v) } }
                        Preference("动态配色", "跟随支持此功能的系统壁纸", savedSettings.dynamicColor) { v -> onSettingChange { it.copy(dynamicColor = v) } }
                        Preference("允许其他应用调用系统引擎", "默认关闭；其他应用可能把文本发送至 DeepSeek 并使用此 API Key 计费", savedSettings.allowThirdPartyRequests) { enabled ->
                            if (enabled) onThirdPartyConsentRequest() else onSettingChange { it.copy(allowThirdPartyRequests = false) }
                        }
                        Text("本机每日最多允许 10,000 个计费字符；这是本机保护上限，不是金额上限。开启或关闭后，请点击上方保存。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("更改设置后，请在上方保存。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (showExternalConsentDialog) AlertDialog(
                    onDismissRequest = onThirdPartyConsentDismiss,
                    title = { Text("启用第三方系统语音调用？") },
                    text = { Text("其他已安装应用调用系统 TTS 时，可以把它们提供的文本发送至 DeepSeek，费用计入此 API Key。本机只提供每日 10,000 计费字符上限，不限制金额，也不能防御 root 或重装绕过。请只在可信设备使用低额度 Key；确认后仍需点击“保存设置”才生效。") },
                    confirmButton = { TextButton(onClick = { onSettingChange { it.copy(allowThirdPartyRequests = true) }; onThirdPartyConsentDismiss() }) { Text("同意并启用") } },
                    dismissButton = { TextButton(onClick = onThirdPartyConsentDismiss) { Text("取消") } }
                )
                Card(Modifier.fillMaxWidth().clickable { about = !about }, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("关于鲸语 TTS · 版本 0.1.0", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (about) Text("使用 Android 系统 TextToSpeech 服务合成；试听与系统合成使用同一引擎。", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                Card(shape = RoundedCornerShape(32.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("让文字，自然流淌", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("用 DeepSeek 云端声音，读出每一段文字。", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Pill(if (keyConfigured && !recoveryRequired) "API Key 已安全保存" else "请先配置 API Key", keyConfigured && !recoveryRequired)
                    }
                }
                Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("系统引擎", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("API Key：" + if (keyConfigured && !recoveryRequired) "已配置" else "未配置", style = MaterialTheme.typography.bodyLarge)
                        Text("系统默认引擎：" + when (defaultEngineName) { packageNameForStatus() -> "鲸语 TTS"; null -> "暂不可读取"; else -> "其他引擎" }, style = MaterialTheme.typography.bodyLarge)
                        Text("最近一次系统合成：" + engineStatus.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (engineStatus.firstAudioMs != null && (engineStatus.phase == EnginePhase.STREAMING || engineStatus.phase == EnginePhase.COMPLETED)) Text("首包音频：" + engineStatus.firstAudioMs + " ms", style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = onOpenSystemSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = CircleShape) { Icon(Icons.Rounded.OpenInNew, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("系统文字转语音设置") }
                    }
                }
                Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("选择音色", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            VoiceChoice("海星", "Stella · 女声", savedSettings.voiceId == "stella", Modifier.weight(1f)) { onVoiceSelect("stella") }
                            VoiceChoice("暗潮", "Tide · 男声", savedSettings.voiceId == "tide", Modifier.weight(1f)) { onVoiceSelect("tide") }
                        }
                    }
                }
                Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("试听", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        if (settingsDirty) Text("试听使用尚未保存的语速和音调；保存后设置才会保留。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        val limitError = remember(sample) { if (sample.isBlank()) null else runCatching { TtsProtocol.textMessage(sample) }.exceptionOrNull()?.message }
                        OutlinedTextField(sample, onSampleChange, modifier = Modifier.fillMaxWidth(), label = { Text("试听文字") }, minLines = 3, maxLines = 6, shape = RoundedCornerShape(20.dp), isError = limitError != null, supportingText = { Text(limitError ?: "计费字符 " + TtsProtocol.billedCharacters(sample) + " / " + TtsProtocol.MAX_BILLED_CHARACTERS) })
                        Text(previewMessage, style = MaterialTheme.typography.bodyMedium, color = if (ttsFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        if (previewBusy) Button(onClick = onStop, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = CircleShape) { Icon(Icons.Rounded.Stop, contentDescription = null); Text("停止试听") }
                        else Button(onClick = { if (!keyConfigured || recoveryRequired) onNavigate(true) else onPlay(savedSettings.voiceId, savedSettings.speechRate, savedSettings.pitch) }, enabled = loaded && (!keyConfigured || recoveryRequired || ttsReady) && sample.isNotBlank() && limitError == null, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = CircleShape) { Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null); Text(if (!keyConfigured || recoveryRequired) "先配置 API Key" else if (ttsReady) "播放试听" else "引擎准备中…") }
                    }
                }
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.WarningAmber, contentDescription = null)
                        Text("朗读文字会发送至 DeepSeek 进行云端合成，需要网络，并可能产生 API 费用。保存 API Key 前，不会提交朗读内容。", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (!settingsPage && feedback != null) Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = if (feedbackIsError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Text(feedback, Modifier.weight(1f)); TextButton(onClick = onClearFeedback) { Text("关闭") } }
            }
        }
    }
}

@Composable
private fun ApiKeySection(configured: Boolean, recovery: Boolean, value: String, onValue: (String) -> Unit, shown: Boolean, onToggle: () -> Unit, loaded: Boolean, saving: Boolean, onSave: () -> Unit, onClear: () -> Unit, feedback: String?, feedbackIsError: Boolean, onDismissFeedback: () -> Unit) {
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary); Text("DeepSeek API Key", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
            Text(if (configured) "密钥已加密保存，原文不会回显。输入新值可替换。" else "输入密钥并保存后才会使用云端服务。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (recovery) Text("已保存密钥无法解密。可输入新密钥覆盖，或清除密钥后重新保存。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(value, { if (!saving && it.length <= 4096 && it.none(Char::isISOControl)) onValue(it) }, enabled = loaded && !saving, modifier = Modifier.fillMaxWidth(), label = { Text(if (configured) "新 API Key（留空保留现有）" else "API Key") }, placeholder = { Text("sk-…") }, singleLine = true, visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), trailingIcon = { TextButton(onClick = onToggle, enabled = loaded && !saving) { Text(if (shown) "遮挡" else "显示") } }, shape = RoundedCornerShape(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onSave, enabled = loaded && !saving, modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = CircleShape) { Icon(Icons.Rounded.CheckCircle, contentDescription = null); Text(if (saving) "保存中" else "保存设置") }
                OutlinedButton(onClick = onClear, enabled = loaded && !saving && (configured || recovery || value.isNotEmpty()), modifier = Modifier.heightIn(min = 48.dp), shape = CircleShape) { Icon(Icons.Rounded.Close, contentDescription = null); Text("清除 API Key") }
            }
            if (feedback != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(feedback, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = if (feedbackIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    TextButton(onClick = onDismissFeedback) { Text("关闭") }
                }
            }
        }
    }
}

@Composable
private fun FactorSetting(label: String, value: Float, change: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, style = MaterialTheme.typography.titleMedium); Text(String.format(Locale.getDefault(), "%.1f×", value), style = MaterialTheme.typography.titleMedium) }
        Slider(value, change, valueRange = .5f..2f, steps = 14, modifier = Modifier.semantics { contentDescription = label + " " + value + "倍" })
    }
}

@Composable
private fun Preference(title: String, detail: String, value: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 68.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(value, change, modifier = Modifier.semantics { contentDescription = title })
    }
}

@Composable
private fun VoiceChoice(title: String, subtitle: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier.selectable(selected = selected, onClick = onClick, role = Role.RadioButton), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); if (selected) Icon(Icons.Rounded.CheckCircle, "已选中", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Pill(text: String, good: Boolean) {
    Card(shape = CircleShape, colors = CardDefaults.cardColors(containerColor = if (good) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)) {
        Text(text, Modifier.padding(horizontal = 14.dp, vertical = 9.dp), style = MaterialTheme.typography.labelLarge)
    }
}

private fun packageNameForStatus(): String = "io.github.deepseektts"