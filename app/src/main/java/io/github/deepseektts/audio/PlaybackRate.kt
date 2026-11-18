package io.github.deepseektts.audio

/**
 * Resolves the speed/pitch actually applied to one utterance.
 *
 * Android passes the caller's rate/pitch as percentages of the platform default (100 = 1.0x).
 * A reader app that never calls setSpeechRate() therefore sends 100, which previously made the
 * engine ignore the rate/pitch the user configured inside this app.
 *
 * For third-party callers the user's setting is used as a baseline and the caller's value is a
 * multiplier on top of it. The app's own preview already passes the configured value directly,
 * so it is not multiplied again.
 */
object PlaybackRate {
    const val MIN = 0.5f
    const val MAX = 2f

    fun effective(requestedPercent: Int, baseline: Float, applyBaseline: Boolean): Float {
        // 0 or a negative value means the caller did not set a rate: use the platform default.
        val caller = if (requestedPercent > 0) requestedPercent / 100f else 1f
        val base = if (applyBaseline && baseline.isFinite()) baseline.coerceIn(MIN, MAX) else 1f
        return (base * caller).coerceIn(MIN, MAX)
    }
}
