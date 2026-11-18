package io.github.deepseektts.update

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class UpdateCheckerTest {
    private lateinit var server: MockWebServer
    private lateinit var state: FakeState
    private val client = OkHttpClient.Builder().build()

    private class FakeState : UpdateStateStore {
        override var lastCheckedMs = 0L
        override var lastKnownTag: String? = null
        override var blockedUntilMs = 0L
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        state = FakeState()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun checker(version: String = "0.1.1") = UpdateChecker(
        client = client,
        state = state,
        currentVersion = version,
        redirectUrl = server.url("/releases/latest").toString(),
        apiUrl = server.url("/api/latest").toString(),
    )

    @Test
    fun comparesSemanticVersionsWithoutDowngrading() {
        assertTrue(ReleaseVersion.isNewer("v0.2.0", "0.1.1"))
        assertTrue(ReleaseVersion.isNewer("1.0", "0.9.9"))
        assertTrue(ReleaseVersion.isNewer("0.1.10", "0.1.9"))
        assertFalse(ReleaseVersion.isNewer("0.1.1", "0.1.1"))
        assertFalse(ReleaseVersion.isNewer("v0.1.0", "0.1.1"))
        assertFalse(ReleaseVersion.isNewer("not-a-version", "0.1.1"))
    }

    @Test
    fun readsTagFromWebRedirect() {
        assertEquals("v1.4.2", ReleaseVersion.tagFromRedirect("https://github.com/o/r/releases/tag/v1.4.2"))
        assertEquals("v2.0.0", ReleaseVersion.tagFromRedirect("https://github.com/o/r/releases/tag/v2.0.0?x=1"))
        assertNull(ReleaseVersion.tagFromRedirect("https://github.com/o/r/releases"))
        assertNull(ReleaseVersion.tagFromRedirect(null))
    }

    @Test
    fun redirectReportsNewerReleaseWithoutTouchingApi() {
        server.enqueue(
            MockResponse().setResponseCode(302)
                .addHeader("Location", "https://github.com/o/r/releases/tag/v9.9.9")
        )
        val result = checker().check(manual = true, now = 1_000_000L)
        assertTrue(result is UpdateResult.Available)
        result as UpdateResult.Available
        assertEquals("v9.9.9", result.latest)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun currentReleaseIsReportedAsUpToDate() {
        server.enqueue(
            MockResponse().setResponseCode(302)
                .addHeader("Location", "https://github.com/o/r/releases/tag/v0.1.1")
        )
        assertTrue(checker("0.1.1").check(manual = true, now = 1_000_000L) is UpdateResult.UpToDate)
    }

    @Test
    fun apiFallbackIsUsedOnlyWhenRedirectIsUnavailable() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("no redirect"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"tag_name\":\"v0.3.0\"}"))
        val result = checker().check(manual = true, now = 1_000_000L)
        assertTrue(result is UpdateResult.Available)
        assertEquals("v0.3.0", (result as UpdateResult.Available).latest)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun rateLimitedApiStopsFurtherRequests() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("no redirect"))
        server.enqueue(MockResponse().setResponseCode(403))
        val subject = checker()
        assertTrue(subject.check(manual = true, now = 1_000_000L) is UpdateResult.RateLimited)
        val requests = server.requestCount
        assertTrue(subject.check(manual = true, now = 1_000_001L) is UpdateResult.RateLimited)
        assertEquals("Rate limit must not hammer GitHub", requests, server.requestCount)
    }

    @Test
    fun cachedResultAvoidsRepeatedNetworkCalls() {
        server.enqueue(
            MockResponse().setResponseCode(302)
                .addHeader("Location", "https://github.com/o/r/releases/tag/v9.9.9")
        )
        val subject = checker()
        assertTrue(subject.check(manual = true, now = 1_000_000L) is UpdateResult.Available)
        val requests = server.requestCount
        assertTrue(subject.check(manual = false, now = 1_000_000L + 60_000L) is UpdateResult.Available)
        assertEquals(requests, server.requestCount)
    }

    @Test
    fun automaticCheckRefreshesAfterCacheWindow() {
        repeat(2) {
            server.enqueue(
                MockResponse().setResponseCode(302)
                    .addHeader("Location", "https://github.com/o/r/releases/tag/v9.9.9")
            )
        }
        val subject = checker()
        assertTrue(subject.check(manual = false, now = 1_000_000L) is UpdateResult.Available)
        val requests = server.requestCount
        assertTrue(
            subject.check(manual = false, now = 1_000_000L + UpdateChecker.AUTO_CHECK_INTERVAL_MS + 1)
                is UpdateResult.Available
        )
        assertTrue("Expired cache should refresh", server.requestCount > requests)
    }

    @Test
    fun networkFailureIsReportedWithoutCrashing() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("nothing"))
        server.enqueue(MockResponse().setResponseCode(500))
        assertTrue(checker().check(manual = true, now = 1_000_000L) is UpdateResult.Failed)
    }
}
