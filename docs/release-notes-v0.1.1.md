# 鲸语 TTS v0.1.1

Android 系统文字转语音（TTS）引擎：Jetpack Compose + Material 3 Expressive 界面，通过 DeepSeek 官方 WebSocket 流式合成朗读文本。

## 本版变化
- 修复：深色模式状态栏区域残留浅色窗口底色（白条）。
- 修复：从首页切换到设置页时会继承首页滚动位置，导致设置页不再从顶部开始。
- 修复：设置页「关于」显示的版本号改为读取构建版本。
- 界面截图与面向用户的 README 改版。

## 功能
- 实现 `android.speech.tts.TextToSpeechService`，可被系统或其他应用选为 TTS 引擎。
- DeepSeek 官方流式合成：stella（女声）/ tide（男声），24 kHz PCM 16-bit 单声道，边接收边交给系统播放。
- 本地 Media3 Sonic 语速/音调（0.5–2 倍），不伪造服务端不支持的参数。
- 10 种语言的联网 Voice 声明；实际语言由 DeepSeek 根据文本识别。

## 安装与启用
1. 下载 `deepseek-tts-0.1.1.apk` 并安装（Android 8.0 / API 26 及以上，需允许安装未知来源应用）。
2. 打开「鲸语 TTS → 设置」，填入自己的 DeepSeek API Key 并保存（Android Keystore AES-GCM 加密）。
3. 进入系统「文字转语音输出」设置，将「鲸语 TTS」选为首选引擎。
4. 第三方应用调用默认开启，便于阅读器开箱即用；可在设置中关闭并保存。

## 安全与隐私边界
- 第三方应用调用默认开启，可随时关闭。其他应用可将文本发送至 DeepSeek，费用计入本机保存的 API Key。
- 本机按 UTC 日最多预留 10,000 个计费字符；这是本机保护上限，不是金额上限，也不能防御 root 或重装绕过。
- 不保存朗读文本、音频或请求日志；备份与设备迁移规则排除保存密钥的 SharedPreferences。
- 应用不内置任何 API Key，运行时需用户自行配置。

## 已验证
- JVM 单元测试 33 项通过。
- Android 14 x86_64 模拟器测试 11 项通过（含 2 项默认跳过的真实网络测试）。
- 真实 DeepSeek 合成 1 项通过：stella 与 tide 均完成流式合成并收到音频。
- `assembleRelease`、`lintRelease` 通过，lint 0 error / 13 warning。

## 已知限制
- Material3 `1.5.0-alpha08` 为实验性依赖。
- 未在真机、Android 8 实机、OEM 系统设置、TalkBack、200% 字体或横屏下系统验证。
- 未验证真实 API 的长文本、限流、欠费、内容审核与计费金额。

## 校验
- APK SHA-256：`628219a3e6209aa054e807750e7af95e1ae08cfac03bb7ddd98219a95e970f9e`
- 签名证书 SHA-256：`70f632c885d20dd9af76164a3fcb8821c57cc947f18e2b58846803378884df06`
