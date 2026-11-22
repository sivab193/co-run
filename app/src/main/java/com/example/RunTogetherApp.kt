package com.example

import android.app.Application
import com.example.data.FirebaseConfigHelper
import com.example.data.FirebaseRoomSyncRepository
import com.example.data.HistoryLocalStore
import com.example.data.LocalRoomSyncRepository
import com.example.data.RoomSyncRepository
import com.example.data.UserPreferencesRepository
import org.osmdroid.config.Configuration
import java.io.File

class RunTogetherApp : Application() {

    lateinit var preferencesRepository: UserPreferencesRepository
        private set

    lateinit var historyLocalStore: HistoryLocalStore
        private set

    lateinit var roomSyncRepository: RoomSyncRepository
        private set

    override fun onCreate() {
        super.onCreate()
        configureOsmdroid()
        preferencesRepository = UserPreferencesRepository(this)
        historyLocalStore = HistoryLocalStore(this)
        roomSyncRepository = if (FirebaseConfigHelper.isConfigured(this)) {
            FirebaseRoomSyncRepository(this, historyLocalStore)
        } else {
            LocalRoomSyncRepository(historyLocalStore)
        }
    }

    private fun configureOsmdroid() {
        val config = Configuration.getInstance()
        config.userAgentValue = packageName
        val osmBase = File(cacheDir, "osmdroid").apply { mkdirs() }
        val tileCache = File(osmBase, "tiles").apply { mkdirs() }
        config.osmdroidBasePath = osmBase
        config.osmdroidTileCache = tileCache
    }
}
