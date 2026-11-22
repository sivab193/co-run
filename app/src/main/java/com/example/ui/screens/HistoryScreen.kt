package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.DistanceUnit
import com.example.model.RunHistoryEntry
import com.example.model.UnitFormatter
import com.example.ui.theme.CarbonOutline
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.InkWhite
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RunnerBlue
import com.example.ui.theme.RunnerOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    entries: List<RunHistoryEntry>,
    unit: DistanceUnit,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onSelectRun: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    LaunchedEffect(Unit) {
        onRefresh()
    }

    Scaffold(
        containerColor = ObsidianBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.history_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = InkWhite
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("history_back_button")
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
            when {
                isLoading && entries.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = RunnerOrange)
                    }
                }
                entries.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DirectionsRun,
                            contentDescription = null,
                            tint = InkSecondary,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.history_empty_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = InkWhite
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.history_empty_body),
                            style = MaterialTheme.typography.bodyLarge,
                            color = InkSecondary
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 600.dp),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(entries, key = { it.runId }) { entry ->
                            HistoryItemCard(
                                entry = entry,
                                unit = unit,
                                onClick = { onSelectRun(entry.runId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryItemCard(
    entry: RunHistoryEntry,
    unit: DistanceUnit,
    onClick: () -> Unit
) {
    val unitLabel = UnitFormatter.distanceUnitLabel(unit)
    Surface(
        color = CarbonSurface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, CarbonOutline),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("history_item_${entry.runId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = UnitFormatter.dateFormatted(entry.createdAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = InkSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))

                entry.runners.forEachIndexed { idx, runner ->
                    val accent = if (idx == 0) RunnerOrange else RunnerBlue
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(accent)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = runner.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                color = InkWhite
                            )
                        }
                        Text(
                            text = "${UnitFormatter.distanceValue(runner.distanceMeters, unit)} $unitLabel",
                            style = MaterialTheme.typography.titleMedium,
                            color = accent
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = "Open share card",
                tint = InkSecondary
            )
        }
    }
}
