package io.github.deepseektts.data

data class EngineSettings(
    val apiKey: String = "",
    val voiceId: String = "stella",
    val filterMarkdown: Boolean = true,
    val speechRate: Float = 1f,
    val pitch: Float = 1f,
    val dynamicColor: Boolean = false,
    val allowThirdPartyRequests: Boolean = false,
)
