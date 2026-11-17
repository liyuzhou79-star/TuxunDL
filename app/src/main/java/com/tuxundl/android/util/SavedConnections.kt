package com.tuxundl.android.util

import com.tuxundl.android.common.log.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * A user-saved snapshot of one connection session ("收藏本次連接"),
 * taken from the advanced traffic panel: when it was saved, which
 * profile/node served it, how long it ran, how much it carried and how
 * good the measured quality was at that moment.
 */
data class SavedConnection(
    val id: String,
    val savedAtMillis: Long,
    val profileName: String,
    val nodeName: String,
    val durationMillis: Long,
    val upBytes: Long,
    val downBytes: Long,
    val avgDelayMs: Int,
    val jitterMs: Int,
    /** 0..1 */
    val failRate: Float,
    /** [QualityRating] name, stored as text so old files stay readable. */
    val rating: String,
) {
    fun ratingValue(): QualityRating = try {
        QualityRating.valueOf(rating)
    } catch (e: Exception) {
        QualityRating.NoData
    }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}

/**
 * The saved-connections list, in `filesDir/saved-connections.json` as a
 * JSON array, newest first. Small and user-managed (entries are only
 * added by an explicit tap and removed one by one), so the whole file
 * is rewritten on every change.
 */
class SavedConnectionStore(private val file: File) {
    private val items = mutableListOf<SavedConnection>()

    fun load(): List<SavedConnection> {
        items.clear()
        if (!file.exists()) return emptyList()

        try {
            val array = JSONArray(file.readText())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                items += SavedConnection(
                    id = obj.optString("id", SavedConnection.newId()),
                    savedAtMillis = obj.optLong("savedAt", 0L),
                    profileName = obj.optString("profile", ""),
                    nodeName = obj.optString("node", ""),
                    durationMillis = obj.optLong("duration", 0L),
                    upBytes = obj.optLong("up", 0L),
                    downBytes = obj.optLong("down", 0L),
                    avgDelayMs = obj.optInt("avgDelay", 0),
                    jitterMs = obj.optInt("jitter", 0),
                    failRate = obj.optDouble("failRate", 0.0).toFloat(),
                    rating = obj.optString("rating", QualityRating.NoData.name),
                )
            }
        } catch (e: Exception) {
            Log.w("Read saved connections failed: $e", e)
            items.clear()
        }

        return snapshot()
    }

    fun snapshot(): List<SavedConnection> = items.toList()

    fun add(connection: SavedConnection) {
        items.add(0, connection)
        save()
    }

    fun remove(id: String) {
        if (items.removeAll { it.id == id }) {
            save()
        }
    }

    private fun save() {
        try {
            val array = JSONArray()
            for (item in items) {
                array.put(
                    JSONObject()
                        .put("id", item.id)
                        .put("savedAt", item.savedAtMillis)
                        .put("profile", item.profileName)
                        .put("node", item.nodeName)
                        .put("duration", item.durationMillis)
                        .put("up", item.upBytes)
                        .put("down", item.downBytes)
                        .put("avgDelay", item.avgDelayMs)
                        .put("jitter", item.jitterMs)
                        .put("failRate", item.failRate.toDouble())
                        .put("rating", item.rating),
                )
            }

            file.parentFile?.mkdirs()
            file.writeText(array.toString())
        } catch (e: Exception) {
            Log.w("Write saved connections failed: $e", e)
        }
    }
}
