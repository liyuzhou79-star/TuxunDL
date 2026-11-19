package com.tuxundl.android.tortest

import android.content.Context
import androidx.compose.ui.graphics.Path
import org.json.JSONArray
import org.json.JSONObject

/**
 * Simplified world-country outlines for the Tor page's map disc.
 *
 * Data: assets/world-countries.json — Natural Earth 110m-level
 * country polygons (via the public world.geo.json dataset),
 * coordinates rounded to 2 decimals, Antarctica dropped; ~160 KB.
 * Parsed once into Compose [Path]s in normalized equirectangular
 * space (x = (lon + 180) / 360, y = (90 − lat) / 180, both 0..1)
 * and cached for the process lifetime; the Canvas only scales and
 * strokes them, never recomputes geometry per frame.
 */
object WorldMapData {

    @Volatile
    private var cached: List<Path>? = null

    fun load(context: Context): List<Path> {
        cached?.let { return it }
        val paths = mutableListOf<Path>()
        try {
            val text = context.assets.open("world-countries.json")
                .bufferedReader().use { it.readText() }
            val features = JSONObject(text).getJSONArray("features")
            for (i in 0 until features.length()) {
                val geometry = features.getJSONObject(i).getJSONObject("geometry")
                val type = geometry.getString("type")
                val coordinates = geometry.getJSONArray("coordinates")
                val path = Path()
                when (type) {
                    "Polygon" -> addPolygon(path, coordinates)
                    "MultiPolygon" -> {
                        for (j in 0 until coordinates.length()) {
                            addPolygon(path, coordinates.getJSONArray(j))
                        }
                    }
                }
                paths += path
            }
        } catch (_: Exception) {
            // A map that fails to parse degrades to the empty disc;
            // the page and Tor itself keep working.
        }
        return paths.also { cached = it }
    }

    private fun addPolygon(path: Path, rings: JSONArray) {
        for (r in 0 until rings.length()) {
            val ring = rings.getJSONArray(r)
            for (p in 0 until ring.length()) {
                val point = ring.getJSONArray(p)
                val x = ((point.getDouble(0) + 180.0) / 360.0).toFloat()
                val y = ((90.0 - point.getDouble(1)) / 180.0).toFloat()
                if (p == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
            path.close()
        }
    }
}
