package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.DistanceUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.userPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "run_together_prefs"
)

data class UserProfile(
    val runnerId: String,
    val displayName: String,
    val distanceUnit: DistanceUnit,
    val knownRoomCodes: Set<String>
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val RUNNER_ID = stringPreferencesKey("runner_id")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val DISTANCE_UNIT = stringPreferencesKey("distance_unit")
        val KNOWN_ROOM_CODES = stringSetPreferencesKey("known_room_codes")
    }

    val userProfile: Flow<UserProfile> = context.userPrefsDataStore.data.map { prefs ->
        val unitName = prefs[Keys.DISTANCE_UNIT]
        val unit = DistanceUnit.entries.firstOrNull { it.name == unitName }
            ?: DistanceUnit.KILOMETERS
        UserProfile(
            runnerId = prefs[Keys.RUNNER_ID].orEmpty(),
            displayName = prefs[Keys.DISPLAY_NAME].orEmpty(),
            distanceUnit = unit,
            knownRoomCodes = prefs[Keys.KNOWN_ROOM_CODES] ?: emptySet()
        )
    }

    suspend fun ensureRunnerId(): String {
        val existing = context.userPrefsDataStore.data.first()[Keys.RUNNER_ID]
        if (!existing.isNullOrBlank()) return existing
        val generated = "r_" + UUID.randomUUID().toString().replace("-", "").take(12)
        context.userPrefsDataStore.edit { prefs ->
            prefs[Keys.RUNNER_ID] = generated
        }
        return generated
    }

    suspend fun saveDisplayName(name: String) {
        val cleaned = name.trim().take(40)
        ensureRunnerId()
        context.userPrefsDataStore.edit { prefs ->
            prefs[Keys.DISPLAY_NAME] = cleaned
        }
    }

    suspend fun setDistanceUnit(unit: DistanceUnit) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[Keys.DISTANCE_UNIT] = unit.name
        }
    }

    suspend fun recordRoomCode(roomCode: String) {
        context.userPrefsDataStore.edit { prefs ->
            val current = prefs[Keys.KNOWN_ROOM_CODES] ?: emptySet()
            prefs[Keys.KNOWN_ROOM_CODES] = (current + roomCode).toList().takeLast(50).toSet()
        }
    }
}
