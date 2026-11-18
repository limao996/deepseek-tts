# 隐私说明

鲸语 TTS 是本地安装的 Android 系统文字转语音引擎，不运行自建服务器，也不收集遥测数据。

## 会发送什么

- 用户启动试听或系统 TTS 合成时，朗读文本会发送至 DeepSeek 官方接口：wss://api.deepseek.com/v1/tools/tts。
- 请求包含设备上保存的 DeepSeek API Key、所选音色（stella 或 tide）、PCM 格式及 Markdown 过滤设置。
- 应用只连接 DeepSeek 官方 API，不支持中转、镜像或第三方兼容端点。
- 点击赞助链接会在浏览器打开爱发电；应用不会向爱发电发送任何数据。

## 不会发送或保存什么

- 不会把朗读文本、合成音频或请求日志写入本地，也不会发送给应用自有服务器；音频流直接交给 Android 系统播放。
- 用量统计仅在合成成功后记录日期、计费字符数和请求数，不含文本或音频。
- API Key 使用 Android Keystore AES-GCM 加密后保存在应用私有 SharedPreferences 中，界面不回显已保存的密钥。
- 应用不包含遥测或广告。

## 第三方调用

- 作为系统 TTS 引擎，其他应用默认可以请求朗读；可在设置中关闭第三方调用。
- 第三方应用提交的文本会发送到 DeepSeek，并使用本机保存的 API Key 计费。应用不限制朗读次数或每日用量；费用由 DeepSeek 账户结算。

## 备份与迁移

- Manifest 声明禁用备份；Android 12+ 云备份与设备迁移规则也显式排除包含密钥的 SharedPreferences。
- 部分厂商迁移流程可能不遵循这些规则。更换设备前可清除密钥，并在新设备重新配置。

## 你的控制权

- 可在设置页随时清除 API Key、关闭第三方系统调用或清除本地用量统计。
- 卸载应用会移除应用私有数据；卸载后，Android Keystore 中对应密钥不可用。
- 点击爱发电赞助链接由浏览器处理；是否访问由你决定。

## 联系

请在项目仓库提交 Issue：https://github.com/limao996/deepseek-tts/issues
