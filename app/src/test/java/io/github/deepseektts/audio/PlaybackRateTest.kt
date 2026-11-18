package io.github.deepseektts.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackRateTest {

    @Test
    fun callerDefaultUsesTheConfiguredBaseline() {
        // A third-party app that never calls setSpeechRate() sends 100.
        assertEquals(1.5f, PlaybackRate.effective(100, 1.5f, applyBaseline = true), 1e-6f)
        assertEquals(0.8f, PlaybackRate.effective(100, 0.8f, applyBaseline = true), 1e-6f)
    }

    @Test
    fun callerMultiplierStacksOnTheBaseline() {
        assertEquals(1.8f, PlaybackRate.effective(120, 1.5f, applyBaseline = true), 1e-6f)
    }

    @Test
    fun ownPreviewIsNotScaledTwice() {
        assertEquals(1.5f, PlaybackRate.effective(150, 1.5f, applyBaseline = false), 1e-6f)
    }

    @Test
    fun resultStaysInsideSonicRange() {
        assertEquals(2f, PlaybackRate.effective(200, 2f, applyBaseline = true), 1e-6f)
        assertEquals(0.5f, PlaybackRate.effective(10, 0.5f, applyBaseline = true), 1e-6f)
        assertEquals(2f, PlaybackRate.effective(400, 1f, applyBaseline = false), 1e-6f)
    }

    @Test
    fun invalidPercentFallsBackToBaseline() {
        assertEquals(1.2f, PlaybackRate.effective(0, 1.2f, applyBaseline = true), 1e-6f)
    }
}
