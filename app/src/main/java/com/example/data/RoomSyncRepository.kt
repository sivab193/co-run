package com.example.data

import com.example.model.RoomMeta
import com.example.model.RoomState
import com.example.model.RoutePoint
import com.example.model.RunHistoryEntry
import com.example.model.RunnerLiveState
import kotlinx.coroutines.flow.Flow

interface RoomSyncRepository {
    val isFirebaseConfigured: Boolean

    suspend fun createRoom(
        roomCode: String,
        runnerId: String,
        displayName: String
    ): Result<RoomMeta>

    suspend fun joinRoom(
        roomCode: String,
        runnerId: String,
        displayName: String
    ): Result<RoomMeta>

    fun observeRoom(roomCode: String): Flow<RoomState>

    suspend fun updateRunnerLiveState(roomCode: String, state: RunnerLiveState)

    suspend fun appendPathBatch(roomCode: String, runnerId: String, points: List<RoutePoint>)

    suspend fun triggerStartTogetherCountdown(
        roomCode: String,
        runnerId: String,
        serverTimeOffsetMs: Long
    ): Result<Long>

    suspend fun saveRunHistory(roomCode: String, entry: RunHistoryEntry)

    suspend fun fetchHistoryForRooms(roomCodes: Set<String>): List<RunHistoryEntry>
}
