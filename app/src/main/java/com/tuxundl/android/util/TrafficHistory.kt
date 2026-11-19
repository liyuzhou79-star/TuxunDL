package com.tuxundl.android.util

/**
 * Long-window traffic history (1.4.0): the home chart used to keep
 * only the last 120 one-second samples. The fullscreen chart needs
 * up to 2 hours, which at 1-second resolution would be 7,200 points;
 * instead the recorder tiers the data:
 *
 * - the most recent [FINE_SPAN_MS] (10 minutes) stays at the native
 *   per-tick resolution (~1 s per point);
 * - older data is folded into wall-clock [BUCKET_MS] (10 s) buckets
 *   whose byte counts are exact sums of the points they absorbed —
 *   only the time resolution drops, never the totals;
 * - everything older than [TOTAL_SPAN_MS] (2 hours) is dropped.
 *
 * Worst case that is ~600 fine points + ~660 buckets ≈ 1,300 small
 * objects (a few tens of KB of heap), bounded no matter how long the
 * session runs. Points are recorded by TrafficSampler while the
 * tunnel runs. When the sampler's process is frozen for a while
 * (app backgrounded, doze) the tunnel keeps forwarding and the
 * counters keep accumulating; on the next tick the sampler spreads
 * the accumulated bytes evenly over the missed seconds as ordinary
 * 1-second points, so a frozen span reads as a flat plateau at the
 * true average rate — not a hole, and not one fabricated spike.
 */
data class TrafficPoint(
    /** Wall-clock start of this point's window. */
    val startMillis: Long,
    /** Nominal window length: ~1 s for fine points, 10 s for buckets. */
    val windowMillis: Long,
    /** Time actually sampled inside the window (<= [windowMillis]). */
    val coveredMillis: Long,
    val upBytes: Long,
    val downBytes: Long,
) {
    val endMillis: Long get() = startMillis + windowMillis

    /** Mean upload rate over the sampled time, bytes/s. */
    val upRate: Long
        get() = if (coveredMillis > 0L) upBytes * 1000L / coveredMillis else 0L

    /** Mean download rate over the sampled time, bytes/s. */
    val downRate: Long
        get() = if (coveredMillis > 0L) downBytes * 1000L / coveredMillis else 0L
}

class TrafficHistoryRecorder {
    private val fine = ArrayDeque<TrafficPoint>()
    private val coarse = ArrayDeque<TrafficPoint>()
    private val disconnects = mutableListOf<Long>()

    /** Append one tick of traffic; [startMillis] is the tick's wall-clock start. */
    fun record(startMillis: Long, durationMillis: Long, upBytes: Long, downBytes: Long) {
        if (durationMillis <= 0L) return

        fine.addLast(
            TrafficPoint(
                startMillis = startMillis,
                windowMillis = durationMillis,
                coveredMillis = durationMillis,
                upBytes = upBytes,
                downBytes = downBytes,
            ),
        )

        // Age points out of the fine tier into 10 s buckets.
        val fineCutoff = startMillis - FINE_SPAN_MS
        while (true) {
            val first = fine.firstOrNull() ?: break
            if (first.startMillis >= fineCutoff) break
            fine.removeFirst()
            foldIntoCoarse(first)
        }

        // Drop buckets that fell out of the 2-hour window.
        val totalCutoff = startMillis - TOTAL_SPAN_MS
        while (coarse.firstOrNull()?.let { it.startMillis < totalCutoff } == true) {
            coarse.removeFirst()
        }
    }

    private fun foldIntoCoarse(point: TrafficPoint) {
        val bucketStart = point.startMillis / BUCKET_MS * BUCKET_MS
        val last = coarse.lastOrNull()

        if (last != null && last.startMillis == bucketStart) {
            coarse.removeLast()
            coarse.addLast(
                last.copy(
                    coveredMillis = last.coveredMillis + point.coveredMillis,
                    upBytes = last.upBytes + point.upBytes,
                    downBytes = last.downBytes + point.downBytes,
                ),
            )
        } else {
            coarse.addLast(
                TrafficPoint(
                    startMillis = bucketStart,
                    windowMillis = BUCKET_MS,
                    coveredMillis = point.coveredMillis,
                    upBytes = point.upBytes,
                    downBytes = point.downBytes,
                ),
            )
        }
    }

    /**
     * Note a moment where the tunnel effectively dropped (the core's
     * cumulative counters reset — a restart / reconnect). Two marks
     * closer than 2 s collapse into one.
     */
    fun recordDisconnect(atMillis: Long) {
        if (disconnects.lastOrNull()?.let { atMillis - it < 2_000L } == true) return
        disconnects += atMillis
        while (disconnects.size > MAX_DISCONNECTS) disconnects.removeAt(0)
    }

    /**
     * Rebuild the tiers from previously persisted points (1.6.1:
     * the sampler's writer process restores its session after a
     * restart from the shared history file). Points arrive oldest
     * first, already tiered — wide windows go back to the coarse
     * tier, narrow ones to the fine tier; anything that fell out of
     * the total span in the meantime is dropped.
     */
    fun restore(points: List<TrafficPoint>, disconnectTimes: List<Long>) {
        clear()

        val cutoff = (points.lastOrNull()?.startMillis ?: return) - TOTAL_SPAN_MS
        for (point in points) {
            if (point.startMillis < cutoff) continue
            if (point.windowMillis >= BUCKET_MS) {
                coarse.addLast(point)
            } else {
                fine.addLast(point)
            }
        }

        disconnects += disconnectTimes.takeLast(MAX_DISCONNECTS)
    }

    /** All retained points, oldest first (coarse buckets, then fine points). */
    fun points(): List<TrafficPoint> = coarse + fine

    /** Recorded disconnect instants, oldest first. */
    fun disconnects(): List<Long> = disconnects.toList()

    fun clear() {
        fine.clear()
        coarse.clear()
        disconnects.clear()
    }

    companion object {
        const val FINE_SPAN_MS = 600_000L
        const val BUCKET_MS = 10_000L
        const val TOTAL_SPAN_MS = 7_200_000L
        const val MAX_DISCONNECTS = 128
    }
}

/** A stretch of connection trouble derived from the delay probes. */
enum class HealthKind {
    /** Probes failed outright: the node was unreachable. */
    Outage,

    /** Probes answered but slowly (or single scattered failures). */
    Poor,
}

data class HealthSegment(
    val startMillis: Long,
    val endMillis: Long,
    val kind: HealthKind,
)

/**
 * Derive outage / poor segments from the 15-second delay probes.
 *
 * Thresholds (kept in line with the rest of the app):
 * - a probe with delayMs < 0 failed (timeout / error);
 * - a probe slower than [POOR_DELAY_MS] (300 ms — the same boundary
 *   the node list paints red) counts as "bad";
 * - an **outage** is a run of at least [MIN_RUN] consecutive failed
 *   probes (~30 s of unreachability);
 * - a **poor** stretch is a run of at least [MIN_RUN] consecutive bad
 *   probes that is not an outage (slow answers, or an isolated failed
 *   probe among slow ones).
 *
 * Single-probe blips never form a segment — the analysis would
 * rather under-report than cry wolf. Segment ends extend to the
 * first healthy probe after the run (or to [nowMillis] when the run
 * is still going).
 */
fun analyzeHealth(probes: List<DelaySample>, nowMillis: Long): List<HealthSegment> {
    if (probes.size < MIN_RUN) return emptyList()

    val sorted = probes.sortedBy { it.atMillis }
    val segments = mutableListOf<HealthSegment>()
    val inOutage = BooleanArray(sorted.size)

    fun runEnd(runLast: Int): Long =
        if (runLast + 1 < sorted.size) {
            sorted[runLast + 1].atMillis
        } else {
            maxOf(nowMillis, sorted[runLast].atMillis)
        }

    // Pass 1: outage runs (all probes failed).
    var i = 0
    while (i < sorted.size) {
        if (sorted[i].delayMs >= 0) {
            i++
            continue
        }
        var j = i
        while (j + 1 < sorted.size && sorted[j + 1].delayMs < 0) j++
        if (j - i + 1 >= MIN_RUN) {
            segments += HealthSegment(sorted[i].atMillis, runEnd(j), HealthKind.Outage)
            for (k in i..j) inOutage[k] = true
        }
        i = j + 1
    }

    // Pass 2: poor runs (bad probes outside outage runs).
    fun isBad(index: Int): Boolean =
        !inOutage[index] && (sorted[index].delayMs < 0 || sorted[index].delayMs > POOR_DELAY_MS)

    i = 0
    while (i < sorted.size) {
        if (!isBad(i)) {
            i++
            continue
        }
        var j = i
        while (j + 1 < sorted.size && isBad(j + 1)) j++
        if (j - i + 1 >= MIN_RUN) {
            segments += HealthSegment(sorted[i].atMillis, runEnd(j), HealthKind.Poor)
        }
        i = j + 1
    }

    return segments.sortedBy { it.startMillis }
}

/** Connection state at one moment, from the probes and segments. */
enum class ConnectionState {
    Ok,
    Poor,
    Outage,

    /** No probe near the moment (gap in sampling, or too long ago). */
    NoData,
}

/**
 * What the connection looked like at [atMillis]: inside an outage
 * segment it was down; inside a poor segment it was struggling;
 * otherwise the nearest probe within [PROBE_WINDOW_MS] speaks for it
 * (a lone failed probe reads as poor, a successful one as OK with
 * its delay). Returns the state and the probe delay when known.
 */
fun connectionStateAt(
    atMillis: Long,
    probes: List<DelaySample>,
    segments: List<HealthSegment>,
): Pair<ConnectionState, Int?> {
    for (segment in segments) {
        if (atMillis in segment.startMillis..segment.endMillis) {
            return when (segment.kind) {
                HealthKind.Outage -> ConnectionState.Outage to null
                HealthKind.Poor -> ConnectionState.Poor to
                    nearestProbe(atMillis, probes)?.delayMs?.takeIf { it >= 0 }
            }
        }
    }

    val probe = nearestProbe(atMillis, probes) ?: return ConnectionState.NoData to null
    if (kotlin.math.abs(probe.atMillis - atMillis) > PROBE_WINDOW_MS) {
        return ConnectionState.NoData to null
    }
    return if (probe.delayMs >= 0) {
        ConnectionState.Ok to probe.delayMs
    } else {
        ConnectionState.Poor to null
    }
}

private fun nearestProbe(atMillis: Long, probes: List<DelaySample>): DelaySample? =
    probes.minByOrNull { kotlin.math.abs(it.atMillis - atMillis) }

/** Mean delay of successful probes inside [fromMillis, toMillis]; null when none. */
fun averageDelayIn(probes: List<DelaySample>, fromMillis: Long, toMillis: Long): Int? {
    val hits = probes.filter { it.atMillis in fromMillis..toMillis && it.delayMs >= 0 }
    if (hits.isEmpty()) return null
    return hits.sumOf { it.delayMs } / hits.size
}

const val POOR_DELAY_MS = 300
const val MIN_RUN = 2
const val PROBE_WINDOW_MS = 20_000L
