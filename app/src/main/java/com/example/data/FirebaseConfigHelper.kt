package com.example.data

import android.content.Context
import com.google.firebase.FirebaseApp

object FirebaseConfigHelper {
    private const val PLACEHOLDER_PROJECT_ID = "your-firebase-project-id"

    fun isConfigured(context: Context): Boolean {
        return try {
            val app = FirebaseApp.getApps(context).firstOrNull()
                ?: FirebaseApp.initializeApp(context)
                ?: return false
            val options = app.options
            val projectId = options.projectId.orEmpty()
            val dbUrl = options.databaseUrl.orEmpty()
            projectId.isNotBlank() &&
                !projectId.equals(PLACEHOLDER_PROJECT_ID, ignoreCase = true) &&
                dbUrl.startsWith("https://") &&
                !dbUrl.contains(PLACEHOLDER_PROJECT_ID, ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    fun getDatabaseUrl(context: Context): String? {
        return try {
            val app = FirebaseApp.getApps(context).firstOrNull()
                ?: FirebaseApp.initializeApp(context)
            app?.options?.databaseUrl?.takeIf {
                it.startsWith("https://") && !it.contains(PLACEHOLDER_PROJECT_ID, ignoreCase = true)
            }
        } catch (_: Exception) {
            null
        }
    }
}
