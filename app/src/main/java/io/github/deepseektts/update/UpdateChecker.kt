package io.github.deepseektts.update

import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class UpdateInfo(val latestTag: String, val releaseUrl: String)

sealed interface UpdateResult {
    data class Available(val current: String, val latest: String, val releaseUrl: String) : UpdateResult
    data class UpToDate(val current: String, val latest: String) : UpdateResult
    data class RateLimited(val retryAfterMs: Long) : UpdateResult
    data class Failed(val message: String) : UpdateResult
}

/**
 * Checks the latest published release without burning the unauthenticated GitHub API budget.
 *
 * Strategy:
 * 1. Read the ordinary web redirect for /releases/latest (no REST API, no 60/hour budget).
 * 2. Fall back to api.github.com only when the redirect yields nothing.
 * 3. Cache results in memory/disk, coalesce concurrent callers, and after a 403/429 stop
 *    asking until the cooldown expires instead of retrying.
 */
class UpdateChecker(
    client: OkHttpClient,
    private val state: UpdateStateStore,
    private val currentVersion: String,
    private val redirectUrl: String = "https://github.com/limao996/deepseek-tts/releases/latest",
    private val apiUrl: String = "https://api.github.com/repos/limao996/deepseek-tts/releases/latest",
) {
    private val client = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private data class Fetched(val tag: String?, val rateLimited: Boolean)

    @Synchronized
    fun check(manual: Boolean, now: Long = System.currentTimeMillis()): UpdateResult {
        val blockedFor = state.blockedUntilMs - now
        if (blockedFor > 0) return UpdateResult.RateLimited(blockedFor)
        val cachedTag = state.lastKnownTag
        val age = now - state.lastCheckedMs
        if (!cachedTag.isNullOrBlank()) {
            if (age in 0 until UpdateChecker.MANUAL_MIN_INTERVAL_MS) return evaluate(cachedTag, now)
            if (!manual && age in 0 until UpdateChecker.AUTO_CHECK_INTERVAL_MS) return evaluate(cachedTag, now)
        }
        val fetched = runCatching { fetchLatestTag() }.getOrElse { Fetched(null, false) }
        if (fetched.rateLimited) {
            state.blockedUntilMs = now + UpdateChecker.RATE_LIMIT_COOLDOWN_MS
            return UpdateResult.RateLimited(UpdateChecker.RATE_LIMIT_COOLDOWN_MS)
        }
        val tag = fetched.tag ?: return UpdateResult.Failed("无法连接 GitHub 检查更新，请检查网络后重试。")
        state.lastCheckedMs = now
        state.lastKnownTag = tag
        return evaluate(tag, now)
    }

    private fun evaluate(tag: String, now: Long): UpdateResult {
        if (state.lastCheckedMs == 0L) state.lastCheckedMs = now
        return if (ReleaseVersion.isNewer(tag, currentVersion)) {
            UpdateResult.Available(currentVersion, tag, AppLinks.releaseTag(tag))
        } else {
            UpdateResult.UpToDate(currentVersion, tag)
        }
    }

    private fun fetchLatestTag(): Fetched {
        val redirectTag = runCatching { fetchRedirectTag() }.getOrNull()
        if (redirectTag != null) return Fetched(redirectTag, false)
        return fetchApiTag()
    }

    private fun fetchRedirectTag(): String? {
        val request = Request.Builder().url(redirectUrl).get().build()
        client.newCall(request).execute().use { response ->
            return ReleaseVersion.tagFromRedirect(response.header("Location"))
                ?: ReleaseVersion.tagFromRedirect(response.request.url.toString())
        }
    }

    private fun fetchApiTag(): Fetched {
        val request = Request.Builder()
            .url(apiUrl)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "jingyu-tts-update-checker")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 403 || response.code == 429) return Fetched(null, true)
            if (!response.isSuccessful) return Fetched(null, false)
            val body = response.body?.string().orEmpty()
            val tag = runCatching {
                JsonParser.parseString(body).asJsonObject.get("tag_name").asString
            }.getOrNull()
            return Fetched(tag?.takeIf { ReleaseVersion.segments(it) != null }, false)
        }
    }

    companion object {
        const val AUTO_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
        const val MANUAL_MIN_INTERVAL_MS = 30_000L
        const val RATE_LIMIT_COOLDOWN_MS = 30 * 60 * 1000L
    }
}
