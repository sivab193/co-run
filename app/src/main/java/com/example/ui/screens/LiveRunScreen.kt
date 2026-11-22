package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.map.DualRouteMapView
import com.example.map.MapFocusMode
import com.example.model.DistanceUnit
import com.example.model.RoomCodeGenerator
import com.example.model.RoutePoint
import com.example.model.RunnerLiveState
import com.example.model.RunnerStatus
import com.example.model.UnitFormatter
import com.example.ui.theme.BarlowCondensedFamily
import com.example.ui.theme.CarbonElevated
import com.example.ui.theme.CarbonOutline
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.EndRed
import com.example.ui.theme.EndRedContainer
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.InkWhite
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LiveGreen
import com.example.ui.theme.LiveGreenContainer
import com.example.ui.theme.MetricHugeStyle
import com.example.ui.theme.MetricUnitStyle
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PausedAmber
import com.example.ui.theme.PausedAmberContainer
import com.example.ui.theme.RunnerBlue
import com.example.ui.theme.RunnerBlueContainer
import com.example.ui.theme.RunnerOrange
import com.example.ui.theme.RunnerOrangeContainer
import com.example.ui.viewmodel.LiveRunViewModel

@Composable
fun LiveRunScreen(
    roomCode: String,
    onRunFinished: (runId: String) -> Unit,
    onLeaveRoom: () -> Unit,
    liveViewModel: LiveRunViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by liveViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(roomCode) {
        liveViewModel.bindRoom(roomCode)
    }

    var mapFocusMode by remember { mutableStateOf(MapFocusMode.ME) }
    var focusToken by remember { mutableIntStateOf(0) }
    var showEndConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var showLeaveConfirmDialog by rememberSaveable { mutableStateOf(false) }

    // Permission flow state
    var pendingStartAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showForegroundRationale by remember { mutableStateOf(false) }
    var showBackgroundRationale by remember { mutableStateOf(false) }
    var askedBackgroundThisSession by rememberSaveable { mutableStateOf(false) }

    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Whether user chose "All the time" or "While using the app", start the foreground service.
        askedBackgroundThisSession = true
        pendingStartAction?.invoke()
        pendingStartAction = null
    }

    val foregroundPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val hasLocation = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!hasLocation) {
            pendingStartAction = null
            return@rememberLauncherForActivityResult
        }
        val needsBg = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !askedBackgroundThisSession &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) != PackageManager.PERMISSION_GRANTED

        if (needsBg) {
            showBackgroundRationale = true
        } else {
            pendingStartAction?.invoke()
            pendingStartAction = null
        }
    }

    fun requestPermissionsThen(action: () -> Unit) {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine) {
            pendingStartAction = action
            showForegroundRationale = true
            return
        }

        val needsBg = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !askedBackgroundThisSession &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) != PackageManager.PERMISSION_GRANTED

        if (needsBg) {
            pendingStartAction = action
            showBackgroundRationale = true
        } else {
            action()
        }
    }

    val myStatus = uiState.myRunner?.status ?: RunnerStatus.READY
    val isActivelyRunningOrPaused = myStatus == RunnerStatus.RUNNING || myStatus == RunnerStatus.PAUSED

    BackHandler {
        if (isActivelyRunningOrPaused) {
            showLeaveConfirmDialog = true
        } else {
            onLeaveRoom()
        }
    }

    fun shareRoomInvite() {
        val name = uiState.myRunner?.displayName.orEmpty().ifBlank { "Your partner" }
        val text = RoomCodeGenerator.buildShareMessage(name, roomCode)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share Run Together room"))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Minimal top strip: back, room code, share button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = {
                        if (isActivelyRunningOrPaused) {
                            showLeaveConfirmDialog = true
                        } else {
                            onLeaveRoom()
                        }
                    },
                    modifier = Modifier.testTag("live_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Leave room",
                        tint = InkWhite
                    )
                }

                Surface(
                    color = CarbonSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CarbonOutline)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (uiState.isRemoteConnected) LiveGreen else PausedAmber
                                )
                        )
                        Text(
                            text = "${stringResource(R.string.live_room_code_prefix)} $roomCode",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            letterSpacing = 1.6.sp,
                            color = InkWhite
                        )
                    }
                }

                IconButton(
                    onClick = { shareRoomInvite() },
                    modifier = Modifier.testTag("share_room_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = stringResource(R.string.live_share_code),
                        tint = RunnerOrange
                    )
                }
            }

            // Runner Telemetry Cards: strictly 2 numbers per runner (Speed & Distance)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Top card: ME (Orange)
                RunnerMetricsCard(
                    runner = uiState.myRunner,
                    unit = uiState.distanceUnit,
                    accentColor = RunnerOrange,
                    accentContainer = RunnerOrangeContainer,
                    isMe = true,
                    modifier = Modifier.testTag("my_metrics_card")
                )

                // Second card: PARTNER (Blue)
                val partner = uiState.partnerRunner
                if (partner != null) {
                    RunnerMetricsCard(
                        runner = partner,
                        unit = uiState.distanceUnit,
                        accentColor = RunnerBlue,
                        accentContainer = RunnerBlueContainer,
                        isMe = false,
                        modifier = Modifier.testTag("partner_metrics_card")
                    )
                } else {
                    WaitingForPartnerCard(
                        onShareCode = { shareRoomInvite() },
                        modifier = Modifier.testTag("waiting_partner_card")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Live Dual-Route OpenStreetMap directly below the numbers (same screen)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 14.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(CarbonSurface)
            ) {
                val myLoc = uiState.myRunner?.takeIf { it.lat != 0.0 || it.lng != 0.0 }
                    ?.let { RoutePoint(it.lat, it.lng, it.lastUpdatedAt) }
                val partnerLoc = uiState.partnerRunner?.takeIf { it.lat != 0.0 || it.lng != 0.0 }
                    ?.let { RoutePoint(it.lat, it.lng, it.lastUpdatedAt) }

                DualRouteMapView(
                    myPath = uiState.myPath,
                    myCurrentLocation = myLoc,
                    partnerPath = uiState.partnerPath,
                    partnerCurrentLocation = partnerLoc,
                    focusMode = mapFocusMode,
                    focusRequestToken = focusToken,
                    modifier = Modifier.fillMaxSize()
                )

                // One-thumb floating camera focus chips (Me / Partner / Both)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(ObsidianBg.copy(alpha = 0.86f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    MapFocusChip(
                        label = stringResource(R.string.live_map_focus_me),
                        selected = mapFocusMode == MapFocusMode.ME,
                        accent = RunnerOrange,
                        onClick = {
                            mapFocusMode = MapFocusMode.ME
                            focusToken++
                        },
                        tag = "map_focus_me"
                    )
                    MapFocusChip(
                        label = stringResource(R.string.live_map_focus_partner),
                        selected = mapFocusMode == MapFocusMode.PARTNER,
                        accent = RunnerBlue,
                        onClick = {
                            mapFocusMode = MapFocusMode.PARTNER
                            focusToken++
                        },
                        tag = "map_focus_partner"
                    )
                    MapFocusChip(
                        label = stringResource(R.string.live_map_focus_both),
                        selected = mapFocusMode == MapFocusMode.BOTH,
                        accent = InkWhite,
                        onClick = {
                            mapFocusMode = MapFocusMode.BOTH
                            focusToken++
                        },
                        tag = "map_focus_both"
                    )
                }
            }

            // Bottom Run Controls Dock (thumb-friendly, respects navigation bar insets)
            LiveControlDock(
                status = myStatus,
                canStartTogether = uiState.canStartTogether,
                onStartMine = {
                    requestPermissionsThen { liveViewModel.startMyRun(context) }
                },
                onStartTogether = {
                    requestPermissionsThen { liveViewModel.triggerStartTogether() }
                },
                onPause = { liveViewModel.pauseMyRun(context) },
                onResume = { liveViewModel.resumeMyRun(context) },
                onRequestEnd = { showEndConfirmDialog = true },
                onViewSummary = { onRunFinished("run_$roomCode") }
            )
        }

        // Optional synchronized 3-2-1 "Start together" full-screen countdown overlay
        AnimatedVisibility(
            visible = uiState.countdownSeconds != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ObsidianBg.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "STARTING TOGETHER",
                        style = MaterialTheme.typography.labelLarge,
                        color = RunnerBlue,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "${uiState.countdownSeconds ?: ""}",
                        fontFamily = BarlowCondensedFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 148.sp,
                        color = RunnerOrange
                    )
                }
            }
        }
    }

    // End run confirmation dialog
    if (showEndConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showEndConfirmDialog = false },
            containerColor = CarbonElevated,
            titleContentColor = InkWhite,
            textContentColor = InkSecondary,
            title = { Text(stringResource(R.string.live_end_confirm_title)) },
            text = { Text(stringResource(R.string.live_end_confirm_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        showEndConfirmDialog = false
                        liveViewModel.endMyRun(context) { runId ->
                            onRunFinished(runId)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EndRed,
                        contentColor = InkWhite
                    ),
                    modifier = Modifier.testTag("confirm_end_run_button")
                ) {
                    Text(stringResource(R.string.live_end_run))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndConfirmDialog = false }) {
                    Text(stringResource(R.string.home_cancel), color = InkSecondary)
                }
            }
        )
    }

    // Leave room confirmation dialog if run is active
    if (showLeaveConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirmDialog = false },
            containerColor = CarbonElevated,
            titleContentColor = InkWhite,
            textContentColor = InkSecondary,
            title = { Text("Run still active") },
            text = { Text("Pause or end your run first, or leave the screen while GPS keeps tracking in the notification.") },
            confirmButton = {
                Button(
                    onClick = {
                        showLeaveConfirmDialog = false
                        onLeaveRoom()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RunnerOrange,
                        contentColor = ObsidianBg
                    )
                ) {
                    Text("Leave screen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirmDialog = false }) {
                    Text("Stay", color = InkSecondary)
                }
            }
        )
    }

    // Step 1: Foreground location + notification permission rationale
    if (showForegroundRationale) {
        AlertDialog(
            onDismissRequest = {
                showForegroundRationale = false
                pendingStartAction = null
            },
            containerColor = CarbonElevated,
            titleContentColor = InkWhite,
            textContentColor = InkSecondary,
            title = { Text(stringResource(R.string.perm_location_title)) },
            text = { Text(stringResource(R.string.perm_location_rationale)) },
            confirmButton = {
                Button(
                    onClick = {
                        showForegroundRationale = false
                        val perms = buildList {
                            add(Manifest.permission.ACCESS_FINE_LOCATION)
                            add(Manifest.permission.ACCESS_COARSE_LOCATION)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        foregroundPermissionsLauncher.launch(perms.toTypedArray())
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RunnerOrange,
                        contentColor = ObsidianBg
                    ),
                    modifier = Modifier.testTag("grant_location_permission_button")
                ) {
                    Text(stringResource(R.string.perm_grant))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showForegroundRationale = false
                        pendingStartAction = null
                    }
                ) {
                    Text(stringResource(R.string.home_cancel), color = InkSecondary)
                }
            }
        )
    }

    // Step 2: Background location rationale (Android 10+)
    if (showBackgroundRationale) {
        AlertDialog(
            onDismissRequest = {
                showBackgroundRationale = false
                askedBackgroundThisSession = true
                pendingStartAction?.invoke()
                pendingStartAction = null
            },
            containerColor = CarbonElevated,
            titleContentColor = InkWhite,
            textContentColor = InkSecondary,
            title = { Text(stringResource(R.string.perm_bg_location_title)) },
            text = { Text(stringResource(R.string.perm_bg_location_rationale)) },
            confirmButton = {
                Button(
                    onClick = {
                        showBackgroundRationale = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            backgroundPermissionLauncher.launch(
                                Manifest.permission.ACCESS_BACKGROUND_LOCATION
                            )
                        } else {
                            pendingStartAction?.invoke()
                            pendingStartAction = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RunnerOrange,
                        contentColor = ObsidianBg
                    )
                ) {
                    Text(stringResource(R.string.perm_grant))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showBackgroundRationale = false
                        askedBackgroundThisSession = true
                        pendingStartAction?.invoke()
                        pendingStartAction = null
                    }
                ) {
                    Text(stringResource(R.string.perm_skip_bg), color = InkSecondary)
                }
            }
        )
    }
}

@Composable
private fun RunnerMetricsCard(
    runner: RunnerLiveState?,
    unit: DistanceUnit,
    accentColor: Color,
    accentContainer: Color,
    isMe: Boolean,
    modifier: Modifier = Modifier
) {
    val status = runner?.status ?: RunnerStatus.READY
    val isPaused = status == RunnerStatus.PAUSED
    val isWaitingGps = status == RunnerStatus.RUNNING && runner?.gpsLocked == false

    val borderColor = when {
        isPaused -> PausedAmber.copy(alpha = 0.85f)
        status == RunnerStatus.RUNNING -> accentColor.copy(alpha = 0.7f)
        else -> CarbonOutline
    }

    Surface(
        color = if (isPaused) PausedAmberContainer.copy(alpha = 0.45f) else CarbonSurface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(if (isPaused) 2.dp else 1.5.dp, borderColor),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Name + crisp status pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(11.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                    Text(
                        text = buildString {
                            append(runner?.displayName ?: if (isMe) "You" else "Partner")
                            if (isMe) append(" (You)")
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = InkWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                StatusPill(status = status, accentColor = accentColor, accentContainer = accentContainer)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Exactly TWO big high-contrast numbers: Speed & Distance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                // Metric 1: Current Speed (or "Waiting for GPS")
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.live_label_speed),
                        style = MaterialTheme.typography.labelSmall,
                        color = InkSecondary
                    )
                    if (isWaitingGps) {
                        Text(
                            text = stringResource(R.string.live_waiting_gps),
                            style = MaterialTheme.typography.headlineMedium,
                            color = PausedAmber,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                    } else {
                        val speedStr = if (status == RunnerStatus.PAUSED || status == RunnerStatus.READY || status == RunnerStatus.FINISHED) {
                            "0.0"
                        } else {
                            UnitFormatter.speedValue(runner?.speedMps ?: 0.0, unit)
                        }
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = speedStr,
                                style = MetricHugeStyle,
                                color = if (isPaused) InkSecondary else accentColor
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = UnitFormatter.speedUnitLabel(unit),
                                style = MetricUnitStyle,
                                color = InkSecondary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }
                }

                // Metric 2: Distance so far
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = stringResource(R.string.live_label_distance),
                        style = MaterialTheme.typography.labelSmall,
                        color = InkSecondary
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = UnitFormatter.distanceValue(runner?.distanceMeters ?: 0.0, unit),
                            style = MetricHugeStyle,
                            color = if (isPaused) InkSecondary else InkWhite
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = UnitFormatter.distanceUnitLabel(unit),
                            style = MetricUnitStyle,
                            color = InkSecondary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(
    status: RunnerStatus,
    accentColor: Color,
    accentContainer: Color
) {
    val (label, bg, fg) = when (status) {
        RunnerStatus.READY -> Triple(stringResource(R.string.live_status_ready), accentContainer, accentColor)
        RunnerStatus.RUNNING -> Triple(stringResource(R.string.live_status_running), LiveGreenContainer, LiveGreen)
        RunnerStatus.PAUSED -> Triple(stringResource(R.string.live_status_paused), PausedAmberContainer, PausedAmber)
        RunnerStatus.FINISHED -> Triple(stringResource(R.string.live_status_finished), CarbonElevated, InkSecondary)
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun WaitingForPartnerCard(
    onShareCode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = CarbonSurface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, RunnerBlue.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(RunnerBlue)
                )
                Text(
                    text = stringResource(R.string.live_waiting_partner),
                    style = MaterialTheme.typography.bodyLarge,
                    color = InkSecondary
                )
            }
            TextButton(
                onClick = onShareCode,
                modifier = Modifier.testTag("waiting_share_code_button")
            ) {
                Text(
                    text = stringResource(R.string.live_share_code),
                    color = RunnerBlue,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun MapFocusChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    tag: String
) {
    Surface(
        color = if (selected) accent else Color.Transparent,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) ObsidianBg else InkSecondary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun LiveControlDock(
    status: RunnerStatus,
    canStartTogether: Boolean,
    onStartMine: () -> Unit,
    onStartTogether: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRequestEnd: () -> Unit,
    onViewSummary: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (status) {
            RunnerStatus.READY -> {
                if (canStartTogether) {
                    OutlinedButton(
                        onClick = onStartTogether,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.5.dp, RunnerBlue),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = RunnerBlueContainer.copy(alpha = 0.6f),
                            contentColor = RunnerBlue
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("start_together_button")
                    ) {
                        Icon(imageVector = Icons.Rounded.Group, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.live_start_together),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                Button(
                    onClick = onStartMine,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RunnerOrange,
                        contentColor = ObsidianBg
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .testTag("start_run_button")
                ) {
                    Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.live_start_my_run),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }

            RunnerStatus.RUNNING -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onPause,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PausedAmber,
                            contentColor = ObsidianBg
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp)
                            .testTag("pause_run_button")
                    ) {
                        Icon(imageVector = Icons.Rounded.Pause, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.live_pause),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Button(
                        onClick = onRequestEnd,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EndRedContainer,
                            contentColor = EndRed
                        ),
                        border = BorderStroke(1.5.dp, EndRed.copy(alpha = 0.7f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp)
                            .testTag("end_run_button")
                    ) {
                        Icon(imageVector = Icons.Rounded.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.live_end_run),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            RunnerStatus.PAUSED -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onResume,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LiveGreen,
                            contentColor = ObsidianBg
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp)
                            .testTag("resume_run_button")
                    ) {
                        Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.live_resume),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Button(
                        onClick = onRequestEnd,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EndRedContainer,
                            contentColor = EndRed
                        ),
                        border = BorderStroke(1.5.dp, EndRed.copy(alpha = 0.7f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp)
                            .testTag("end_run_button")
                    ) {
                        Icon(imageVector = Icons.Rounded.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.live_end_run),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            RunnerStatus.FINISHED -> {
                Button(
                    onClick = onViewSummary,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RunnerOrange,
                        contentColor = ObsidianBg
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .testTag("view_summary_button")
                ) {
                    Text(
                        text = stringResource(R.string.summary_share),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}
