package com.tuxundl.windows

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** One hour of traffic: the hour it starts (epoch seconds) and its totals. */
@Serializable
data class HourTraffic(
    val hour: Long,
    val down: Long = 0,
    val up: Long = 0,
)

/**
 * Rolling traffic history, persisted as JSON in the app data dir so
 * it survives restarts. The home page's poll loop adds each
 * interval's byte deltas here; hour buckets are kept for the last
 * 7 days so the home page can draw both a 24-hour view (hourly) and
 * a 7-day view (the same buckets summed per day). Hours when the app
 * was not running simply have no data.
 */
object TrafficHistory {

    private val json = Json { ignoreUnknownKeys = true }

    private val file: File
        get() = File(CoreBridge.workDir, "traffic-history.json")

    private var buckets: MutableMap<Long, HourTraffic>? = null

    private fun currentHour(): Long =
        System.currentTimeMillis() / 1000 / 3600 * 3600

    @Synchronized
    private fun load(): MutableMap<Long, HourTraffic> {
        buckets?.let { return it }
        val loaded = if (file.exists()) {
            runCatching {
                json.decodeFromString<List<HourTraffic>>(file.readText())
                    .associateBy { it.hour }
                    .toMutableMap()
            }.getOrDefault(mutableMapOf())
        } else {
            mutableMapOf()
        }
        buckets = loaded
        return loaded
    }

    /** Add one interval's bytes to the current hour and persist. */
    @Synchronized
    fun add(downDelta: Long, upDelta: Long) {
        if (downDelta <= 0 && upDelta <= 0) return
        val map = load()
        val hour = currentHour()
        val existing = map[hour] ?: HourTraffic(hour)
        map[hour] = existing.copy(
            down = existing.down + downDelta.coerceAtLeast(0),
            up = existing.up + upDelta.coerceAtLeast(0),
        )
        // Keep the last 7 days of hour buckets, the current hour
        // included — the 7-day chart sums these per day, so dropping
        // everything past 24 hours (the old behaviour) would leave
        // it nothing to draw.
        val cutoff = hour - (7 * 24 - 1) * 3600
        map.keys.removeIf { it < cutoff }
        runCatching {
            CoreBridge.workDir.mkdirs()
            file.writeText(json.encodeToString(map.values.sortedBy { it.hour }))
        }
    }

    /** Today's (download, upload) totals, summed from the buckets. */
    @Synchronized
    fun todayTotals(): Pair<Long, Long> {
        val today = java.time.LocalDate.now()
        var down = 0L
        var up = 0L
        for (bucket in load().values) {
            val day = java.time.Instant.ofEpochSecond(bucket.hour)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
            if (day == today) {
                down += bucket.down
                up += bucket.up
            }
        }
        return down to up
    }

    /** The last 24 hour buckets, oldest first, gaps zero-filled. */
    @Synchronized
    fun last24Hours(): List<HourTraffic> {
        val map = load()
        val now = currentHour()
        return (0 until 24).map { index ->
            val hour = now - (23 - index) * 3600L
            map[hour] ?: HourTraffic(hour)
        }
    }

    /**
     * The last 7 days (today included), oldest first, as one bucket
     * per day: the day's hour buckets summed. Each entry's `hour`
     * holds the epoch second of that day's local midnight, which is
     * all the chart needs for its date labels.
     */
    @Synchronized
    fun last7Days(): List<HourTraffic> {
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now()
        val byDay = load().values.groupBy { bucket ->
            java.time.Instant.ofEpochSecond(bucket.hour)
                .atZone(zone)
                .toLocalDate()
        }
        // 6 days ago first, today last: oldest first, like the
        // hourly view.
        return (6 downTo 0).map { daysAgo ->
            val day = today.minusDays(daysAgo.toLong())
            val buckets = byDay[day].orEmpty()
            HourTraffic(
                hour = day.atStartOfDay(zone).toEpochSecond(),
                down = buckets.sumOf { it.down },
                up = buckets.sumOf { it.up },
            )
        }
    }
}
