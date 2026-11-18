package io.github.deepseektts.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Keystore key never leaves the device; ciphertext is excluded from backup. */
class EngineSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("engine_settings", Context.MODE_PRIVATE)

    fun load(): EngineSettings = synchronized(LOCK) {
        val encrypted = preferences.getString("credential", null)
        val apiKey = if (encrypted.isNullOrBlank()) "" else try { decrypt(encrypted) }
        catch (_: Exception) { throw SettingsStorageException("无法解密 API Key，请在设置中清除后重新保存。") }
        EngineSettings(
            apiKey = apiKey,
            voiceId = preferences.getString("voice", "stella").takeIf { it == "stella" || it == "tide" } ?: "stella",
            filterMarkdown = preferences.getBoolean("markdown", true),
            speechRate = safeFactor(preferences.getFloat("rate", 1f)),
            pitch = safeFactor(preferences.getFloat("pitch", 1f)),
            dynamicColor = preferences.getBoolean("dynamic_color", false),
            allowThirdPartyRequests = preferences.getBoolean("allow_third_party_requests", false),
        )
    }

    fun save(settings: EngineSettings) = synchronized(LOCK) {
        val token = settings.apiKey.trim()
        require(token.all { it.code in 33..126 }) { "API Key 只能包含不含空格的可打印英文字符" }
        require(token.length <= 4096) { "API Key 过长" }
        val editor = preferences.edit()
        if (token.isEmpty()) editor.remove("credential") else editor.putString("credential", encrypt(token))
        check(editor.putString("voice", if (settings.voiceId == "tide") "tide" else "stella")
            .putBoolean("markdown", settings.filterMarkdown)
            .putFloat("rate", safeFactor(settings.speechRate))
            .putFloat("pitch", safeFactor(settings.pitch))
            .putBoolean("dynamic_color", settings.dynamicColor)
            .putBoolean("allow_third_party_requests", settings.allowThirdPartyRequests).commit()) { "保存失败，请检查设备存储空间" }
    }

    /** Reserve worst-case billed characters for every attempted cloud request. */
    fun reserveDailyCharacters(requested: Int, nowMillis: Long = System.currentTimeMillis()): DailyQuota {
        require(requested > 0) { "字符预留数必须大于零" }
        return synchronized(LOCK) {
            val nowDay = Math.floorDiv(nowMillis, MILLIS_PER_DAY)
            val savedDay = preferences.getLong("usage_day_utc", -1L)
            // Do not reset usage if the wall clock moves backwards.
            val usageDay = maxOf(savedDay, nowDay)
            val savedUsed = if (savedDay == usageDay) preferences.getInt("usage_characters", 0) else 0
            if (requested > DAILY_CHARACTER_LIMIT - savedUsed) {
                return@synchronized DailyQuota(false, savedUsed, DAILY_CHARACTER_LIMIT - savedUsed)
            }
            val nextUsed = savedUsed + requested
            check(preferences.edit().putLong("usage_day_utc", usageDay).putInt("usage_characters", nextUsed).commit()) {
                "无法更新本机用量上限，请稍后重试"
            }
            DailyQuota(true, nextUsed, DAILY_CHARACTER_LIMIT - nextUsed)
        }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + bytes, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        require(bytes.size >= 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return String(cipher.doFinal(bytes, 12, bytes.size - 12), Charsets.UTF_8)
    }

    private fun safeFactor(value: Float) = if (value.isFinite()) value.coerceIn(0.5f, 2f) else 1f
    companion object {
        const val DAILY_CHARACTER_LIMIT = 10_000
        private const val MILLIS_PER_DAY = 86_400_000L
        private const val ALIAS = "deepseek_tts_api_key_v1"
        private val LOCK = Any()
    }
}

data class DailyQuota(val allowed: Boolean, val used: Int, val remaining: Int)
class SettingsStorageException(message: String) : IllegalStateException(message)
