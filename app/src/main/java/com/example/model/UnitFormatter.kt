package com.example.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object UnitFormatter {
    private const val METERS_PER_KM = 1000.0
    private const val METERS_PER_MILE = 1609.344
    private const val MPS_TO_KMH = 3.6
    private const val MPS_TO_MPH = 2.2369362920544

    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

    fun speedValue(speedMps: Double, unit: DistanceUnit): String {
        val safe = speedMps.coerceAtLeast(0.0)
        val converted = when (unit) {
            DistanceUnit.KILOMETERS -> safe * MPS_TO_KMH
            DistanceUnit.MILES -> safe * MPS_TO_MPH
        }
        return String.format(Locale.US, "%.1f", converted)
    }

    fun speedUnitLabel(unit: DistanceUnit): String = when (unit) {
        DistanceUnit.KILOMETERS -> "km/h"
        DistanceUnit.MILES -> "mph"
    }

    fun distanceValue(distanceMeters: Double, unit: DistanceUnit): String {
        val safe = distanceMeters.coerceAtLeast(0.0)
        val converted = when (unit) {
            DistanceUnit.KILOMETERS -> safe / METERS_PER_KM
            DistanceUnit.MILES -> safe / METERS_PER_MILE
        }
        return String.format(Locale.US, "%.2f", converted)
    }

    fun distanceUnitLabel(unit: DistanceUnit): String = when (unit) {
        DistanceUnit.KILOMETERS -> "km"
        DistanceUnit.MILES -> "mi"
    }

    fun paceFormatted(distanceMeters: Double, elapsedMs: Long, unit: DistanceUnit): String {
        val unitMeters = when (unit) {
            DistanceUnit.KILOMETERS -> METERS_PER_KM
            DistanceUnit.MILES -> METERS_PER_MILE
        }
        if (distanceMeters < 10.0 || elapsedMs <= 0L) {
            return "--:-- ${paceUnitLabel(unit)}"
        }
        val secondsPerUnit = (elapsedMs / 1000.0) / (distanceMeters / unitMeters)
        if (!secondsPerUnit.isFinite() || secondsPerUnit > 3599.0) {
            return "--:-- ${paceUnitLabel(unit)}"
        }
        val totalSeconds = secondsPerUnit.toInt()
        val mins = totalSeconds / 60
        val secs = totalSeconds % 60
        return String.format(Locale.US, "%d:%02d %s", mins, secs, paceUnitLabel(unit))
    }

    fun paceUnitLabel(unit: DistanceUnit): String = when (unit) {
        DistanceUnit.KILOMETERS -> "/km"
        DistanceUnit.MILES -> "/mi"
    }

    fun durationFormatted(elapsedMs: Long): String {
        val totalSeconds = (elapsedMs.coerceAtLeast(0L) / 1000L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun dateFormatted(epochMs: Long): String {
        val instant = Instant.ofEpochMilli(if (epochMs > 0L) epochMs else System.currentTimeMillis())
        return dateFormatter.format(instant.atZone(ZoneId.systemDefault()))
    }
}
