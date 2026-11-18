package io.github.deepseektts.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class EnginePhase { IDLE, CONNECTING, STREAMING, COMPLETED, CANCELLED, ERROR }
data class EngineStatus(
    val phase: EnginePhase = EnginePhase.IDLE,
    val message: String = "等待朗读",
    val firstAudioMs: Long? = null,
    val audioBytes: Long = 0,
    val updatedAtMs: Long = 0L,
)
object EngineStatusBus {
    private val mutable = MutableStateFlow(EngineStatus())
    val state: StateFlow<EngineStatus> = mutable.asStateFlow()
    internal fun publish(status: EngineStatus) { mutable.value = status }
}
