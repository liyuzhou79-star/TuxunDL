package com.tuxundl.android.util

import kotlin.math.abs

/**
 * One periodic delay probe of the current node, taken by MainActivity
 * while the tunnel runs. [delayMs] < 0 means the probe failed (timeout
 * or error). Kept in memory only — the advanced traffic panel reads the
 * recent history, nothing is persisted.
 */
data class DelaySample(
    val atMillis: Long,
    val delayMs: Int,
)

/** Coarse connection-quality verdict derived from the probe history. */
enum class QualityRating {
    Excellent,
    Stable,
    Unstable,
    Poor,

    /** No successful probe yet (or no probes at all). */
    NoData,
}

/** Aggregated view over a list of [DelaySample]s. */
data class QualitySummary(
    val sampleCount: Int,
    val successCount: Int,
    /** Mean delay of successful probes, ms; 0 when none succeeded. */
    val avgDelayMs: Int,
    /** Mean absolute difference between consecutive successful delays, ms. */
    val jitterMs: Int,
    /** Failed probes / all probes, 0..1. */
    val failRate: Float,
    val rating: QualityRating,
)

/**
 * Summarise probe history into the numbers the advanced traffic panel
 * shows. Pure function — also used when snapshotting a saved connection,
 * so the panel and the snapshot always agree on the verdict.
 */
fun summarizeQuality(samples: List<DelaySample>): QualitySummary {
    if (samples.isEmpty()) {
        return QualitySummary(0, 0, 0, 0, 0f, QualityRating.NoData)
    }

    val successes = samples.filter { it.delayMs >= 0 }
    val failRate = (samples.size - successes.size).toFloat() / samples.size

    if (successes.isEmpty()) {
        return QualitySummary(samples.size, 0, 0, 0, failRate, QualityRating.Poor)
    }

    val avg = successes.sumOf { it.delayMs } / successes.size
    val jitter = if (successes.size >= 2) {
        var total = 0
        for (i in 1 until successes.size) {
            total += abs(successes[i].delayMs - successes[i - 1].delayMs)
        }
        total / (successes.size - 1)
    } else {
        0
    }

    val rating = when {
        failRate <= 0f && jitter < 30 && avg < 200 -> QualityRating.Excellent
        failRate <= 0.1f && jitter < 80 -> QualityRating.Stable
        failRate <= 0.3f && jitter < 150 -> QualityRating.Unstable
        else -> QualityRating.Poor
    }

    return QualitySummary(samples.size, successes.size, avg, jitter, failRate, rating)
}
