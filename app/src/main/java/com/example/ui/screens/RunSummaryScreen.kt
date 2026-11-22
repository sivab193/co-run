package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.ui.theme.CarbonOutline
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.InkWhite
import com.example.ui.theme.LiveGreen
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RunnerBlue
import com.example.ui.theme.RunnerOrange
import com.example.ui.viewmodel.RunSummaryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunSummaryScreen(
    runId: String,
    autoSaveToGallery: Boolean,
    onDone: () -> Unit,
    summaryViewModel: RunSummaryViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by summaryViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(runId) {
        summaryViewModel.loadRun(runId, autoSaveToGallery = autoSaveToGallery)
    }

    LaunchedEffect(uiState.statusMessage) {
        val msg = uiState.statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            summaryViewModel.clearStatusMessage()
        }
    }

    Scaffold(
        containerColor = ObsidianBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.summary_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = InkWhite
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onDone,
                        modifier = Modifier.testTag("summary_back_button")
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
                    .widthIn(max = 560.dp)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val bitmap = uiState.cardBitmap
                    if (uiState.isRendering) {
                        Spacer(modifier = Modifier.height(64.dp))
                        CircularProgressIndicator(color = RunnerOrange)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.summary_generating),
                            style = MaterialTheme.typography.bodyLarge,
                            color = InkSecondary
                        )
                    } else if (bitmap != null) {
                        Surface(
                            color = CarbonSurface,
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, CarbonOutline),
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1080f / 1350f)
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Run Together dual-map share card",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(20.dp))
                                    .testTag("share_card_image")
                            )
                        }

                        if (uiState.savedToGallery) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = LiveGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = stringResource(R.string.summary_saved_gallery),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = LiveGreen
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Could not load run summary.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = InkSecondary
                        )
                    }
                }

                // Bottom share & save actions
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { summaryViewModel.shareCard(context) },
                        enabled = uiState.cardFile != null && !uiState.isRendering,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RunnerOrange,
                            contentColor = ObsidianBg
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("share_card_button")
                    ) {
                        Icon(imageVector = Icons.Rounded.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.summary_share),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { summaryViewModel.saveToGallery(context) },
                            enabled = uiState.cardBitmap != null && !uiState.isRendering,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.5.dp, RunnerBlue),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = CarbonSurface,
                                contentColor = RunnerBlue
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("save_gallery_button")
                        ) {
                            Icon(imageVector = Icons.Rounded.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.summary_save_gallery))
                        }

                        OutlinedButton(
                            onClick = onDone,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, CarbonOutline),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = CarbonSurface,
                                contentColor = InkWhite
                            ),
                            modifier = Modifier
                                .weight(0.75f)
                                .height(52.dp)
                                .testTag("summary_done_button")
                        ) {
                            Text(stringResource(R.string.summary_back_home))
                        }
                    }
                }
            }
        }
    }
}
