package io.github.deepseektts.update

import android.content.Context

interface UpdateStateStore {
    var lastCheckedMs: Long
    var lastKnownTag: String?
    var blockedUntilMs: Long
}

class SharedPreferencesUpdateState(context: Context) : UpdateStateStore {
    private val prefs = context.applicationContext.getSharedPreferences("update_state", Context.MODE_PRIVATE)
    override var lastCheckedMs: Long
        get() = prefs.getLong("last_checked_ms", 0L)
        set(value) { prefs.edit().putLong("last_checked_ms", value).apply() }
    override var lastKnownTag: String?
        get() = prefs.getString("last_known_tag", null)
        set(value) { prefs.edit().putString("last_known_tag", value).apply() }
    override var blockedUntilMs: Long
        get() = prefs.getLong("blocked_until_ms", 0L)
        set(value) { prefs.edit().putLong("blocked_until_ms", value).apply() }
}
