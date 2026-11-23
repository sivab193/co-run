package com.example.data

import android.content.Context
import android.util.Log
import com.example.model.RoomMeta
import com.example.model.RoomState
import com.example.model.RoutePoint
import com.example.model.RunHistoryEntry
import com.example.model.RunnerLiveState
import com.example.model.RunnerRunSummary
import com.example.model.RunnerStatus
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

class FirebaseRoomSyncRepository(
    context: Context,
    private val localHistoryStore: HistoryLocalStore
) : RoomSyncRepository {

    companion object {
        private const val TAG = "FirebaseRoomSyncRepo"
        private const val NODE_ROOMS = "rooms"
        private const val NODE_META = "meta"
        private const val NODE_RUNNERS = "runners"
        private const val NODE_PATHS = "paths"
        private const val NODE_HISTORY = "history"
    }

    override val isFirebaseConfigured: Boolean = FirebaseConfigHelper.isConfigured(context)

    private val database: FirebaseDatabase by lazy {
        val url = FirebaseConfigHelper.getDatabaseUrl(context)
        val instance = if (url != null) {
            FirebaseDatabase.getInstance(url)
        } else {
            FirebaseDatabase.getInstance()
        }
        instance
    }

    private fun roomRef(roomCode: String): DatabaseReference =
        database.reference.child(NODE_ROOMS).child(roomCode)

    override suspend fun createRoom(
        roomCode: String,
        runnerId: String,
        displayName: String
    ): Result<RoomMeta> = runCatching {
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

        val updates = mapOf<String, Any>(
            NODE_META to meta.toMap(),
            "$NODE_RUNNERS/$runnerId" to runner.toMap()
        )
        roomRef(roomCode).updateChildren(updates).await()
        meta
    }

    override suspend fun joinRoom(
        roomCode: String,
        runnerId: String,
        displayName: String
    ): Result<RoomMeta> = runCatching {
        val snapshot = roomRef(roomCode).get().await()
        if (!snapshot.exists() || !snapshot.child(NODE_META).exists()) {
            error("Room $roomCode not found. Check the 6-character code and try again.")
        }

        val meta = snapshot.child(NODE_META).toRoomMeta(roomCode)
        val existingRunners = snapshot.child(NODE_RUNNERS).children.mapNotNull { it.key }.toSet()
        val alreadyMember = runnerId in existingRunners

        if (!alreadyMember && existingRunners.size >= meta.maxRunners) {
            error("Room $roomCode already has ${meta.maxRunners} runners.")
        }

        val accentSlot = if (alreadyMember) {
            snapshot.child(NODE_RUNNERS).child(runnerId).child("accentSlot")
                .getValue(Int::class.java) ?: 0
        } else {
            existingRunners.size.coerceAtMost(1)
        }

        val now = System.currentTimeMillis()
        if (!alreadyMember) {
            val runner = RunnerLiveState(
                runnerId = runnerId,
                displayName = displayName,
                joinedAt = now,
                status = RunnerStatus.READY,
                lastUpdatedAt = now,
                accentSlot = accentSlot
            )
            roomRef(roomCode).child(NODE_RUNNERS).child(runnerId).setValue(runner.toMap()).await()
        } else {
            roomRef(roomCode).child(NODE_RUNNERS).child(runnerId)
                .get()
                .await()
                .let { runnerSnapshot ->
                    val status = RunnerStatus.fromWire(
                        runnerSnapshot.child("status").getValue(String::class.java)
                    )
                    val updates = mutableMapOf<String, Any>(
                        "displayName" to displayName,
                        "lastUpdatedAt" to now
                    )
                    if (status == RunnerStatus.FINISHED) {
                        updates["status"] = RunnerStatus.READY.name
                        updates["startedAt"] = 0L
                        updates["finishedAt"] = 0L
                        updates["lat"] = 0.0
                        updates["lng"] = 0.0
                        updates["speedMps"] = 0.0
                        updates["topSpeedMps"] = 0.0
                        updates["distanceMeters"] = 0.0
                        updates["elapsedMs"] = 0L
                        updates["gpsLocked"] = false
                        roomRef(roomCode).child(NODE_PATHS).child(runnerId).removeValue().await()
                    }
                    roomRef(roomCode).child(NODE_RUNNERS).child(runnerId)
                        .updateChildren(updates)
                        .await()
                }
        }
        meta
    }

    override fun observeRoom(roomCode: String): Flow<RoomState> = callbackFlow {
        var currentMeta = RoomMeta(roomCode = roomCode)
        var currentRunners: Map<String, RunnerLiveState> = emptyMap()
        var currentPaths: Map<String, List<RoutePoint>> = emptyMap()
        var currentOffset = 0L
        var isConnected = false
        var lastError: String? = null

        fun emitState() {
            trySend(
                RoomState(
                    meta = currentMeta,
                    runners = currentRunners,
                    paths = currentPaths,
                    serverTimeOffsetMs = currentOffset,
                    isRemoteConnected = isConnected,
                    errorMessage = lastError
                )
            )
        }

        val offsetRef = database.getReference(".info/serverTimeOffset")
        val offsetListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                currentOffset = snapshot.getValue(Long::class.java) ?: 0L
                emitState()
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }

        val connectedRef = database.getReference(".info/connected")
        val connectedListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                isConnected = snapshot.getValue(Boolean::class.java) ?: false
                emitState()
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }

        val metaRef = roomRef(roomCode).child(NODE_META)
        val metaListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    currentMeta = snapshot.toRoomMeta(roomCode)
                    lastError = null
                    emitState()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Meta listener cancelled: ${error.message}")
                lastError = error.message
                emitState()
            }
        }

        val runnersRef = roomRef(roomCode).child(NODE_RUNNERS)
        val runnersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val map = LinkedHashMap<String, RunnerLiveState>()
                for (child in snapshot.children) {
                    val parsed = child.toRunnerLiveState()
                    if (parsed != null) {
                        map[parsed.runnerId] = parsed
                    }
                }
                currentRunners = map
                emitState()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Runners listener cancelled: ${error.message}")
                lastError = error.message
                emitState()
            }
        }

        val pathsRef = roomRef(roomCode).child(NODE_PATHS)
        val pathsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val map = LinkedHashMap<String, List<RoutePoint>>()
                for (runnerNode in snapshot.children) {
                    val rId = runnerNode.key ?: continue
                    val points = ArrayList<RoutePoint>()
                    for (ptNode in runnerNode.children) {
                        val lat = ptNode.child("lat").getValue(Double::class.java)
                        val lng = ptNode.child("lng").getValue(Double::class.java)
                        val t = ptNode.child("t").getValue(Long::class.java) ?: 0L
                        if (lat != null && lng != null) {
                            points.add(RoutePoint(lat, lng, t))
                        }
                    }
                    points.sortBy { it.timestampMs }
                    map[rId] = points
                }
                currentPaths = map
                emitState()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Paths listener cancelled: ${error.message}")
            }
        }

        offsetRef.addValueEventListener(offsetListener)
        connectedRef.addValueEventListener(connectedListener)
        metaRef.addValueEventListener(metaListener)
        runnersRef.addValueEventListener(runnersListener)
        pathsRef.addValueEventListener(pathsListener)

        awaitClose {
            offsetRef.removeEventListener(offsetListener)
            connectedRef.removeEventListener(connectedListener)
            metaRef.removeEventListener(metaListener)
            runnersRef.removeEventListener(runnersListener)
            pathsRef.removeEventListener(pathsListener)
        }
    }

    override suspend fun updateRunnerLiveState(roomCode: String, state: RunnerLiveState) {
        try {
            roomRef(roomCode)
                .child(NODE_RUNNERS)
                .child(state.runnerId)
                .updateChildren(state.toMap())
        } catch (e: Exception) {
            Log.w(TAG, "Failed updating runner state: ${e.message}")
        }
    }

    override suspend fun appendPathBatch(
        roomCode: String,
        runnerId: String,
        points: List<RoutePoint>
    ) {
        if (points.isEmpty()) return
        try {
            val pathRef = roomRef(roomCode).child(NODE_PATHS).child(runnerId)
            val updates = HashMap<String, Any>(points.size)
            for (pt in points) {
                val key = pathRef.push().key ?: "${pt.timestampMs}_${updates.size}"
                updates[key] = mapOf(
                    "lat" to pt.lat,
                    "lng" to pt.lng,
                    "t" to pt.timestampMs
                )
            }
            pathRef.updateChildren(updates)
        } catch (e: Exception) {
            Log.w(TAG, "Failed appending path batch: ${e.message}")
        }
    }

    override suspend fun triggerStartTogetherCountdown(
        roomCode: String,
        runnerId: String,
        serverTimeOffsetMs: Long
    ): Result<Long> = runCatching {
        val targetStartMs = System.currentTimeMillis() + serverTimeOffsetMs + 3400L
        roomRef(roomCode).child(NODE_META).updateChildren(
            mapOf(
                "countdownStartAt" to targetStartMs,
                "countdownInitiatedBy" to runnerId
            )
        ).await()
        targetStartMs
    }

    override suspend fun saveRunHistory(roomCode: String, entry: RunHistoryEntry) {
        localHistoryStore.upsert(entry)
        try {
            val runnersMap = LinkedHashMap<String, Any>()
            for (r in entry.runners) {
                val sampledPoints = downsamplePoints(r.points, maxPoints = 250)
                runnersMap[r.runnerId] = mapOf(
                    "runnerId" to r.runnerId,
                    "displayName" to r.displayName,
                    "distanceMeters" to r.distanceMeters,
                    "elapsedMs" to r.elapsedMs,
                    "topSpeedMps" to r.topSpeedMps,
                    "avgSpeedMps" to r.avgSpeedMps,
                    "accentSlot" to r.accentSlot,
                    "points" to sampledPoints.map { mapOf("lat" to it.lat, "lng" to it.lng, "t" to it.timestampMs) }
                )
            }
            val payload = mutableMapOf<String, Any>(
                "runId" to entry.runId,
                "roomCode" to entry.roomCode,
                "createdAt" to entry.createdAt,
                "runners" to runnersMap
            )
            if (!entry.shareImageBase64.isNullOrBlank()) {
                payload["shareImageBase64"] = entry.shareImageBase64
            }
            roomRef(roomCode).child(NODE_HISTORY).child(entry.runId).updateChildren(payload).await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed saving history to RTDB: ${e.message}")
        }
    }

    override suspend fun fetchHistoryForRooms(roomCodes: Set<String>): List<RunHistoryEntry> {
        val merged = LinkedHashMap<String, RunHistoryEntry>()
        for (local in localHistoryStore.loadAll()) {
            merged[local.runId] = local
        }
        for (code in roomCodes) {
            try {
                val snapshot = withTimeout(5_000) {
                    roomRef(code).child(NODE_HISTORY).get().await()
                }
                for (runNode in snapshot.children) {
                    val parsed = runNode.toRunHistoryEntry(code) ?: continue
                    val existing = merged[parsed.runId]
                    val combined = parsed.copy(localCardPath = existing?.localCardPath)
                    merged[parsed.runId] = combined
                    localHistoryStore.upsert(combined)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed fetching history for $code: ${e.message}")
            }
        }
        return merged.values.sortedByDescending { it.createdAt }
    }

    private fun downsamplePoints(points: List<RoutePoint>, maxPoints: Int): List<RoutePoint> {
        if (points.size <= maxPoints) return points
        val step = (points.size - 1).toDouble() / (maxPoints - 1).toDouble()
        return List(maxPoints) { i ->
            val idx = (i * step).toInt().coerceIn(0, points.lastIndex)
            points[idx]
        }
    }

    private fun RoomMeta.toMap(): Map<String, Any> = mapOf(
        "roomCode" to roomCode,
        "createdAt" to createdAt,
        "createdBy" to createdBy,
        "maxRunners" to maxRunners,
        "countdownStartAt" to countdownStartAt,
        "countdownInitiatedBy" to countdownInitiatedBy
    )

    private fun RunnerLiveState.toMap(): Map<String, Any> = mapOf(
        "runnerId" to runnerId,
        "displayName" to displayName,
        "joinedAt" to joinedAt,
        "startedAt" to startedAt,
        "finishedAt" to finishedAt,
        "status" to status.name,
        "lat" to lat,
        "lng" to lng,
        "speedMps" to speedMps,
        "topSpeedMps" to topSpeedMps,
        "distanceMeters" to distanceMeters,
        "elapsedMs" to elapsedMs,
        "gpsLocked" to gpsLocked,
        "lastUpdatedAt" to lastUpdatedAt,
        "accentSlot" to accentSlot
    )

    private fun DataSnapshot.toRoomMeta(fallbackCode: String): RoomMeta {
        return RoomMeta(
            roomCode = child("roomCode").getValue(String::class.java) ?: fallbackCode,
            createdAt = child("createdAt").getValue(Long::class.java) ?: 0L,
            createdBy = child("createdBy").getValue(String::class.java).orEmpty(),
            maxRunners = child("maxRunners").getValue(Int::class.java) ?: 2,
            countdownStartAt = child("countdownStartAt").getValue(Long::class.java) ?: 0L,
            countdownInitiatedBy = child("countdownInitiatedBy").getValue(String::class.java).orEmpty()
        )
    }

    private fun DataSnapshot.toRunnerLiveState(): RunnerLiveState? {
        val id = child("runnerId").getValue(String::class.java) ?: key ?: return null
        val name = child("displayName").getValue(String::class.java) ?: "Runner"
        return RunnerLiveState(
            runnerId = id,
            displayName = name,
            joinedAt = child("joinedAt").getValue(Long::class.java) ?: 0L,
            startedAt = child("startedAt").getValue(Long::class.java) ?: 0L,
            finishedAt = child("finishedAt").getValue(Long::class.java) ?: 0L,
            status = RunnerStatus.fromWire(child("status").getValue(String::class.java)),
            lat = child("lat").getValue(Double::class.java) ?: 0.0,
            lng = child("lng").getValue(Double::class.java) ?: 0.0,
            speedMps = child("speedMps").getValue(Double::class.java) ?: 0.0,
            topSpeedMps = child("topSpeedMps").getValue(Double::class.java) ?: 0.0,
            distanceMeters = child("distanceMeters").getValue(Double::class.java) ?: 0.0,
            elapsedMs = child("elapsedMs").getValue(Long::class.java) ?: 0L,
            gpsLocked = child("gpsLocked").getValue(Boolean::class.java) ?: false,
            lastUpdatedAt = child("lastUpdatedAt").getValue(Long::class.java) ?: 0L,
            accentSlot = child("accentSlot").getValue(Int::class.java) ?: 0
        )
    }

    private fun DataSnapshot.toRunHistoryEntry(fallbackRoomCode: String): RunHistoryEntry? {
        val runId = child("runId").getValue(String::class.java) ?: key ?: return null
        val roomCode = child("roomCode").getValue(String::class.java) ?: fallbackRoomCode
        val createdAt = child("createdAt").getValue(Long::class.java) ?: 0L
        val shareBase64 = child("shareImageBase64").getValue(String::class.java)
        val runners = ArrayList<RunnerRunSummary>()
        for (rNode in child("runners").children) {
            val rId = rNode.child("runnerId").getValue(String::class.java) ?: rNode.key ?: continue
            val pts = ArrayList<RoutePoint>()
            for (ptNode in rNode.child("points").children) {
                val lat = ptNode.child("lat").getValue(Double::class.java)
                val lng = ptNode.child("lng").getValue(Double::class.java)
                val t = ptNode.child("t").getValue(Long::class.java) ?: 0L
                if (lat != null && lng != null) pts.add(RoutePoint(lat, lng, t))
            }
            runners.add(
                RunnerRunSummary(
                    runnerId = rId,
                    displayName = rNode.child("displayName").getValue(String::class.java) ?: "Runner",
                    distanceMeters = rNode.child("distanceMeters").getValue(Double::class.java) ?: 0.0,
                    elapsedMs = rNode.child("elapsedMs").getValue(Long::class.java) ?: 0L,
                    topSpeedMps = rNode.child("topSpeedMps").getValue(Double::class.java) ?: 0.0,
                    avgSpeedMps = rNode.child("avgSpeedMps").getValue(Double::class.java) ?: 0.0,
                    accentSlot = rNode.child("accentSlot").getValue(Int::class.java) ?: 0,
                    points = pts
                )
            )
        }
        return RunHistoryEntry(
            runId = runId,
            roomCode = roomCode,
            createdAt = createdAt,
            runners = runners.sortedBy { it.accentSlot },
            shareImageBase64 = shareBase64
        )
    }
}
