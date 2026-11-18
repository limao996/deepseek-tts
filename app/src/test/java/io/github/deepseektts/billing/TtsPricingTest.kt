package io.github.deepseektts.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsPricingTest {

    @Test
    fun costScalesWithThousandsOfCharacters() {
        assertEquals(0.0, TtsPricing.costCny(0), 1e-9)
        assertEquals(1.5, TtsPricing.costCny(10_000), 1e-9)
        assertEquals(3.0, TtsPricing.costCny(10_000, TtsPricing.LIST_PRICE_CNY_PER_10K), 1e-9)
        assertEquals(0.15, TtsPricing.costCny(1_000), 1e-9)
    }

    @Test
    fun ignoresNegativeInput() {
        assertEquals(0.0, TtsPricing.costCny(-5), 1e-9)
    }

    @Test
    fun formattingUsesYuanAndKeepsSmallAmountsVisible() {
        assertEquals("¥0", TtsPricing.formatCny(0.0))
        assertTrue(TtsPricing.formatCny(0.00001).startsWith("<¥"))
        assertTrue(TtsPricing.formatCny(0.0042).startsWith("¥0.00"))
        assertEquals("¥12.30", TtsPricing.formatCny(12.3))
    }
}
