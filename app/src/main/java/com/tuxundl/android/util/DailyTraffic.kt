package com.tuxundl.android.util

import com.tuxundl.android.common.log.Log
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** One day of forwarded traffic, in raw bytes. */
data class DailyTrafficDay(
    val date: String,
    val up: Long,
    val down: Long,
    /** Seconds spent connected that day (app open and tunnel running). */
    val connSeconds: Long = 0L,
)

/** One hour of forwarded traffic, in raw bytes. */
data class HourTrafficBucket(
    /** Epoch millis at the start of the hour. */
    val hourStartMillis: Long,
    val up: Long,
    val down: Long,
)

/**
 * Per-day forwarded traffic, kept in one small JSON file
 * (`filesDir/traffic-daily.json`):
 * `{"days": {"2026-11-17": {"up": 123, "down": 456, "conn": 60}},
 *   "lifetime": {"up": 1, "down": 2, "conn": 3},
 *   "hourly": {"492345": {"up": 1, "down": 2}}}`.
 *
 * Only traffic seen while the app process is alive is counted — the
 * ticker in MainActivity feeds the deltas, so traffic forwarded while
 * the app is closed never reaches this file. The UI says so.
 * Writes are throttled by the caller (every 60 seconds plus once when
 * the activity stops); only the last 14 days and the last 48 hourly
 * buckets are kept. Lifetime totals never reset. Files written by older
 * versions (days only, no "conn"/"lifetime"/"hourly") load unchanged.
 */
class DailyTrafficStore(private val file: File) {
    /** date -> [up, down, connectedSeconds] */
    private val days = LinkedHashMap<String, LongArray>()

    /** epochHour -> [up, down] */
    private val hourly = LinkedHashMap<Long, LongArray>()

    /** [up, down, connectedSeconds], accumulated forever. */
    private val lifetime = longArrayOf(0L, 0L, 0L)

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /** Key for "today" in the device's local timezone. */
    fun todayKey(): String = dateFormat.format(Calendar.getInstance().time)

    fun load() {
        days.clear()
        hourly.clear()
        lifetime.fill(0L)
        if (!file.exists()) return

        try {
            val root = JSONObject(file.readText())

            val stored = root.optJSONObject("days")
            if (stored != null) {
                for (key in stored.keys()) {
                    val day = stored.optJSONObject(key) ?: continue
                    days[key] = longArrayOf(
                        day.optLong("up", 0L),
                        day.optLong("down", 0L),
                        day.optLong("conn", 0L),
                    )
                }
            }

            val life = root.optJSONObject("lifetime")
            if (life != null) {
                lifetime[0] = life.optLong("up", 0L)
                lifetime[1] = life.optLong("down", 0L)
                lifetime[2] = life.optLong("conn", 0L)
            }

            val hours = root.optJSONObject("hourly")
            if (hours != null) {
                for (key in hours.keys()) {
                    val hour = key.toLongOrNull() ?: continue
                    val bucket = hours.optJSONObject(key) ?: continue
                    hourly[hour] = longArrayOf(
                        bucket.optLong("up", 0L),
                        bucket.optLong("down", 0L),
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("Read daily traffic failed: $e", e)
            days.clear()
            hourly.clear()
            lifetime.fill(0L)
        }
    }

    fun add(date: String, up: Long, down: Long) {
        val current = days.getOrPut(date) { longArrayOf(0L, 0L, 0L) }
        current[0] += up
        current[1] += down
        lifetime[0] += up
        lifetime[1] += down
    }

    /** Add [seconds] of connected time to [date] and to the lifetime total. */
    fun addConnected(date: String, seconds: Long) {
        val current = days.getOrPut(date) { longArrayOf(0L, 0L, 0L) }
        current[2] += seconds
        lifetime[2] += seconds
    }

    /** Add bytes to the bucket of [epochHour] (millis / 3_600_000). */
    fun addHourly(epochHour: Long, up: Long, down: Long) {
        val current = hourly.getOrPut(epochHour) { longArrayOf(0L, 0L) }
        current[0] += up
        current[1] += down
    }

    fun lifetimeUp(): Long = lifetime[0]
    fun lifetimeDown(): Long = lifetime[1]
    fun lifetimeConnectedSeconds(): Long = lifetime[2]

    fun today(date: String): DailyTrafficDay {
        val current = days[date]
        return DailyTrafficDay(
            date,
            current?.get(0) ?: 0L,
            current?.get(1) ?: 0L,
            current?.get(2) ?: 0L,
        )
    }

    /** Connected seconds recorded for [date]; 0 when the day has no data. */
    fun dayConnectedSeconds(date: String): Long = days[date]?.get(2) ?: 0L

    /**
     * Hourly buckets in [fromHour]..[toHour] (epoch hours, inclusive),
     * oldest first, one entry per hour — hours without data come back
     * as zero buckets so charts can render a continuous 24h window.
     */
    fun hourlyBuckets(fromHour: Long, toHour: Long): List<HourTrafficBucket> {
        if (toHour < fromHour) return emptyList()

        return (fromHour..toHour).map { hour ->
            val values = hourly[hour]
            HourTrafficBucket(
                hourStartMillis = hour * 3_600_000L,
                up = values?.get(0) ?: 0L,
                down = values?.get(1) ?: 0L,
            )
        }
    }

    /** Most recent [count] days before [today], newest first. */
    fun previousDays(count: Int, today: String): List<DailyTrafficDay> {
        return days.keys
            .filter { it < today }
            .sortedDescending()
            .take(count)
            .map { key ->
                val values = days[key]
                DailyTrafficDay(
                    key,
                    values?.get(0) ?: 0L,
                    values?.get(1) ?: 0L,
                    values?.get(2) ?: 0L,
                )
            }
    }

    /** Days with stored data in [start]..[end] (inclusive), oldest first. */
    fun daysBetween(start: String, end: String): List<DailyTrafficDay> {
        return days.keys
            .filter { it in start..end }
            .sorted()
            .map { key ->
                val values = days[key]
                DailyTrafficDay(
                    key,
                    values?.get(0) ?: 0L,
                    values?.get(1) ?: 0L,
                    values?.get(2) ?: 0L,
                )
            }
    }

    /**
     * Days of the week [weeksBack] weeks ago that have stored data,
     * oldest first. Weeks start on Monday; the current week ends today,
     * past weeks end on Sunday. Only days present in the file are
     * returned (the file keeps 14 days, so last week may be partial).
     */
    fun weekDays(todayKey: String, weeksBack: Int): List<DailyTrafficDay> {
        val parsed = try {
            dateFormat.parse(todayKey)
        } catch (e: Exception) {
            null
        } ?: return emptyList()

        val cal = Calendar.getInstance()
        cal.time = parsed
        // Calendar.SUNDAY == 1 .. SATURDAY == 7; Monday is offset 0.
        val offsetToMonday = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        cal.add(Calendar.DAY_OF_YEAR, -offsetToMonday - weeksBack * 7)
        val start = dateFormat.format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, 6)
        var end = dateFormat.format(cal.time)
        if (end > todayKey) end = todayKey
        return daysBetween(start, end)
    }

    /**
     * The rolling window of the last [count] days ending today
     * (inclusive), oldest first. Only days present in the file are
     * returned, same as [weekDays] — the file keeps 14 days, so any
     * window up to that fits.
     */
    fun recentDays(todayKey: String, count: Int): List<DailyTrafficDay> {
        val parsed = try {
            dateFormat.parse(todayKey)
        } catch (e: Exception) {
            null
        } ?: return emptyList()

        val cal = Calendar.getInstance()
        cal.time = parsed
        val end = dateFormat.format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, -(count - 1))
        val start = dateFormat.format(cal.time)
        return daysBetween(start, end)
    }

    fun save() {
        try {
            val stored = JSONObject()
            // Keep the file small: last 14 days only.
            for ((date, values) in days.entries.sortedBy { it.key }.takeLast(14)) {
                stored.put(
                    date,
                    JSONObject()
                        .put("up", values[0])
                        .put("down", values[1])
                        .put("conn", values[2]),
                )
            }

            // Hourly buckets: last 48 hours only.
            val currentHour = System.currentTimeMillis() / 3_600_000L
            val storedHours = JSONObject()
            for ((hour, values) in hourly.entries.sortedBy { it.key }) {
                if (hour < currentHour - 48) continue
                storedHours.put(
                    hour.toString(),
                    JSONObject().put("up", values[0]).put("down", values[1]),
                )
            }

            file.parentFile?.mkdirs()
            file.writeText(
                JSONObject()
                    .put("days", stored)
                    .put(
                        "lifetime",
                        JSONObject()
                            .put("up", lifetime[0])
                            .put("down", lifetime[1])
                            .put("conn", lifetime[2]),
                    )
                    .put("hourly", storedHours)
                    .toString(),
            )
        } catch (e: Exception) {
            Log.w("Write daily traffic failed: $e", e)
        }
    }
}
