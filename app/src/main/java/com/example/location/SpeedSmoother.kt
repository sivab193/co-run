package com.example.location

import kotlin.math.abs

/**
 * Rolling time-window speed smoother that filters raw GPS jitter and spikes
 * while staying responsive to real pace changes during a run.
 */
class SpeedSmoother(
    private val windowMs: Long = 5_000L,
    private val stationaryThresholdMps: Double = 0.35,
    private val maxRealisticSpeedMps: Double = 14.0
) {
    private data class Sample(val timestampMs: Long, val speedMps: Double)

    private val samples = ArrayDeque<Sample>()

    fun addSample(timestampMs: Long, rawSpeedMps: Double): Double {
        if (!rawSpeedMps.isFinite() || rawSpeedMps < 0.0 || rawSpeedMps > maxRealisticSpeedMps) {
            return currentSpeedMps(timestampMs)
        }
        val clamped = if (rawSpeedMps < stationaryThresholdMps) 0.0 else rawSpeedMps
        samples.addLast(Sample(timestampMs, clamped))
        pruneOld(timestampMs)
        return computeWeightedAverage(timestampMs)
    }

    fun currentSpeedMps(nowMs: Long): Double {
        pruneOld(nowMs)
        return computeWeightedAverage(nowMs)
    }

    fun reset() {
        samples.clear()
    }

    private fun pruneOld(nowMs: Long) {
        val cutoff = nowMs - windowMs
        while (samples.isNotEmpty() && samples.first().timestampMs < cutoff) {
            samples.removeFirst()
        }
    }

    private fun computeWeightedAverage(nowMs: Long): Double {
        if (samples.isEmpty()) return 0.0
        var weightedSum = 0.0
        var totalWeight = 0.0
        for (sample in samples) {
            val ageMs = abs(nowMs - sample.timestampMs).coerceAtMost(windowMs)
            // Linear recency weight from 0.4 (oldest in window) to 1.0 (newest)
            val weight = 1.0 - 0.6 * (ageMs.toDouble() / windowMs.toDouble())
            weightedSum += sample.speedMps * weight
            totalWeight += weight
        }
        val avg = if (totalWeight > 0.0) weightedSum / totalWeight else 0.0
        return if (avg < stationaryThresholdMps) 0.0 else avg
    }
}
