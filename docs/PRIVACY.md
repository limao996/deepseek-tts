# 隐私说明

鲸语 TTS 是本地安装的 Android 系统文字转语音引擎。应用没有自建服务器，也不收集遥测数据。

## 会发送什么
- 当应用内试听或系统 TTS 调用触发合成时，待朗读的文本会通过 wss://api.deepseek.com/v1/tools/tts 发送给 DeepSeek。
- 请求携带用户在该设备上保存的 DeepSeek API Key，以及所选音色（stella/tide）、PCM 格式与 Markdown 过滤开关。

## 不保存什么
- 不保存朗读文本、合成音频或请求日志；音频流直接交给 Android 系统播放。
- API Key 使用 Android Keystore AES-GCM 加密后存放在应用私有 SharedPreferences，界面不回显已保存的密钥。

## 第三方应用边界
- 作为系统 TTS 引擎，其他应用可以请求本引擎朗读。默认关闭该能力；只有在设置页明确确认费用与隐私提示并保存后才允许。
- 开启后，其他应用提供的文本会发送至 DeepSeek，并使用本机保存的 API Key 计费。应用提供本机每日 10,000 计费字符的上限保护，但该上限不是金额上限，也不防御 root、重装等绕过方式。

## 备份与迁移
- Manifest 声明禁用备份，并为 Android 12+ 云备份与设备迁移显式排除保存密钥的 SharedPreferences。
- 厂商迁移实现可能不遵循这些设置；如需更换设备，建议先清除密钥，在新设备重新配置。

## 用户控制
- 可在设置页随时清除 API Key，或关闭第三方系统调用。
- 卸载应用会移除应用私有数据；Android Keystore 中对应密钥在卸载后不可用。

## 联系
请在项目仓库提交 Issue。
