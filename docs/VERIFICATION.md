# 验证记录（2026-11-18）

## 已执行
- 环境：Windows、Temurin JDK 17、Android SDK 36、Gradle Wrapper 8.13。中文工作目录下 Windows 的 JVM 测试类加载失败（实际 ClassNotFoundException，编译后 class 文件存在）；临时映射到英文盘符 Z: 执行，结束后移除映射。依赖经本机代理下载，未把代理地址写进工程。
- 执行 :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug --continue --no-daemon，退出码 0，BUILD SUCCESSFUL。调试 APK SHA-256: 151886dd3c7ddfdb3aa9314cb33aeab02e27c42c049217a315ae4ef674ce059a。
- JVM 测试 28 项，0 失败，覆盖协议、限长、认证/速率错误、首帧先于完成、末尾排空、2.4MB 背压、提前关闭、超时、先停止再连接、PCM跨帧对齐与本地变速。使用本地 MockWebServer，不连接真实 DeepSeek。
- Android 14/API34 x86_64 模拟器测试 8 项，0 失败：Keystore 加密和清除、损坏密钥恢复、语言目录、PCM流处理、外部系统TTS客户端绑定/无密钥错误、Compose保存/清除、草稿隔离。界面首页成功安装启动并截图。
- lintDebug：0 error、13 warning；包含旧版本依赖提示和系统引擎必须公开带来的 ExportedService 提示。系统 TTS 服务必须导出让普通阅读器可绑定；为此加入默认拒绝策略：非本应用 UID 需用户在设置页明确确认第三方调用与费用提示后才可使用，并有本机 UTC 日 10,000 计费字符上限（EngineAccessTest 覆盖策略，EngineDeviceTest 覆盖配额）。
- 独立规格/质量审查覆盖流式回调线程、取消、凭据、界面草稿与回退；发现项已修复并重测。真实无效 API Key 的 API34 系统 TTS 回归曾复现 NetworkOnMainThreadException，随后把 TLS/OkHttp 清理移至后台线程，并用相同官方端点回归通过。补充修复了 finish_text 后不以45秒无音频误杀合法慢合成；新增 slow-first-audio 回归。无旧代码，无迁移或替换分支；组件均为当前任务创建。

## 未执行/边界
- 已用测试方临时提供、仅保存在应用私有外部文件中的真实 API Key 在 Android 14 模拟器完成一次真实合成：stella 与 tide 均通过系统 TextToSpeechService 绑定、官方 WebSocket PCM 流、合成完成回调返回 onDone，且 EngineStatus 达到 COMPLETED、audioBytes > 0。测试后设备文件与主机临时文件均已删除，仓库未包含该密钥；该密钥已在聊天中暴露，应视为需轮换。
- 尚未验证真实 API 下的长文本、限流、欠费、内容审核与计费金额；未在真机或 Android 8 模拟器验证。
- 未系统化测试 TalkBack、200%字体、横屏、多窗口、OEM系统设置/设备迁移、与第三方阅读器端到端朗读。
- Material3 1.5.0-alpha08 为实验性依赖；稳定升级要重新编译与验证。调试 APK 使用 debug 签名，不能当作正式发布包；生产发布需自配签名。
- lint警告须在发布前评估；公开TTS服务是系统引擎本身的必要边界，不添加会阻止普通客户端的 BIND_TEXT_SERVICE 权限。默认关闭第三方调用降低了滥用面，但用户主动开启后仍可能被已安装应用使用账户，属已声明的产品边界。
- 备份：Android 12+ dataExtractionRules 与旧版 backup rules 均排除 engine_settings SharedPreferences（含加密密钥）；未在真机/厂商迁移流程实测。
- 尚未生成正式签名 release（release 构建产物为 unsigned APK），也尚未验证真机、Android 8、TalkBack、200% 字体、横屏、OEM TTS 设置与第三方阅读器端到端朗读。
