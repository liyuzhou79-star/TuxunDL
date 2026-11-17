package com.tuxundl.android.util

import com.tuxundl.android.common.log.Log
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** One day of forwarded traffic, in raw bytes. */
data class DailyTrafficDay(val date: String, val up: Long, val down: Long)

/**
 * Per-day forwarded traffic, kept in one small JSON file
 * (`filesDir/traffic-daily.json`):
 * `{"days": {"2026-11-17": {"up": 123, "down": 456}}}`.
 *
 * Only traffic seen while the app process is alive is counted — the
 * ticker in MainActivity feeds the deltas, so traffic forwarded while
 * the app is closed never reaches this file. The UI says so.
 * Writes are throttled by the caller (every 60 seconds plus once when
 * the activity stops); only the last 14 days are kept.
 */
class DailyTrafficStore(private val file: File) {
    private val days = LinkedHashMap<String, LongArray>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /** Key for "today" in the device's local timezone. */
    fun todayKey(): String = dateFormat.format(Calendar.getInstance().time)

    fun load() {
        days.clear()
        if (!file.exists()) return

        try {
            val root = JSONObject(file.readText())
            val stored = root.optJSONObject("days") ?: return

            for (key in stored.keys()) {
                val day = stored.optJSONObject(key) ?: continue
                days[key] = longArrayOf(
                    day.optLong("up", 0L),
                    day.optLong("down", 0L),
                )
            }
        } catch (e: Exception) {
            Log.w("Read daily traffic failed: $e", e)
            days.clear()
        }
    }

    fun add(date: String, up: Long, down: Long) {
        val current = days.getOrPut(date) { longArrayOf(0L, 0L) }
        current[0] += up
        current[1] += down
    }

    fun today(date: String): DailyTrafficDay {
        val current = days[date]
        return DailyTrafficDay(date, current?.get(0) ?: 0L, current?.get(1) ?: 0L)
    }

    /** Most recent [count] days before [today], newest first. */
    fun previousDays(count: Int, today: String): List<DailyTrafficDay> {
        return days.keys
            .filter { it < today }
            .sortedDescending()
            .take(count)
            .map { DailyTrafficDay(it, days[it]?.get(0) ?: 0L, days[it]?.get(1) ?: 0L) }
    }

    fun save() {
        try {
            val stored = JSONObject()
            // Keep the file small: last 14 days only.
            for ((date, values) in days.entries.sortedBy { it.key }.takeLast(14)) {
                stored.put(
                    date,
                    JSONObject().put("up", values[0]).put("down", values[1]),
                )
            }

            file.parentFile?.mkdirs()
            file.writeText(JSONObject().put("days", stored).toString())
        } catch (e: Exception) {
            Log.w("Write daily traffic failed: $e", e)
        }
    }
}
