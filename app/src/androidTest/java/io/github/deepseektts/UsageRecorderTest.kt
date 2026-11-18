package io.github.deepseektts

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.deepseektts.billing.TtsPricing
import io.github.deepseektts.billing.UsageRecorder
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UsageRecorderTest {
    @Test fun successfulSynthesisIsAggregatedForCostAndHeatmap() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val recorder = UsageRecorder(context)
        // Start from a clean slate: the device may hold real or seeded history.
        context.getSharedPreferences("usage_history", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        val today = LocalDate.now()
        recorder.record(1_500, today)
        recorder.record(500, today)
        recorder.record(2_000, today.minusDays(2))

        val snapshot = recorder.snapshot(today)
        assertEquals(2_000, snapshot.todayCharacters)
        // Window counts both today and the day before yesterday.
        assertEquals(4_000, snapshot.dayCharactersInWindow())
        // 2,000 characters at the limited-time price (¥1.5 per 10k) is ¥0.30.
        assertEquals(0.30, TtsPricing.costCny(snapshot.todayCharacters), 1e-9)
        assertEquals(0.60, TtsPricing.costCny(snapshot.todayCharacters, TtsPricing.LIST_PRICE_CNY_PER_10K), 1e-9)
        assertEquals(3, snapshot.totalRequests)
        assertTrue(snapshot.days.size >= 180)
        assertEquals(today, snapshot.days.last().date)
        assertFalse(snapshot.isEmpty)
    }

    @Test fun zeroCharactersIsNotRecorded() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val recorder = UsageRecorder(context)
        context.getSharedPreferences("usage_history", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        val before = recorder.snapshot().totalRequests
        recorder.record(0)
        recorder.record(-10)
        assertEquals(before, recorder.snapshot().totalRequests)
    }

    private fun io.github.deepseektts.billing.UsageSnapshot.dayCharactersInWindow(): Int =
        days.sumOf { it.characters }
}
