package com.example.model

/**
 * Per-phone unit preference. All canonical measurements in Firebase and GPS tracking
 * are stored in SI units (meters, meters/second, milliseconds) and formatted locally.
 */
enum class DistanceUnit {
    KILOMETERS,
    MILES
}

enum class RunnerStatus {
    READY,
    RUNNING,
    PAUSED,
    FINISHED;

    companion object {
        fun fromWire(value: String?): RunnerStatus {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: READY
        }
    }
}

data class RoutePoint(
    val lat: Double,
    val lng: Double,
    val timestampMs: Long = 0L
)

/**
 * Compact live telemetry state synced for each runner under rooms/<roomCode>/runners/<runnerId>.
 * Kept intentionally small so frequent ticks use negligible Firebase Spark bandwidth.
 */
data class RunnerLiveState(
    val runnerId: String,
    val displayName: String,
    val joinedAt: Long = 0L,
    val startedAt: Long = 0L,
    val finishedAt: Long = 0L,
    val status: RunnerStatus = RunnerStatus.READY,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val speedMps: Double = 0.0,
    val topSpeedMps: Double = 0.0,
    val distanceMeters: Double = 0.0,
    val elapsedMs: Long = 0L,
    val gpsLocked: Boolean = false,
    val lastUpdatedAt: Long = 0L,
    val accentSlot: Int = 0
)

data class RoomMeta(
    val roomCode: String,
    val createdAt: Long = 0L,
    val createdBy: String = "",
    /** v1 enforces 2 runners; data model supports expanding maxRunners in Phase 2. */
    val maxRunners: Int = 2,
    /** Synchronized epoch ms when an optional 3-2-1 "Start together" countdown finishes. */
    val countdownStartAt: Long = 0L,
    val countdownInitiatedBy: String = ""
)

/**
 * Full observed state of an active room.
 * Phase 2 hooks (voice room ID, Spotify Jam link, nudges, streaks) attach cleanly
 * as an optional sibling node under rooms/<roomCode>/extensions without breaking v1.
 */
data class RoomState(
    val meta: RoomMeta,
    val runners: Map<String, RunnerLiveState> = emptyMap(),
    val paths: Map<String, List<RoutePoint>> = emptyMap(),
    val serverTimeOffsetMs: Long = 0L,
    val isRemoteConnected: Boolean = false,
    val errorMessage: String? = null
)

data class RunnerRunSummary(
    val runnerId: String,
    val displayName: String,
    val distanceMeters: Double,
    val elapsedMs: Long,
    val topSpeedMps: Double,
    val avgSpeedMps: Double,
    val accentSlot: Int,
    val points: List<RoutePoint>
)

data class RunHistoryEntry(
    val runId: String,
    val roomCode: String,
    val createdAt: Long,
    val runners: List<RunnerRunSummary>,
    val localCardPath: String? = null,
    val shareImageBase64: String? = null
)
