package com.example.data

import com.example.model.RoomMeta
import com.example.model.RoomState
import com.example.model.RoutePoint
import com.example.model.RunHistoryEntry
import com.example.model.RunnerLiveState
import com.example.model.RunnerStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Local in-memory fallback used ONLY while app/google-services.json still contains
 * placeholder values. Allows single-device testing of GPS tracking, maps, and share
 * cards without crashing before the user connects their Firebase Realtime Database.
 */
class LocalRoomSyncRepository(
    private val localHistoryStore: HistoryLocalStore
) : RoomSyncRepository {

    override val isFirebaseConfigured: Boolean = false

    private val roomsFlow = MutableStateFlow<Map<String, RoomState>>(emptyMap())

    override suspend fun createRoom(
        roomCode: String,
        runnerId: String,
        displayName: String
    ): Result<RoomMeta> {
        val now = System.currentTimeMillis()
        val meta = RoomMeta(
            roomCode = roomCode,
            createdAt = now,
            createdBy = runnerId,
            maxRunners = 2
        )
        val runner = RunnerLiveState(
            runnerId = runnerId,
            displayName = displayName,
            joinedAt = now,
            status = RunnerStatus.READY,
            lastUpdatedAt = now,
            accentSlot = 0
        )
        roomsFlow.update { current ->
            current + (roomCode to RoomState(
                meta = meta,
                runners = mapOf(runnerId to runner),
                paths = emptyMap(),
                isRemoteConnected = false
            ))
        }
        return Result.success(meta)
    }

    override suspend fun joinRoom(
        roomCode: String,
        runnerId: String,
        displayName: String
    ): Result<RoomMeta> {
        val existing = roomsFlow.value[roomCode]
        if (existing != null) {
            val runner = existing.runners[runnerId]
            if (runner?.status == RunnerStatus.FINISHED) {
                val resetRunner = runner.copy(
                    startedAt = 0L,
                    finishedAt = 0L,
                    status = RunnerStatus.READY,
                    lat = 0.0,
                    lng = 0.0,
                    speedMps = 0.0,
                    topSpeedMps = 0.0,
                    distanceMeters = 0.0,
                    elapsedMs = 0L,
                    gpsLocked = false,
                    lastUpdatedAt = System.currentTimeMillis()
                )
                roomsFlow.update { current ->
                    current + (roomCode to existing.copy(
                        runners = existing.runners + (runnerId to resetRunner),
                        paths = existing.paths + (runnerId to emptyList())
                    ))
                }
            }
            return Result.success(existing.meta)
        }
        // In local-only mode without Firebase, open the room locally so UI remains testable.
        return createRoom(roomCode, runnerId, displayName)
    }

    override fun observeRoom(roomCode: String): Flow<RoomState> {
        return roomsFlow.map { map ->
            map[roomCode] ?: RoomState(meta = RoomMeta(roomCode = roomCode))
        }
    }

    override suspend fun updateRunnerLiveState(roomCode: String, state: RunnerLiveState) {
        roomsFlow.update { current ->
            val room = current[roomCode] ?: RoomState(meta = RoomMeta(roomCode = roomCode))
            current + (roomCode to room.copy(runners = room.runners + (state.runnerId to state)))
        }
    }

    override suspend fun appendPathBatch(
        roomCode: String,
        runnerId: String,
        points: List<RoutePoint>
    ) {
        if (points.isEmpty()) return
        roomsFlow.update { current ->
            val room = current[roomCode] ?: return@update current
            val existing = room.paths[runnerId].orEmpty()
            current + (roomCode to room.copy(paths = room.paths + (runnerId to (existing + points))))
        }
    }

    override suspend fun triggerStartTogetherCountdown(
        roomCode: String,
        runnerId: String,
        serverTimeOffsetMs: Long
    ): Result<Long> {
        val target = System.currentTimeMillis() + 3400L
        roomsFlow.update { current ->
            val room = current[roomCode] ?: return@update current
            current + (roomCode to room.copy(
                meta = room.meta.copy(
                    countdownStartAt = target,
                    countdownInitiatedBy = runnerId
                )
            ))
        }
        return Result.success(target)
    }

    override suspend fun saveRunHistory(roomCode: String, entry: RunHistoryEntry) {
        localHistoryStore.upsert(entry)
    }

    override suspend fun fetchHistoryForRooms(roomCodes: Set<String>): List<RunHistoryEntry> {
        return localHistoryStore.loadAll()
    }
}
