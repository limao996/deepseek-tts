# DeepSeek TTS 实现记录

## 目标与边界
从空目录创建 Android 系统 TTS 引擎与 Compose M3 Expressive 中文设置应用。官方 WebSocket 输出原始 PCM；系统播放与试听共用系统 TTS 服务，不另造播放器。Android 8.0+；不带自建服务端、不内置 API Key；朗读文本会发送给 DeepSeek，不在应用中记录全文或密钥；不做离线或自动重试。

## 起点
TaskStartSnapshot: 2026-11-18；当前目录文件与目录扫描为空；非 Git 仓库；无现有 README/AGENTS/CONTEXT 或架构约束。Java 17、Gradle 9.6.1、Android SDK 34/36/37 可用；无连接设备。ArchitectureReviewRequired: yes，系统服务边界、网络状态与密钥保存需独立审查。TDD mode off；route skipped；实现后执行自动测试与构建，非 test-first。

## 契约
- 包名 io.github.deepseektts；application label 鲸语 TTS。
- DeepSeek: wss://api.deepseek.com/v1/tools/tts?voice_id=stella|tide&format=pcm&filter_markdown=true|false；Authorization Bearer。发送 text 和 finish_text；二进制为 24kHz/16bit/mono，必须收到 finish/success 才成功。异常关闭不得算成功。
- EngineSettings(apiKey:String, voiceId:String, filterMarkdown:Boolean, speechRate:Float, pitch:Float, dynamicColor:Boolean)；EngineSettingsStore(Context).load()/save(settings)，StateFlow 由 Activity/ViewModel 管理；密钥 Android Keystore AES-GCM 加密存储，禁用备份。
- EngineStatusBus.state: StateFlow<EngineStatus>；EngineStatus(phase:EnginePhase, message:String, firstAudioMs:Long?, audioBytes:Long)；EnginePhase IDLE, CONNECTING, STREAMING, COMPLETED, CANCELLED, ERROR。不得存全文/密钥。
- TextToSpeechService 同步合成线程消费有界 PCM 队列；仅该线程调用 SynthesisCallback；onStop 取消网络与等待。SonicAudioProcessor 本地调速/调高。
- Compose 的试听使用显式指定本包 TextToSpeech 引擎，关闭页面/Activity 销毁释放。设置与服务同进程。

## 计划
1. lead：工程、服务清单、配置安全存储、协议/流队列、PCM处理、系统引擎、单元测试。
2. ui-agent：MainActivity 与 ui/ 下 Compose 界面，蓝灰底、大圆角、蓝色强调、Expressive 主题/动效，首页/设置；API密钥遮挡保存、试听/停止、音色、语速、音调、Markdown、动态配色、系统引擎设置入口；适应窄屏/暗色/大字体/系统栏。
3. lead：整合、依赖/编译/测试/Android lint；独立规格和质量审查；可用时模拟器启动截图；真 API 无密钥不调用、不计费。
4. 交付 README、构建产物位置与真实验证边界。

## 验收
服务可被系统 TTS 查询；语音列表包含10种语言×2音色且标记需联网；收到音频不等待全量，限长与64KiB UTF8检查；音频碎片跨帧16bit对齐；只有服务完成事件才成功；停止/超时/队列溢出/鉴权失败可结束不死等。测试覆盖协议、并发取消、错误、音频切块。构建必须通过；UI/设备/API覆盖按实际执行汇报。

## 官方参考
- https://api-docs.deepseek.com/guides/tts/ (2026-11-18 完整抓取)
- https://developer.android.com/reference/android/speech/tts/SynthesisCallback (线程/终止契约)
- https://developer.android.com/reference/androidx/media3/common/audio/SonicAudioProcessor
- https://developer.android.com/reference/kotlin/androidx/compose/material3/MaterialExpressiveTheme.composable

## 最新检查点
实现与独立审查完成；最新完整构建通过，28项JVM测试和8项Android 14模拟器测试通过，lint 0 error/13 warning。模拟器启动首页；无密钥，真实DeepSeek云端握手/朗读与真机尚未验证。中文Windows路径的JVM测试通过临时盘符映射执行。详细证据见 VERIFICATION.md。默认采用本地变速/变调；用户交互选项超时，未将其记为用户同意。首版范围采用常规可逆默认。
