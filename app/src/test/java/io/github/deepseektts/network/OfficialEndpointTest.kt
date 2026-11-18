package io.github.deepseektts.network

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialEndpointTest {
    private val client = OkHttpClient.Builder().build()

    @Test
    fun defaultsToOfficialDeepSeekEndpoint() {
        val endpoint = Class.forName("io.github.deepseektts.network.DeepSeekStreamSession")
            .getDeclaredField("OFFICIAL_ENDPOINT")
            .also { it.isAccessible = true }
            .get(null) as String
        assertEquals("wss://api.deepseek.com/v1/tools/tts", endpoint)
        DeepSeekStreamSession(client) // construction with defaults must be accepted
    }

    @Test
    fun rejectsRelayOrThirdPartyEndpoint() {
        for (host in listOf("wss://relay.example.com/v1/tools/tts", "https://api.openai.com/v1/audio/speech", "wss://api.deepseek.com.evil.test/tts")) {
            val error = assertThrows(IllegalArgumentException::class.java) {
                DeepSeekStreamSession(client, host)
            }
            assertTrue(error.message!!.contains("official DeepSeek"))
        }
    }

    @Test
    fun allowsLoopbackForAutomatedTestsOnly() {
        DeepSeekStreamSession(client, "http://127.0.0.1:8080/v1/tools/tts")
        DeepSeekStreamSession(client, "http://localhost:8080/v1/tools/tts")
    }
}
