package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.model.RoomCodeGenerator
import com.example.ui.theme.CarbonElevated
import com.example.ui.theme.CarbonOutline
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.EndRed
import com.example.ui.theme.EndRedContainer
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.InkWhite
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PausedAmber
import com.example.ui.theme.PausedAmberContainer
import com.example.ui.theme.RoomCodeStyle
import com.example.ui.theme.RunnerBlue
import com.example.ui.theme.RunnerOrange
import com.example.ui.viewmodel.HomeActionState

@Composable
fun HomeScreen(
    displayName: String,
    isFirebaseConfigured: Boolean,
    actionState: HomeActionState,
    onCreateRoom: () -> Unit,
    onJoinRoom: (String) -> Unit,
    onClearError: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var showJoinDialog by rememberSaveable { mutableStateOf(false) }
    var joinCodeInput by rememberSaveable { mutableStateOf("") }
    var showFirebaseSetupDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = ObsidianBg
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(RunnerOrange)
                            )
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(RunnerBlue)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "RUN TOGETHER",
                                style = MaterialTheme.typography.labelMedium,
                                color = InkSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.headlineLarge,
                            color = InkWhite
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = onOpenHistory,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(CarbonSurface)
                                .testTag("home_history_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = stringResource(R.string.home_history),
                                tint = InkWhite
                            )
                        }
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(CarbonSurface)
                                .testTag("home_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = stringResource(R.string.home_settings),
                                tint = InkWhite
                            )
                        }
                    }
                }

                // Center primary actions
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (!isFirebaseConfigured) {
                        Surface(
                            color = PausedAmberContainer,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, PausedAmber.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CloudOff,
                                    contentDescription = null,
                                    tint = PausedAmber
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.home_firebase_banner_title),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = PausedAmber
                                    )
                                    Text(
                                        text = stringResource(R.string.home_firebase_banner_body),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = InkWhite.copy(alpha = 0.85f)
                                    )
                                }
                                TextButton(
                                    onClick = { showFirebaseSetupDialog = true },
                                    modifier = Modifier.testTag("firebase_setup_guide_button")
                                ) {
                                    Text(
                                        text = stringResource(R.string.home_firebase_setup_button),
                                        color = PausedAmber
                                    )
                                }
                            }
                        }
                    }

                    if (actionState.errorMessage != null) {
                        Surface(
                            color = EndRedContainer,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, EndRed.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = actionState.errorMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = InkWhite,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = onClearError) {
                                    Text("OK", color = EndRed)
                                }
                            }
                        }
                    }

                    Button(
                        onClick = onCreateRoom,
                        enabled = !actionState.isBusy,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RunnerOrange,
                            contentColor = ObsidianBg
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(76.dp)
                            .testTag("create_room_button")
                    ) {
                        if (actionState.isBusy) {
                            CircularProgressIndicator(
                                color = ObsidianBg,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.home_create_room),
                                style = MaterialTheme.typography.headlineMedium
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            onClearError()
                            joinCodeInput = ""
                            showJoinDialog = true
                        },
                        enabled = !actionState.isBusy,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(2.dp, RunnerBlue),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = CarbonSurface,
                            contentColor = RunnerBlue
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(76.dp)
                            .testTag("join_room_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Login,
                            contentDescription = null,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.home_join_room),
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                }

                // Minimal footer cue
                Text(
                    text = "No accounts. Share a 6-character code and run live from anywhere.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
    }

    if (showJoinDialog) {
        val normalized = RoomCodeGenerator.normalize(joinCodeInput)
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            containerColor = CarbonElevated,
            titleContentColor = InkWhite,
            textContentColor = InkSecondary,
            title = { Text(stringResource(R.string.home_join_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.home_join_code_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSecondary
                    )
                    OutlinedTextField(
                        value = joinCodeInput,
                        onValueChange = { joinCodeInput = RoomCodeGenerator.normalize(it) },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.home_join_code_placeholder),
                                style = RoomCodeStyle,
                                color = InkSecondary.copy(alpha = 0.5f)
                            )
                        },
                        textStyle = RoomCodeStyle.copy(color = InkWhite),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CarbonSurface,
                            unfocusedContainerColor = CarbonSurface,
                            focusedBorderColor = RunnerBlue,
                            unfocusedBorderColor = CarbonOutline,
                            cursorColor = RunnerBlue
                        ),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                if (RoomCodeGenerator.isValid(normalized)) {
                                    showJoinDialog = false
                                    onJoinRoom(normalized)
                                }
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("join_code_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showJoinDialog = false
                        onJoinRoom(normalized)
                    },
                    enabled = RoomCodeGenerator.isValid(normalized),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RunnerBlue,
                        contentColor = ObsidianBg
                    ),
                    modifier = Modifier.testTag("join_confirm_button")
                ) {
                    Text(stringResource(R.string.home_join_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoinDialog = false }) {
                    Text(stringResource(R.string.home_cancel), color = InkSecondary)
                }
            }
        )
    }

    if (showFirebaseSetupDialog) {
        FirebaseSetupGuideDialog(onDismiss = { showFirebaseSetupDialog = false })
    }
}

@Composable
fun FirebaseSetupGuideDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CarbonElevated,
        titleContentColor = InkWhite,
        textContentColor = InkWhite,
        title = { Text("Connect Firebase Realtime Database") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Run Together uses Firebase Realtime Database on the free Spark plan (no login, no billing).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSecondary
                )
                Text(
                    text = "1. Open console.firebase.google.com and create a free Spark project.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "2. Go to Build → Realtime Database → Create Database.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "3. Project Settings → Add Android app with exact package name:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Surface(
                    color = ObsidianBg,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = BuildConfig.APPLICATION_ID,
                        fontFamily = JetBrainsMonoFamily,
                        color = RunnerOrange,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Text(
                    text = "4. Download google-services.json and replace app/google-services.json.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "5. Paste database.rules.json into Realtime Database → Rules and publish, then rebuild the APK.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = RunnerOrange,
                    contentColor = ObsidianBg
                )
            ) {
                Text("Got it")
            }
        }
    )
}
