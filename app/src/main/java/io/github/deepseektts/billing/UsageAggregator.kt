package io.github.deepseektts.billing

import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class DayUsage(val date: LocalDate, val characters: Int, val requests: Int)

data class UsageSnapshot(
    val days: List<DayUsage>,
    val todayCharacters: Int = 0,
    val weekCharacters: Int = 0,
    val monthCharacters: Int = 0,
    val totalCharacters: Int = 0,
    val totalRequests: Int = 0,
    val maxDayCharacters: Int = 0,
) {
    val isEmpty: Boolean get() = totalCharacters == 0 && totalRequests == 0
}

/** Pure aggregation so the heatmap and totals can be unit tested without Android. */
object UsageAggregator {
    const val DEFAULT_WINDOW_DAYS = 182

    fun snapshot(
        recorded: Map<LocalDate, DayUsage>,
        today: LocalDate,
        windowDays: Int = DEFAULT_WINDOW_DAYS,
    ): UsageSnapshot {
        val span = windowDays.coerceIn(7, 400)
        val start = today.minusDays((span - 1).toLong())
        val days = (0 until span).map { offset ->
            val date = start.plusDays(offset.toLong())
            recorded[date] ?: DayUsage(date, 0, 0)
        }
        val weekStart = today.minusDays(6)
        val monthStart = today.minusDays(29)
        var week = 0
        var month = 0
        var total = 0
        var requests = 0
        for ((date, day) in recorded) {
            total += day.characters
            requests += day.requests
            if (!date.isBefore(weekStart)) week += day.characters
            if (!date.isBefore(monthStart)) month += day.characters
        }
        return UsageSnapshot(
            days = days,
            todayCharacters = recorded[today]?.characters ?: 0,
            weekCharacters = week,
            monthCharacters = month,
            totalCharacters = total,
            totalRequests = requests,
            maxDayCharacters = days.maxOfOrNull { it.characters } ?: 0,
        )
    }
}

object UsageDates {
    private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    fun key(date: LocalDate): String = ISO.format(date)
    fun parse(value: String): LocalDate? = runCatching { LocalDate.parse(value, ISO) }.getOrNull()
}
