# 鲸语 TTS v0.1.0

Android 系统文字转语音（TTS）引擎，使用 Jetpack Compose + Material 3 Expressive 界面，通过 DeepSeek 官方 WebSocket 流式合成朗读文本。

## 功能
- 实现 `android.speech.tts.TextToSpeechService`，可被系统或其他应用选为 TTS 引擎。
- DeepSeek 官方流式合成：stella（女声）/ tide（男声），24 kHz PCM 16-bit 单声道，边接收边交给系统播放。
- 本地 Media3 Sonic 语速/音调（0.5–2 倍），不伪造服务端不支持的参数。
- 10 种语言的联网 Voice 声明；实际语言由 DeepSeek 根据文本识别。

## 安装与启用
1. 下载 `deepseek-tts-0.1.0.apk` 并安装（Android 8.0 / API 26 及以上，需允许安装未知来源应用）。
2. 打开「鲸语 TTS → 设置」，填入自己的 DeepSeek API Key 并保存（密钥使用 Android Keystore AES-GCM 加密）。
3. 进入系统「文字转语音输出」设置，将「鲸语 TTS」选为首选引擎。
4. 若要允许其他应用使用本引擎朗读，需要在设置中阅读费用与隐私提示并明确开启、保存；默认关闭。

## 安全与隐私边界
- 默认关闭第三方应用调用。开启后其他应用可将文本发送至 DeepSeek，费用计入本机保存的 API Key。
- 本机按 UTC 日最多预留 10,000 个计费字符；这是本机保护上限，不是金额上限，也不能防御 root 或重装绕过。
- 不保存朗读文本、音频或请求日志；备份与设备迁移规则排除保存密钥的 SharedPreferences。
- 应用不内置任何 API Key，运行时需用户自行配置。

## 已验证
- JVM 单元测试 33 项通过（协议序列化与限长、PCM 跨帧对齐、WebSocket 鉴权/取消/背压/超时/完成排空、本地变速、第三方访问策略）。
- Android 14 x86_64 模拟器测试 11 项通过（其中 2 项真实网络测试需显式开启、默认跳过）：系统 TTS 绑定与音色、无凭据错误、Keystore 存取与损坏恢复、本机配额、Compose 设置交互。
- 真实 DeepSeek 合成 1 项通过：stella 与 tide 均通过系统 TTS 完成流式合成，状态达到 COMPLETED 且收到音频数据。
- `assembleRelease`、`lintRelease` 通过，lint 0 error / 13 warning。

## 已知限制
- Material3 `1.5.0-alpha08` 为实验性依赖。
- 未在真机、Android 8 实机、OEM 系统设置、TalkBack、200% 字体或横屏下系统验证。
- 未验证真实 API 的长文本、限流、欠费、内容审核与计费金额。
- 未实现系统 rangeStart 逐字高亮、缓存、导出、后台播放器与断点续传。

## 校验
- APK SHA-256：`cf02f6d2529540ff3c8c973530d85290fa61a73faed7ade6d804f8e07c37bac6`
- 签名证书 SHA-256：`70f632c885d20dd9af76164a3fcb8821c57cc947f18e2b58846803378884df06`
- 证书：CN=DeepSeek TTS, O=limao996, C=CN（RSA 3072，有效期 10000 天）

自行构建需要 JDK 17、Android SDK Platform 36；构建与测试命令见 README。Windows 中文路径下 JVM 测试可能无法加载测试类，建议使用纯英文路径。
