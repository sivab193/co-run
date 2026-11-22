package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.RunTogetherApp
import com.example.location.LocalRunTelemetry
import com.example.location.RunTrackerStateHolder
import com.example.location.RunTrackingService
import com.example.model.DistanceUnit
import com.example.model.RoomMeta
import com.example.model.RoomState
import com.example.model.RoutePoint
import com.example.model.RunHistoryEntry
import com.example.model.RunnerLiveState
import com.example.model.RunnerRunSummary
import com.example.model.RunnerStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.ceil

data class LiveRunUiState(
    val roomCode: String = "",
    val distanceUnit: DistanceUnit = DistanceUnit.KILOMETERS,
    val myRunner: RunnerLiveState? = null,
    val partnerRunner: RunnerLiveState? = null,
    val myPath: List<RoutePoint> = emptyList(),
    val partnerPath: List<RoutePoint> = emptyList(),
    val countdownSeconds: Int? = null,
    val canStartTogether: Boolean = false,
    val isFirebaseConfigured: Boolean = false,
    val isRemoteConnected: Boolean = false,
    val errorMessage: String? = null
)

class LiveRunViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as RunTogetherApp
    private val prefs = app.preferencesRepository
    private val syncRepo = app.roomSyncRepository

    private val _uiState = MutableStateFlow(
        LiveRunUiState(isFirebaseConfigured = syncRepo.isFirebaseConfigured)
    )
    val uiState: StateFlow<LiveRunUiState> = _uiState.asStateFlow()

    private var activeRoomCode: String = ""
    private var observeJob: Job? = null
    private var countdownJob: Job? = null
    private var lastHandledCountdownTarget: Long = 0L
    private var latestRoomState: RoomState = RoomState(meta = RoomMeta(roomCode = ""))

    fun bindRoom(roomCode: String) {
        if (activeRoomCode == roomCode && observeJob != null) return
        activeRoomCode = roomCode
        observeJob?.cancel()
        countdownJob?.cancel()

        observeJob = viewModelScope.launch {
            val runnerId = prefs.ensureRunnerId()
            val profile = prefs.userProfile.first()
            RunTrackerStateHolder.resetForRoom(
                roomCode = roomCode,
                runnerId = runnerId,
                displayName = profile.displayName.ifBlank { "Runner" },
                accentSlot = 0
            )

            combine(
                syncRepo.observeRoom(roomCode),
                RunTrackerStateHolder.telemetry,
                prefs.userProfile
            ) { roomState, localTelemetry, currentProfile ->
                latestRoomState = roomState
                buildUiState(roomCode, roomState, localTelemetry, currentProfile.runnerId, currentProfile.displayName, currentProfile.distanceUnit)
            }.collect { state ->
                _uiState.value = state
                evaluateCountdown(latestRoomState, state.myRunner?.status)
            }
        }
    }

    private fun buildUiState(
        roomCode: String,
        roomState: RoomState,
        local: LocalRunTelemetry,
        myId: String,
        myName: String,
        unit: DistanceUnit
    ): LiveRunUiState {
        val remoteMe = roomState.runners[myId]
        val useLocal = local.roomCode == roomCode && (local.isServiceRunning || local.status != RunnerStatus.READY)

        val myRunner = if (useLocal) {
            RunnerLiveState(
                runnerId = myId,
                displayName = myName.ifBlank { remoteMe?.displayName ?: "You" },
                startedAt = local.startedAt,
                finishedAt = local.finishedAt,
                status = local.status,
                lat = local.currentLat,
                lng = local.currentLng,
                speedMps = local.speedMps,
                topSpeedMps = local.topSpeedMps,
                distanceMeters = local.distanceMeters,
                elapsedMs = local.elapsedMs,
                gpsLocked = local.gpsLocked,
                accentSlot = remoteMe?.accentSlot ?: 0
            )
        } else {
            remoteMe ?: RunnerLiveState(
                runnerId = myId,
                displayName = myName.ifBlank { "You" },
                status = RunnerStatus.READY,
                accentSlot = 0
            )
        }

        val partner = roomState.runners.values
            .filter { it.runnerId != myId }
            .minByOrNull { it.joinedAt }

        val remoteMyPath = roomState.paths[myId].orEmpty()
        val myPath = if (local.roomCode == roomCode && local.pathPoints.size >= remoteMyPath.size) {
            local.pathPoints
        } else {
            remoteMyPath
        }
        val partnerPath = partner?.let { roomState.paths[it.runnerId].orEmpty() }.orEmpty()

        val canStartTogether = myRunner.status == RunnerStatus.READY &&
            partner != null &&
            partner.status == RunnerStatus.READY

        return LiveRunUiState(
            roomCode = roomCode,
            distanceUnit = unit,
            myRunner = myRunner,
            partnerRunner = partner,
            myPath = myPath,
            partnerPath = partnerPath,
            countdownSeconds = _uiState.value.countdownSeconds,
            canStartTogether = canStartTogether,
            isFirebaseConfigured = syncRepo.isFirebaseConfigured,
            isRemoteConnected = roomState.isRemoteConnected,
            errorMessage = roomState.errorMessage
        )
    }

    private fun evaluateCountdown(roomState: RoomState, myStatus: RunnerStatus?) {
        val target = roomState.meta.countdownStartAt
        if (target <= 0L || target == lastHandledCountdownTarget) return
        if (myStatus != RunnerStatus.READY) return

        val nowAdjusted = System.currentTimeMillis() + roomState.serverTimeOffsetMs
        val remainingMs = target - nowAdjusted
        if (remainingMs <= -1_500L) {
            lastHandledCountdownTarget = target
            return
        }

        lastHandledCountdownTarget = target
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis() + latestRoomState.serverTimeOffsetMs
                val left = target - now
                if (left <= 0L) {
                    _uiState.value = _uiState.value.copy(countdownSeconds = null)
                    startMyRun(getApplication())
                    break
                }
                val sec = ceil(left / 1000.0).toInt().coerceIn(1, 3)
                _uiState.value = _uiState.value.copy(countdownSeconds = sec)
                delay(100L)
            }
        }
    }

    fun startMyRun(context: Context) {
        val state = _uiState.value
        val me = state.myRunner ?: return
        if (me.status == RunnerStatus.RUNNING) return
        RunTrackingService.startRun(
            context = context.applicationContext,
            roomCode = state.roomCode,
            runnerId = me.runnerId,
            displayName = me.displayName,
            accentSlot = me.accentSlot
        )
    }

    fun triggerStartTogether() {
        val code = activeRoomCode
        if (code.isBlank()) return
        val myId = _uiState.value.myRunner?.runnerId ?: return
        viewModelScope.launch {
            syncRepo.triggerStartTogetherCountdown(
                roomCode = code,
                runnerId = myId,
                serverTimeOffsetMs = latestRoomState.serverTimeOffsetMs
            )
        }
    }

    fun pauseMyRun(context: Context) {
        RunTrackingService.pauseRun(context.applicationContext)
    }

    fun resumeMyRun(context: Context) {
        RunTrackingService.resumeRun(context.applicationContext)
    }

    fun endMyRun(context: Context, onFinished: (String) -> Unit) {
        val snapshot = _uiState.value
        RunTrackingService.stopRun(context.applicationContext)

        viewModelScope.launch {
            val me = snapshot.myRunner
            val partner = snapshot.partnerRunner
            val summaries = buildList {
                if (me != null) {
                    val avg = if (me.elapsedMs > 0L) me.distanceMeters / (me.elapsedMs / 1000.0) else 0.0
                    add(
                        RunnerRunSummary(
                            runnerId = me.runnerId,
                            displayName = me.displayName,
                            distanceMeters = me.distanceMeters,
                            elapsedMs = me.elapsedMs,
                            topSpeedMps = me.topSpeedMps,
                            avgSpeedMps = avg,
                            accentSlot = 0,
                            points = snapshot.myPath
                        )
                    )
                }
                if (partner != null) {
                    val avg = if (partner.elapsedMs > 0L) {
                        partner.distanceMeters / (partner.elapsedMs / 1000.0)
                    } else 0.0
                    add(
                        RunnerRunSummary(
                            runnerId = partner.runnerId,
                            displayName = partner.displayName,
                            distanceMeters = partner.distanceMeters,
                            elapsedMs = partner.elapsedMs,
                            topSpeedMps = partner.topSpeedMps,
                            avgSpeedMps = avg,
                            accentSlot = 1,
                            points = snapshot.partnerPath
                        )
                    )
                }
            }

            // Use roomCode as stable run ID for this session so when the second runner finishes,
            // both runners' final routes & stats merge into the same room history entry.
            val runId = "run_${snapshot.roomCode}"
            val entry = RunHistoryEntry(
                runId = runId,
                roomCode = snapshot.roomCode,
                createdAt = System.currentTimeMillis(),
                runners = summaries
            )
            syncRepo.saveRunHistory(snapshot.roomCode, entry)
            onFinished(runId)
        }
    }

    override fun onCleared() {
        observeJob?.cancel()
        countdownJob?.cancel()
        super.onCleared()
    }
}
