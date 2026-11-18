package io.github.deepseektts.billing

import android.content.Context
import java.time.LocalDate

/**
 * Local, privacy-friendly usage history: only day, character count and request count.
 * Text, audio and keys are never stored here.
 */
class UsageRecorder(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("usage_history", Context.MODE_PRIVATE)

    @Synchronized
    fun record(characters: Int, date: LocalDate = LocalDate.now()) {
        if (characters <= 0) return
        val key = UsageDates.key(date)
        val todayChars = prefs.getInt("chars_$key", 0)
        val todayRequests = prefs.getInt("requests_$key", 0)
        prefs.edit()
            .putInt("chars_$key", todayChars + characters)
            .putInt("requests_$key", todayRequests + 1)
            .apply()
    }

    @Synchronized
    fun snapshot(today: LocalDate = LocalDate.now()): UsageSnapshot {
        val recorded = LinkedHashMap<LocalDate, DayUsage>()
        for ((key, value) in prefs.all) {
            if (value !is Int) continue
            if (key.startsWith("chars_")) {
                val date = UsageDates.parse(key.removePrefix("chars_")) ?: continue
                recorded[date] = DayUsage(date, value, prefs.getInt("requests_" + UsageDates.key(date), 0))
            }
        }
        return UsageAggregator.snapshot(recorded, today)
    }

    /** Keeps the preference file small when a user has used the engine for years. */
    @Synchronized
    fun prune(today: LocalDate = LocalDate.now(), keepDays: Int = 400) {
        val cutoff = UsageDates.key(today.minusDays(keepDays.toLong()))
        val editor = prefs.edit()
        for (key in prefs.all.keys) {
            val date = UsageDates.parse(key.substringAfter('_', "")) ?: continue
            if (UsageDates.key(date) < cutoff) editor.remove(key)
        }
        editor.apply()
    }
}
