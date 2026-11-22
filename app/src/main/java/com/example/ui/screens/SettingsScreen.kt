package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.DistanceUnit
import com.example.ui.theme.CarbonOutline
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.InkWhite
import com.example.ui.theme.LiveGreen
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PausedAmber
import com.example.ui.theme.RunnerOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentName: String,
    currentUnit: DistanceUnit,
    isFirebaseConfigured: Boolean,
    onSaveName: (String) -> Unit,
    onSelectUnit: (DistanceUnit) -> Unit,
    onNavigateBack: () -> Unit
) {
    var nameInput by rememberSaveable(currentName) { mutableStateOf(currentName) }
    var nameSavedFlash by rememberSaveable { mutableStateOf(false) }
    var showSetupDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = ObsidianBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = InkWhite
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = InkWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianBg)
            )
        }
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Display Name section
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.settings_display_name),
                        style = MaterialTheme.typography.labelLarge,
                        color = InkSecondary
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = {
                                if (it.length <= 40) {
                                    nameInput = it
                                    nameSavedFlash = false
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = CarbonSurface,
                                unfocusedContainerColor = CarbonSurface,
                                focusedBorderColor = RunnerOrange,
                                unfocusedBorderColor = CarbonOutline,
                                focusedTextColor = InkWhite,
                                unfocusedTextColor = InkWhite,
                                cursorColor = RunnerOrange
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_name_input")
                        )
                        Button(
                            onClick = {
                                val cleaned = nameInput.trim()
                                if (cleaned.isNotEmpty()) {
                                    onSaveName(cleaned)
                                    nameSavedFlash = true
                                }
                            },
                            enabled = nameInput.trim().isNotEmpty(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RunnerOrange,
                                contentColor = ObsidianBg
                            ),
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("settings_save_name_button")
                        ) {
                            Text(stringResource(R.string.settings_save))
                        }
                    }
                    if (nameSavedFlash) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = LiveGreen
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Name updated",
                                style = MaterialTheme.typography.bodyMedium,
                                color = LiveGreen
                            )
                        }
                    }
                }

                // Units toggle section (per-phone)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.settings_units),
                        style = MaterialTheme.typography.labelLarge,
                        color = InkSecondary
                    )
                    Surface(
                        color = CarbonSurface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, CarbonOutline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            UnitOptionRow(
                                label = stringResource(R.string.settings_units_km),
                                selected = currentUnit == DistanceUnit.KILOMETERS,
                                onClick = { onSelectUnit(DistanceUnit.KILOMETERS) },
                                tag = "unit_km_option"
                            )
                            UnitOptionRow(
                                label = stringResource(R.string.settings_units_mi),
                                selected = currentUnit == DistanceUnit.MILES,
                                onClick = { onSelectUnit(DistanceUnit.MILES) },
                                tag = "unit_mi_option"
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.settings_units_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSecondary
                    )
                }

                // Firebase RTDB Status section
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.settings_firebase_status),
                        style = MaterialTheme.typography.labelLarge,
                        color = InkSecondary
                    )
                    Surface(
                        color = CarbonSurface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, CarbonOutline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = if (isFirebaseConfigured) {
                                    Icons.Rounded.CloudDone
                                } else {
                                    Icons.Rounded.CloudOff
                                },
                                contentDescription = null,
                                tint = if (isFirebaseConfigured) LiveGreen else PausedAmber
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isFirebaseConfigured) "Connected (Spark RTDB)" else "Not configured (Local mode)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = InkWhite
                                )
                                Text(
                                    text = if (isFirebaseConfigured) {
                                        "Live two-phone sync is active."
                                    } else {
                                        "Replace app/google-services.json with your Firebase project config."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = InkSecondary
                                )
                            }
                            TextButton(onClick = { showSetupDialog = true }) {
                                Text("Guide", color = RunnerOrange)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSetupDialog) {
        FirebaseSetupGuideDialog(onDismiss = { showSetupDialog = false })
    }
}

@Composable
private fun UnitOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = RunnerOrange,
                unselectedColor = InkSecondary
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = InkWhite
        )
    }
}
