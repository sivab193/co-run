package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.RunTogetherApp
import com.example.model.DistanceUnit
import com.example.model.RunHistoryEntry
import com.example.share.ImageGallerySaver
import com.example.share.ShareCardGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class RunSummaryUiState(
    val entry: RunHistoryEntry? = null,
    val unit: DistanceUnit = DistanceUnit.KILOMETERS,
    val cardBitmap: Bitmap? = null,
    val cardFile: File? = null,
    val isRendering: Boolean = true,
    val savedToGallery: Boolean = false,
    val statusMessage: String? = null
)

class RunSummaryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as RunTogetherApp
    private val prefs = app.preferencesRepository
    private val localStore = app.historyLocalStore
    private val syncRepo = app.roomSyncRepository

    private val _uiState = MutableStateFlow(RunSummaryUiState())
    val uiState: StateFlow<RunSummaryUiState> = _uiState.asStateFlow()

    private var loadedRunId: String? = null

    fun loadRun(runId: String, autoSaveToGallery: Boolean) {
        if (loadedRunId == runId && _uiState.value.cardBitmap != null) return
        loadedRunId = runId

        viewModelScope.launch {
            _uiState.value = RunSummaryUiState(isRendering = true)
            val profile = prefs.userProfile.first()
            val unit = profile.distanceUnit

            var entry = localStore.findById(runId)
            if (entry == null) {
                val all = syncRepo.fetchHistoryForRooms(profile.knownRoomCodes)
                entry = all.firstOrNull { it.runId == runId }
            }

            if (entry == null) {
                _uiState.value = RunSummaryUiState(
                    isRendering = false,
                    statusMessage = "Run record not found."
                )
                return@launch
            }

            // Always render crisp full-res 1080x1350 dual-map card in the viewer's preferred unit
            val renderedBitmap = ShareCardGenerator.renderShareCard(entry, unit)
            val destFile = localStore.cardFileFor(entry.runId)
            ImageGallerySaver.saveInternalCard(renderedBitmap, destFile)

            // Persist compact preview + local path back into history
            val compactBase64 = withContext(Dispatchers.Default) {
                ShareCardGenerator.encodeCompactBase64(renderedBitmap)
            }
            val updatedEntry = entry.copy(
                localCardPath = destFile.absolutePath,
                shareImageBase64 = compactBase64
            )
            syncRepo.saveRunHistory(entry.roomCode, updatedEntry)

            var gallerySaved = false
            if (autoSaveToGallery) {
                gallerySaved = ImageGallerySaver.saveToGallery(app, renderedBitmap).isSuccess
            }

            _uiState.value = RunSummaryUiState(
                entry = updatedEntry,
                unit = unit,
                cardBitmap = renderedBitmap,
                cardFile = destFile,
                isRendering = false,
                savedToGallery = gallerySaved
            )
        }
    }

    fun saveToGallery(context: Context) {
        val bmp = _uiState.value.cardBitmap ?: return
        viewModelScope.launch {
            val result = ImageGallerySaver.saveToGallery(context.applicationContext, bmp)
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        savedToGallery = true,
                        statusMessage = "Saved to Pictures/Run Together"
                    )
                },
                onFailure = { err ->
                    _uiState.value = _uiState.value.copy(
                        statusMessage = err.message ?: "Could not save to gallery"
                    )
                }
            )
        }
    }

    fun shareCard(context: Context) {
        val state = _uiState.value
        val file = state.cardFile ?: return
        val names = state.entry?.runners?.joinToString(" & ") { it.displayName } ?: "Run Together"
        ImageGallerySaver.launchShareImageSheet(context, file, "$names — Run Together")
    }

    fun clearStatusMessage() {
        _uiState.value = _uiState.value.copy(statusMessage = null)
    }

    @Suppress("unused")
    private fun loadCachedBitmapIfAvailable(entry: RunHistoryEntry): Bitmap? {
        val path = entry.localCardPath
        if (!path.isNullOrBlank()) {
            val file = File(path)
            if (file.exists()) {
                return BitmapFactory.decodeFile(file.absolutePath)
            }
        }
        return entry.shareImageBase64?.let { ShareCardGenerator.decodeBase64Bitmap(it) }
    }
}
