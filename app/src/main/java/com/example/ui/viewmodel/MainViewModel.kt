package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.RunTogetherApp
import com.example.data.UserProfile
import com.example.model.DistanceUnit
import com.example.model.RoomCodeGenerator
import com.example.model.RunHistoryEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeActionState(
    val isBusy: Boolean = false,
    val errorMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as RunTogetherApp
    private val prefs = app.preferencesRepository
    private val syncRepo = app.roomSyncRepository

    val isFirebaseConfigured: Boolean = syncRepo.isFirebaseConfigured

    val userProfile: StateFlow<UserProfile?> = prefs.userProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _homeState = MutableStateFlow(HomeActionState())
    val homeState: StateFlow<HomeActionState> = _homeState.asStateFlow()

    private val _history = MutableStateFlow<List<RunHistoryEntry>>(emptyList())
    val history: StateFlow<List<RunHistoryEntry>> = _history.asStateFlow()

    private val _isLoadingHistory = MutableStateFlow(false)
    val isLoadingHistory: StateFlow<Boolean> = _isLoadingHistory.asStateFlow()

    private val _pendingDeepLinkCode = MutableStateFlow<String?>(null)
    val pendingDeepLinkCode: StateFlow<String?> = _pendingDeepLinkCode.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.ensureRunnerId()
        }
    }

    fun onDeepLinkRoomCode(code: String) {
        val normalized = RoomCodeGenerator.normalize(code)
        if (RoomCodeGenerator.isValid(normalized)) {
            _pendingDeepLinkCode.value = normalized
        }
    }

    fun clearPendingDeepLink() {
        _pendingDeepLinkCode.value = null
    }

    fun saveDisplayName(name: String, onSaved: () -> Unit = {}) {
        val cleaned = name.trim()
        if (cleaned.isEmpty()) return
        viewModelScope.launch {
            prefs.saveDisplayName(cleaned)
            onSaved()
        }
    }

    fun setDistanceUnit(unit: DistanceUnit) {
        viewModelScope.launch {
            prefs.setDistanceUnit(unit)
        }
    }

    fun clearHomeError() {
        _homeState.value = _homeState.value.copy(errorMessage = null)
    }

    fun createRoom(onCreated: (String) -> Unit) {
        if (_homeState.value.isBusy) return
        viewModelScope.launch {
            _homeState.value = HomeActionState(isBusy = true, errorMessage = null)
            val runnerId = prefs.ensureRunnerId()
            val profile = prefs.userProfile.first()
            val name = profile.displayName.ifBlank { "Runner" }
            val roomCode = RoomCodeGenerator.generate()

            val result = syncRepo.createRoom(roomCode, runnerId, name)
            result.fold(
                onSuccess = {
                    prefs.recordRoomCode(roomCode)
                    _homeState.value = HomeActionState(isBusy = false)
                    onCreated(roomCode)
                },
                onFailure = { err ->
                    _homeState.value = HomeActionState(
                        isBusy = false,
                        errorMessage = err.message ?: "Failed to create room"
                    )
                }
            )
        }
    }

    fun joinRoom(rawCode: String, onJoined: (String) -> Unit) {
        val code = RoomCodeGenerator.normalize(rawCode)
        if (!RoomCodeGenerator.isValid(code)) {
            _homeState.value = HomeActionState(
                isBusy = false,
                errorMessage = "Enter a valid 6-character room code."
            )
            return
        }
        if (_homeState.value.isBusy) return
        viewModelScope.launch {
            _homeState.value = HomeActionState(isBusy = true, errorMessage = null)
            val runnerId = prefs.ensureRunnerId()
            val profile = prefs.userProfile.first()
            val name = profile.displayName.ifBlank { "Runner" }

            val result = syncRepo.joinRoom(code, runnerId, name)
            result.fold(
                onSuccess = {
                    prefs.recordRoomCode(code)
                    _homeState.value = HomeActionState(isBusy = false)
                    onJoined(code)
                },
                onFailure = { err ->
                    _homeState.value = HomeActionState(
                        isBusy = false,
                        errorMessage = err.message ?: "Could not join room $code"
                    )
                }
            )
        }
    }

    fun refreshHistory() {
        viewModelScope.launch {
            _isLoadingHistory.value = true
            val knownCodes = prefs.userProfile.first().knownRoomCodes
            val loaded = syncRepo.fetchHistoryForRooms(knownCodes)
            _history.value = loaded
            _isLoadingHistory.value = false
        }
    }
}
