package com.example.location

import com.example.model.RoutePoint
import com.example.model.RunnerStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class LocalRunTelemetry(
    val roomCode: String = "",
    val runnerId: String = "",
    val displayName: String = "",
    val accentSlot: Int = 0,
    val status: RunnerStatus = RunnerStatus.READY,
    val startedAt: Long = 0L,
    val finishedAt: Long = 0L,
    val elapsedMs: Long = 0L,
    val distanceMeters: Double = 0.0,
    val speedMps: Double = 0.0,
    val topSpeedMps: Double = 0.0,
    val currentLat: Double = 0.0,
    val currentLng: Double = 0.0,
    val gpsLocked: Boolean = false,
    val pathPoints: List<RoutePoint> = emptyList(),
    val isServiceRunning: Boolean = false
)

/**
 * Process-scoped holder bridging RunTrackingService (foreground service) and Compose ViewModels.
 */
object RunTrackerStateHolder {
    private val _telemetry = MutableStateFlow(LocalRunTelemetry())
    val telemetry: StateFlow<LocalRunTelemetry> = _telemetry.asStateFlow()

    fun update(transform: (LocalRunTelemetry) -> LocalRunTelemetry) {
        _telemetry.update(transform)
    }

    fun resetForRoom(roomCode: String, runnerId: String, displayName: String, accentSlot: Int) {
        val current = _telemetry.value
        if (current.roomCode == roomCode && current.isServiceRunning) return
        _telemetry.value = LocalRunTelemetry(
            roomCode = roomCode,
            runnerId = runnerId,
            displayName = displayName,
            accentSlot = accentSlot,
            status = RunnerStatus.READY
        )
    }

    fun clear() {
        _telemetry.value = LocalRunTelemetry()
    }
}
