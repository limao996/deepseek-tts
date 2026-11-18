# 鲸语 TTS · DeepSeek Android Engine

Jetpack Compose + Material 3 Expressive 的 Android 系统文字转语音引擎。蓝灰底色、圆润卡片、海蓝强调；不是只在应用里播放的 Demo，而是实现 Android TextToSpeechService 的可选择引擎。

## 功能
- DeepSeek 官方 WebSocket 流式合成，Stella（海星）/ Tide（暗潮）两种音色。
- 24 kHz / PCM 16-bit / mono 音频边接收边交给 Android 系统播放。
- 本地 Media3 Sonic 语速/音调处理（0.5–2 倍），不伪造服务端不支持的参数。
- 10种语言的联网 Voice 声明；实际语言由 DeepSeek 根据文本识别。
- API Key 设备内 Android Keystore AES-GCM 加密；应用声明禁用备份，不写日志、不缓存朗读文本/音频。部分 Android 厂商的设备迁移可能不遵循禁用备份设置，复制出去的密文依赖原设备密钥，新设备需重新配置。
- 试听通过本包系统 TTS 服务，支持停止、超时、错误与服务端中断处理，不自动重试或续传。

## 构建
要求 JDK 17、Android SDK Platform 36、Build Tools 35.0.0（AGP会按需选择），网络可访问 Google/Maven Central。固定 AGP 8.13.2、Kotlin 2.2.21、Gradle Wrapper 8.13。Material3 使用 1.5.0-alpha08 以启用公开 Expressive API（1.4.0 的对应主题为内部 API）；升级时需复验该实验 API。

Windows:

gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug

macOS/Linux:

bash gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug

Windows 若项目路径包含中文，AGP/JVM 测试可能无法加载测试类；将工程放在纯英文路径后重试测试。本次验证也使用临时盘符映射，不修改源码位置。

调试 APK：app/build/outputs/apk/debug/app-debug.apk。Android Studio 可直接打开项目；SDK 路径通过 ANDROID_HOME 或本地 local.properties 配置，不提交个人路径。发布版需要自行配置签名。

## 使用
1. 安装调试 APK（最低 Android 8.0 / API 26）。
2. 打开「鲸语 TTS → 设置」，录入自己的 DeepSeek API Key 并保存。不要把密钥写到源码或发到聊天里。
3. 首页输入文字，选择音色并试听。文本将发送至 DeepSeek 云端，需联网且可能产生 API 费用；未保存 Key 不会调用合成。
4. 通过「系统 TTS 设置」进入系统设置，将「鲸语 TTS」选为首选引擎。应用不能静默修改系统首选引擎。不同厂商入口可能位于无障碍/语言与输入法的文字转语音输出设置中。
5. 阅读器等遵循系统 TTS 的应用即可调用；强制自有音频引擎的应用不受该设置控制。

## 约束与隐私
- 只连官方 wss://api.deepseek.com/v1/tools/tts。没有自建代理、离线模型或遥测。
- 单会话最多 10,000 计费字符、64 KiB，中文汉字按2计；Android 客户端本身通常将单次文本限制在4,000字符，长文由调用方分段。
- WebSocket 必须返回 finish/success 才视为成功；输入尚未结束时遵守 DeepSeek 60 秒 idle limit，单次会话最长 30 分钟。发送 finish_text 后不设置未经协议支持的短首帧超时。中断不自动重播，以免重复内容与计费。
- 应用中的首包指标是本次引擎从开始到收到第一批音频的耗时，不是首个声音真正播出的延迟。
- 系统请求的语速/音调由引擎本地处理；应用滑块用于自身试听，其他调用方的参数各自有效。DeepSeek本身不提供语速控制。
- 密钥加密防止磁盘明文泄露，但应用内存、root设备等不在此保护边界内；可以在设置中清除。
- 系统 TTS 引擎默认关闭第三方应用调用。设置页会明确提示：允许后，其他应用可将文本发送至 DeepSeek，费用计入此 API Key；必须由用户确认并保存后才生效。已启用时，本机按 UTC 日最多预留 10,000 个计费字符（不是金额上限，也不能抵御 root/重装绕过），请只在可信设备使用低额度/预算 Key。
- 当前未实现系统 rangeStart 逐字高亮、缓存、导出、后台播放器和断点续传。

## 验证
- JVM: TtsProtocolTest / PcmFramerTest / DeepSeekStreamSessionTest / PcmProcessorTest，覆盖序列化/限长、半采样边界、首帧先于完成、完成前数据排空、鉴权/中断/超时/取消、变速输出。
- 设备: gradlew.bat :app:connectedDebugAndroidTest，包含 Keystore 保存清除、PCM本地处理、普通系统TTS客户端绑定与无密钥错误。
- 手动: 首次配置、错误Key、断网、长文、按停止后立刻再朗读、切换系统默认引擎、深色/大字体/横竖屏。API Key 不随工程提供，真实云端合成需自行配置验证。

实际执行结果在 docs/VERIFICATION.md；未执行不得当作通过。

## 实现结构
- data/：设置与加密密钥，唯一持久化入口。
- network/：官方消息协议、有界PCM队列与取消/终止状态。
- audio/：16bit跨帧对齐与Media3 Sonic。
- engine/：Android系统服务、语言/音色与平台数据检查Activity。
- ui/：Compose界面和自身试听生命周期。

## 官方参考
- https://api-docs.deepseek.com/guides/tts/
- https://developer.android.com/reference/android/speech/tts/TextToSpeechService
- https://developer.android.com/reference/android/speech/tts/SynthesisCallback
- https://developer.android.com/develop/ui/compose/designsystems/material3

界面中的插图由代码绘制；未将用户提供的角色图片打包进应用。
