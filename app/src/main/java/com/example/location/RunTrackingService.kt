package com.example.location

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.RunTogetherApp
import com.example.model.DistanceUnit
import com.example.model.RoutePoint
import com.example.model.RunnerLiveState
import com.example.model.RunnerStatus
import com.example.model.UnitFormatter
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RunTrackingService : Service() {

    companion object {
        private const val TAG = "RunTrackingService"
        private const val CHANNEL_ID = "run_together_tracking_channel"
        private const val NOTIFICATION_ID = 4201

        const val ACTION_START = "com.example.location.ACTION_START"
        const val ACTION_PAUSE = "com.example.location.ACTION_PAUSE"
        const val ACTION_RESUME = "com.example.location.ACTION_RESUME"
        const val ACTION_STOP = "com.example.location.ACTION_STOP"

        const val EXTRA_ROOM_CODE = "extra_room_code"
        const val EXTRA_RUNNER_ID = "extra_runner_id"
        const val EXTRA_DISPLAY_NAME = "extra_display_name"
        const val EXTRA_ACCENT_SLOT = "extra_accent_slot"

        private const val GPS_STALE_TIMEOUT_MS = 8_000L
        private const val LIVE_SYNC_MIN_INTERVAL_MS = 450L
        private const val PATH_FLUSH_INTERVAL_MS = 5_000L
        private const val PATH_FLUSH_BATCH_SIZE = 5
        private const val MAX_ACCEPTABLE_ACCURACY_M = 35f
        private const val MIN_STEP_DISTANCE_M = 1.5f

        fun startRun(
            context: Context,
            roomCode: String,
            runnerId: String,
            displayName: String,
            accentSlot: Int
        ) {
            val intent = Intent(context, RunTrackingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ROOM_CODE, roomCode)
                putExtra(EXTRA_RUNNER_ID, runnerId)
                putExtra(EXTRA_DISPLAY_NAME, displayName)
                putExtra(EXTRA_ACCENT_SLOT, accentSlot)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun pauseRun(context: Context) {
            context.startService(
                Intent(context, RunTrackingService::class.java).apply { action = ACTION_PAUSE }
            )
        }

        fun resumeRun(context: Context) {
            context.startService(
                Intent(context, RunTrackingService::class.java).apply { action = ACTION_RESUME }
            )
        }

        fun stopRun(context: Context) {
            context.startService(
                Intent(context, RunTrackingService::class.java).apply { action = ACTION_STOP }
            )
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedClient: FusedLocationProviderClient
    private val speedSmoother = SpeedSmoother()

    private var timerJob: Job? = null
    private var lastLocation: Location? = null
    private var lastFixRealtimeMs: Long = 0L
    private var lastTickRealtimeMs: Long = 0L
    private var lastLiveSyncRealtimeMs: Long = 0L
    private var lastPathFlushRealtimeMs: Long = 0L
    private var cachedUnit: DistanceUnit = DistanceUnit.KILOMETERS

    private val pendingPathBatch = mutableListOf<RoutePoint>()
    private val batchLock = Any()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            for (loc in result.locations) {
                handleLocation(loc)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        serviceScope.launch {
            val app = application as RunTogetherApp
            cachedUnit = app.preferencesRepository.userProfile.first().distanceUnit
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val roomCode = intent.getStringExtra(EXTRA_ROOM_CODE).orEmpty()
                val runnerId = intent.getStringExtra(EXTRA_RUNNER_ID).orEmpty()
                val displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME).orEmpty()
                val accentSlot = intent.getIntExtra(EXTRA_ACCENT_SLOT, 0)
                beginTracking(roomCode, runnerId, displayName, accentSlot)
            }
            ACTION_PAUSE -> handlePause()
            ACTION_RESUME -> handleResume()
            ACTION_STOP -> handleStop()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopLocationUpdates()
        timerJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun beginTracking(
        roomCode: String,
        runnerId: String,
        displayName: String,
        accentSlot: Int
    ) {
        val nowEpoch = System.currentTimeMillis()
        val nowRealtime = SystemClock.elapsedRealtime()
        lastLocation = null
        lastFixRealtimeMs = 0L
        lastTickRealtimeMs = nowRealtime
        lastLiveSyncRealtimeMs = 0L
        lastPathFlushRealtimeMs = nowRealtime
        speedSmoother.reset()
        synchronized(batchLock) { pendingPathBatch.clear() }

        RunTrackerStateHolder.update { current ->
            val keepExistingRun = current.roomCode == roomCode &&
                current.startedAt > 0L &&
                current.status != RunnerStatus.FINISHED
            current.copy(
                roomCode = roomCode,
                runnerId = runnerId,
                displayName = displayName,
                accentSlot = accentSlot,
                status = RunnerStatus.RUNNING,
                startedAt = if (keepExistingRun) current.startedAt else nowEpoch,
                finishedAt = 0L,
                elapsedMs = if (keepExistingRun) current.elapsedMs else 0L,
                distanceMeters = if (keepExistingRun) current.distanceMeters else 0.0,
                speedMps = 0.0,
                topSpeedMps = if (keepExistingRun) current.topSpeedMps else 0.0,
                gpsLocked = false,
                pathPoints = if (keepExistingRun) current.pathPoints else emptyList(),
                isServiceRunning = true
            )
        }

        val notification = buildNotification(RunTrackerStateHolder.telemetry.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startLocationUpdates()
        startTickerLoop()
        pushLiveStateNow(force = true)
    }

    private fun handlePause() {
        speedSmoother.reset()
        lastLocation = null
        RunTrackerStateHolder.update {
            if (it.status == RunnerStatus.RUNNING) {
                it.copy(status = RunnerStatus.PAUSED, speedMps = 0.0)
            } else {
                it
            }
        }
        flushPendingPathBatch()
        pushLiveStateNow(force = true)
        updateNotification(RunTrackerStateHolder.telemetry.value)
    }

    private fun handleResume() {
        lastLocation = null
        lastTickRealtimeMs = SystemClock.elapsedRealtime()
        speedSmoother.reset()
        RunTrackerStateHolder.update {
            if (it.status == RunnerStatus.PAUSED) {
                it.copy(status = RunnerStatus.RUNNING)
            } else {
                it
            }
        }
        pushLiveStateNow(force = true)
        updateNotification(RunTrackerStateHolder.telemetry.value)
    }

    private fun handleStop() {
        stopLocationUpdates()
        timerJob?.cancel()
        val now = System.currentTimeMillis()
        RunTrackerStateHolder.update {
            it.copy(
                status = RunnerStatus.FINISHED,
                finishedAt = now,
                speedMps = 0.0,
                isServiceRunning = false
            )
        }
        flushPendingPathBatch()
        pushLiveStateNow(force = true)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startLocationUpdates() {
        val hasFine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            Log.w(TAG, "Location permission missing; cannot start FusedLocation updates.")
            return
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
            .setMinUpdateIntervalMillis(500L)
            .setWaitForAccurateLocation(false)
            .build()

        try {
            fusedClient.removeLocationUpdates(locationCallback)
            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException requesting location updates", e)
        }
    }

    private fun stopLocationUpdates() {
        try {
            fusedClient.removeLocationUpdates(locationCallback)
        } catch (_: Exception) {
        }
    }

    private fun handleLocation(location: Location) {
        if (location.hasAccuracy() && location.accuracy > MAX_ACCEPTABLE_ACCURACY_M) {
            return
        }

        val nowRealtime = SystemClock.elapsedRealtime()
        val nowEpoch = System.currentTimeMillis()
        lastFixRealtimeMs = nowRealtime

        val current = RunTrackerStateHolder.telemetry.value
        val isActivelyRunning = current.status == RunnerStatus.RUNNING

        var addedDistance = 0.0
        var derivedSpeedMps = 0.0
        val prev = lastLocation

        if (isActivelyRunning && prev != null) {
            val deltaMeters = prev.distanceTo(location).toDouble()
            val dtSec = ((location.elapsedRealtimeNanos - prev.elapsedRealtimeNanos) / 1e9)
                .coerceAtLeast(0.1)
            val segmentSpeed = deltaMeters / dtSec
            if (deltaMeters >= MIN_STEP_DISTANCE_M && segmentSpeed <= 15.0) {
                addedDistance = deltaMeters
                derivedSpeedMps = segmentSpeed
            }
        }

        val smoothedSpeed = if (isActivelyRunning) {
            val rawSpeed = if (location.hasSpeed() && location.speed >= 0f) {
                location.speed.toDouble()
            } else {
                derivedSpeedMps
            }
            speedSmoother.addSample(nowRealtime, rawSpeed)
        } else {
            0.0
        }

        val newPoint = RoutePoint(location.latitude, location.longitude, nowEpoch)
        val shouldRecordPoint = isActivelyRunning &&
            (current.pathPoints.isEmpty() || addedDistance >= MIN_STEP_DISTANCE_M)

        if (isActivelyRunning) {
            lastLocation = location
        }

        RunTrackerStateHolder.update { state ->
            val updatedTop = maxOf(state.topSpeedMps, smoothedSpeed)
            val updatedPath = if (shouldRecordPoint) {
                state.pathPoints + newPoint
            } else {
                state.pathPoints
            }
            state.copy(
                currentLat = location.latitude,
                currentLng = location.longitude,
                distanceMeters = state.distanceMeters + addedDistance,
                speedMps = smoothedSpeed,
                topSpeedMps = updatedTop,
                gpsLocked = true,
                pathPoints = updatedPath
            )
        }

        if (shouldRecordPoint) {
            val shouldFlushNow: Boolean
            synchronized(batchLock) {
                pendingPathBatch.add(newPoint)
                shouldFlushNow = pendingPathBatch.size >= PATH_FLUSH_BATCH_SIZE ||
                    (nowRealtime - lastPathFlushRealtimeMs) >= PATH_FLUSH_INTERVAL_MS
            }
            if (shouldFlushNow) {
                flushPendingPathBatch()
            }
        }

        pushLiveStateNow(force = false)
    }

    private fun startTickerLoop() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            val app = application as RunTogetherApp
            while (isActive) {
                delay(500L)
                val nowRealtime = SystemClock.elapsedRealtime()
                val delta = (nowRealtime - lastTickRealtimeMs).coerceIn(0L, 5_000L)
                lastTickRealtimeMs = nowRealtime

                val gpsStillFresh = lastFixRealtimeMs > 0L &&
                    (nowRealtime - lastFixRealtimeMs) <= GPS_STALE_TIMEOUT_MS

                RunTrackerStateHolder.update { state ->
                    val updatedElapsed = if (state.status == RunnerStatus.RUNNING) {
                        state.elapsedMs + delta
                    } else {
                        state.elapsedMs
                    }
                    val updatedSpeed = if (state.status == RunnerStatus.RUNNING && gpsStillFresh) {
                        speedSmoother.currentSpeedMps(nowRealtime)
                    } else {
                        0.0
                    }
                    state.copy(
                        elapsedMs = updatedElapsed,
                        gpsLocked = gpsStillFresh,
                        speedMps = updatedSpeed
                    )
                }

                if ((nowRealtime - lastPathFlushRealtimeMs) >= PATH_FLUSH_INTERVAL_MS) {
                    flushPendingPathBatch()
                }
                pushLiveStateNow(force = false)

                cachedUnit = app.preferencesRepository.userProfile.first().distanceUnit
                updateNotification(RunTrackerStateHolder.telemetry.value)
            }
        }
    }

    private fun pushLiveStateNow(force: Boolean) {
        val nowRealtime = SystemClock.elapsedRealtime()
        if (!force && (nowRealtime - lastLiveSyncRealtimeMs) < LIVE_SYNC_MIN_INTERVAL_MS) return
        lastLiveSyncRealtimeMs = nowRealtime

        val t = RunTrackerStateHolder.telemetry.value
        if (t.roomCode.isBlank() || t.runnerId.isBlank()) return

        val app = application as RunTogetherApp
        val wireState = RunnerLiveState(
            runnerId = t.runnerId,
            displayName = t.displayName.ifBlank { "Runner" },
            startedAt = t.startedAt,
            finishedAt = t.finishedAt,
            status = t.status,
            lat = t.currentLat,
            lng = t.currentLng,
            speedMps = t.speedMps,
            topSpeedMps = t.topSpeedMps,
            distanceMeters = t.distanceMeters,
            elapsedMs = t.elapsedMs,
            gpsLocked = t.gpsLocked,
            lastUpdatedAt = System.currentTimeMillis(),
            accentSlot = t.accentSlot
        )
        serviceScope.launch {
            app.roomSyncRepository.updateRunnerLiveState(t.roomCode, wireState)
        }
    }

    private fun flushPendingPathBatch() {
        val batch: List<RoutePoint>
        synchronized(batchLock) {
            if (pendingPathBatch.isEmpty()) return
            batch = pendingPathBatch.toList()
            pendingPathBatch.clear()
            lastPathFlushRealtimeMs = SystemClock.elapsedRealtime()
        }
        val t = RunTrackerStateHolder.telemetry.value
        if (t.roomCode.isBlank() || t.runnerId.isBlank()) return

        val app = application as RunTogetherApp
        serviceScope.launch {
            app.roomSyncRepository.appendPathBatch(t.roomCode, t.runnerId, batch)
        }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notif_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(telemetry: LocalRunTelemetry): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dist = UnitFormatter.distanceValue(telemetry.distanceMeters, cachedUnit)
        val distUnit = UnitFormatter.distanceUnitLabel(cachedUnit)
        val speedText = if (telemetry.gpsLocked) {
            "${UnitFormatter.speedValue(telemetry.speedMps, cachedUnit)} ${UnitFormatter.speedUnitLabel(cachedUnit)}"
        } else {
            getString(R.string.live_waiting_gps)
        }
        val statusPrefix = if (telemetry.status == RunnerStatus.PAUSED) "PAUSED · " else ""
        val contentText = "$statusPrefix$dist $distUnit  •  $speedText"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(telemetry: LocalRunTelemetry) {
        if (!telemetry.isServiceRunning) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(telemetry))
    }
}
