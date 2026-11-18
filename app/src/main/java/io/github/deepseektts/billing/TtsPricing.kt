package io.github.deepseektts.billing

/**
 * DeepSeek TTS list prices, per 10,000 billed characters, in CNY.
 *
 * Official Chinese pricing page: https://api-docs.deepseek.com/zh-cn/quick_start/pricing
 * TTS list price is 3 元 / 10k characters; a limited-time 50% discount makes it 1.5 元.
 * DeepSeek publishes prices in CNY for domestic accounts and may change them, so the UI
 * labels every figure as an estimate.
 */
object TtsPricing {
    const val LIST_PRICE_CNY_PER_10K = 3.0
    const val PROMO_PRICE_CNY_PER_10K = 1.5
    const val CHARACTERS_PER_UNIT = 10_000

    /** Cost in CNY for a character count at the given per-10k price. */
    fun costCny(characters: Int, pricePer10k: Double = PROMO_PRICE_CNY_PER_10K): Double {
        if (characters <= 0) return 0.0
        return characters.toDouble() / CHARACTERS_PER_UNIT * pricePer10k
    }

    /** Compact CNY label that never hides small amounts behind "0.00". */
    fun formatCny(value: Double): String {
        val v = if (value.isFinite() && value > 0) value else 0.0
        return when {
            v == 0.0 -> "¥0"
            v < 0.001 -> "<¥0.001"
            v < 0.01 -> "¥" + String.format(java.util.Locale.US, "%.4f", v)
            v < 1.0 -> "¥" + String.format(java.util.Locale.US, "%.3f", v)
            else -> "¥" + String.format(java.util.Locale.US, "%.2f", v)
        }
    }
}
