package io.github.deepseektts.billing

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageAggregatorTest {
    private val today = LocalDate.of(2026, 11, 18)

    @Test fun buildsDenseWindowEndingToday() {
        val snapshot = UsageAggregator.snapshot(emptyMap(), today, windowDays = 10)
        assertEquals(10, snapshot.days.size)
        assertEquals(today, snapshot.days.last().date)
        assertEquals(today.minusDays(9), snapshot.days.first().date)
        assertTrue(snapshot.isEmpty)
    }

    @Test fun totalsTodayWeekMonthAndAllTime() {
        val recorded = mapOf(
            today to DayUsage(today, 500, 2),
            today.minusDays(3) to DayUsage(today.minusDays(3), 300, 1),
            today.minusDays(20) to DayUsage(today.minusDays(20), 200, 1),
            today.minusDays(200) to DayUsage(today.minusDays(200), 1_000, 5),
        )
        val snapshot = UsageAggregator.snapshot(recorded, today, windowDays = 30)
        assertEquals(500, snapshot.todayCharacters)
        assertEquals(800, snapshot.weekCharacters)
        assertEquals(1_000, snapshot.monthCharacters)
        assertEquals(2_000, snapshot.totalCharacters)
        assertEquals(9, snapshot.totalRequests)
        assertEquals(500, snapshot.maxDayCharacters)
        assertFalse(snapshot.isEmpty)
    }

    @Test fun futureAndOutOfWindowDatesDoNotBreakTheGrid() {
        val future = today.plusDays(5)
        val snapshot = UsageAggregator.snapshot(
            mapOf(future to DayUsage(future, 42, 1)),
            today,
            windowDays = 7,
        )
        assertEquals(7, snapshot.days.size)
        assertEquals(42, snapshot.totalCharacters)
        assertEquals(0, snapshot.maxDayCharacters)
    }

    @Test fun roundsTripDateKeys() {
        val date = LocalDate.of(2026, 1, 2)
        assertEquals(date, UsageDates.parse(UsageDates.key(date)))
        assertEquals(null, UsageDates.parse("not-a-date"))
    }
}
