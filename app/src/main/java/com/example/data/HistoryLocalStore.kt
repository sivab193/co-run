package com.example.data

import android.content.Context
import com.example.model.RoutePoint
import com.example.model.RunHistoryEntry
import com.example.model.RunnerRunSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class HistoryLocalStore(private val context: Context) {

    private val historyFile: File
        get() = File(context.filesDir, "run_history.json")

    val cardsDir: File
        get() = File(context.filesDir, "run_cards").apply { mkdirs() }

    fun cardFileFor(runId: String): File = File(cardsDir, "run_$runId.png")

    suspend fun loadAll(): List<RunHistoryEntry> = withContext(Dispatchers.IO) {
        if (!historyFile.exists()) return@withContext emptyList()
        return@withContext try {
            val raw = historyFile.readText()
            val arr = JSONArray(raw)
            val list = ArrayList<RunHistoryEntry>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(parseEntry(obj))
            }
            list.sortedByDescending { it.createdAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun findById(runId: String): RunHistoryEntry? {
        return loadAll().firstOrNull { it.runId == runId }
    }

    suspend fun upsert(entry: RunHistoryEntry) = withContext(Dispatchers.IO) {
        val current = loadAll().associateBy { it.runId }.toMutableMap()
        val existing = current[entry.runId]
        val merged = entry.copy(
            localCardPath = entry.localCardPath ?: existing?.localCardPath,
            shareImageBase64 = entry.shareImageBase64 ?: existing?.shareImageBase64
        )
        current[entry.runId] = merged
        val sorted = current.values.sortedByDescending { it.createdAt }.take(100)
        val arr = JSONArray()
        for (item in sorted) {
            arr.put(serializeEntry(item))
        }
        historyFile.writeText(arr.toString())
    }

    private fun serializeEntry(entry: RunHistoryEntry): JSONObject {
        val obj = JSONObject()
        obj.put("runId", entry.runId)
        obj.put("roomCode", entry.roomCode)
        obj.put("createdAt", entry.createdAt)
        if (entry.localCardPath != null) obj.put("localCardPath", entry.localCardPath)
        if (entry.shareImageBase64 != null) obj.put("shareImageBase64", entry.shareImageBase64)
        val runnersArr = JSONArray()
        for (r in entry.runners) {
            val rObj = JSONObject()
            rObj.put("runnerId", r.runnerId)
            rObj.put("displayName", r.displayName)
            rObj.put("distanceMeters", r.distanceMeters)
            rObj.put("elapsedMs", r.elapsedMs)
            rObj.put("topSpeedMps", r.topSpeedMps)
            rObj.put("avgSpeedMps", r.avgSpeedMps)
            rObj.put("accentSlot", r.accentSlot)
            val ptsArr = JSONArray()
            for (pt in r.points) {
                val ptObj = JSONObject()
                ptObj.put("lat", pt.lat)
                ptObj.put("lng", pt.lng)
                ptObj.put("t", pt.timestampMs)
                ptsArr.put(ptObj)
            }
            rObj.put("points", ptsArr)
            runnersArr.put(rObj)
        }
        obj.put("runners", runnersArr)
        return obj
    }

    private fun parseEntry(obj: JSONObject): RunHistoryEntry {
        val runnersArr = obj.optJSONArray("runners") ?: JSONArray()
        val runners = ArrayList<RunnerRunSummary>(runnersArr.length())
        for (i in 0 until runnersArr.length()) {
            val rObj = runnersArr.getJSONObject(i)
            val ptsArr = rObj.optJSONArray("points") ?: JSONArray()
            val pts = ArrayList<RoutePoint>(ptsArr.length())
            for (j in 0 until ptsArr.length()) {
                val pObj = ptsArr.getJSONObject(j)
                pts.add(
                    RoutePoint(
                        lat = pObj.optDouble("lat", 0.0),
                        lng = pObj.optDouble("lng", 0.0),
                        timestampMs = pObj.optLong("t", 0L)
                    )
                )
            }
            runners.add(
                RunnerRunSummary(
                    runnerId = rObj.optString("runnerId"),
                    displayName = rObj.optString("displayName", "Runner"),
                    distanceMeters = rObj.optDouble("distanceMeters", 0.0),
                    elapsedMs = rObj.optLong("elapsedMs", 0L),
                    topSpeedMps = rObj.optDouble("topSpeedMps", 0.0),
                    avgSpeedMps = rObj.optDouble("avgSpeedMps", 0.0),
                    accentSlot = rObj.optInt("accentSlot", i),
                    points = pts
                )
            )
        }
        return RunHistoryEntry(
            runId = obj.optString("runId"),
            roomCode = obj.optString("roomCode"),
            createdAt = obj.optLong("createdAt", 0L),
            runners = runners,
            localCardPath = obj.optString("localCardPath").takeIf { it.isNotBlank() },
            shareImageBase64 = obj.optString("shareImageBase64").takeIf { it.isNotBlank() }
        )
    }
}
