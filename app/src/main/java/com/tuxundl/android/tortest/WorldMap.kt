package com.tuxundl.android.tortest

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Simplified world-country outlines for the Tor page's globe.
 *
 * Data: assets/world-countries.json — Natural Earth 110m-level
 * country polygons (via the public world.geo.json dataset),
 * coordinates rounded to 2 decimals, Antarctica dropped; ~160 KB,
 * 179 countries / 285 rings / ~10k points.
 *
 * Parsed once into unit-sphere 3D coordinates and cached for the
 * process lifetime: each ring keeps its points as (x, y, z) triplets
 * plus a centroid and a culling threshold, so the globe renderer can
 * skip back-facing rings with one dot product and never recompute
 * geography per frame — it only rotates and projects.
 *
 * Sphere convention: lon 0° / lat 0° → (0, 0, 1), i.e. +z faces the
 * viewer at zero rotation; x = cos(lat)·sin(lon), y = sin(lat),
 * z = cos(lat)·cos(lon).
 */
class GlobeRing(
    /** Flattened (x, y, z) unit-sphere points, in ring order. */
    val coords: FloatArray,
    val cx: Float,
    val cy: Float,
    val cz: Float,
    /**
     * A ring can only be visible when its rotated centroid z is
     * above this (negative) threshold: −sin(angular radius).
     */
    val cullZ: Float
)

object WorldGlobeData {

    @Volatile
    private var cached: List<GlobeRing>? = null

    @Volatile
    private var cachedRegional: List<GlobeRing>? = null

    /** Base-dataset feature names the regional variant replaces. */
    private val regionalNames = setOf("China", "Taiwan")

    fun load(context: Context, regionalVariant: Boolean = false): List<GlobeRing> {
        if (regionalVariant) {
            cachedRegional?.let { return it }
        } else {
            cached?.let { return it }
        }
        val rings = mutableListOf<GlobeRing>()
        try {
            parseAsset(context, "world-countries.json", rings, regionalVariant)
            if (regionalVariant) {
                parseAsset(context, "world-countries-cn.json", rings, false)
            }
        } catch (_: Exception) {
            // A globe that fails to parse degrades to the bare
            // sphere; the page and Tor itself keep working.
        }
        return rings.also {
            if (regionalVariant) cachedRegional = it else cached = it
        }
    }

    private fun parseAsset(
        context: Context,
        asset: String,
        rings: MutableList<GlobeRing>,
        skipRegional: Boolean
    ) {
        val text = context.assets.open(asset)
            .bufferedReader().use { it.readText() }
        val features = JSONObject(text).getJSONArray("features")
        for (i in 0 until features.length()) {
            val feature = features.getJSONObject(i)
            if (skipRegional) {
                val name = feature.optJSONObject("properties")
                    ?.optString("name")
                if (name in regionalNames) continue
            }
            val geometry = feature.getJSONObject("geometry")
            val coordinates = geometry.getJSONArray("coordinates")
            when (geometry.getString("type")) {
                "Polygon" -> addPolygon(rings, coordinates)
                "MultiPolygon" -> {
                    for (j in 0 until coordinates.length()) {
                        addPolygon(rings, coordinates.getJSONArray(j))
                    }
                }
            }
        }
    }

    private fun addPolygon(out: MutableList<GlobeRing>, rings: JSONArray) {
        for (r in 0 until rings.length()) {
            val ring = rings.getJSONArray(r)
            val count = ring.length()
            if (count < 3) continue
            val coords = FloatArray(count * 3)
            var sx = 0.0
            var sy = 0.0
            var sz = 0.0
            for (p in 0 until count) {
                val point = ring.getJSONArray(p)
                val lon = Math.toRadians(point.getDouble(0))
                val lat = Math.toRadians(point.getDouble(1))
                val cosLat = cos(lat)
                val x = (cosLat * sin(lon)).toFloat()
                val y = sin(lat).toFloat()
                val z = (cosLat * cos(lon)).toFloat()
                coords[p * 3] = x
                coords[p * 3 + 1] = y
                coords[p * 3 + 2] = z
                sx += x
                sy += y
                sz += z
            }
            val length = sqrt(sx * sx + sy * sy + sz * sz)
            if (length < 1e-6) continue
            val cx = (sx / length).toFloat()
            val cy = (sy / length).toFloat()
            val cz = (sz / length).toFloat()
            // Angular radius: the smallest dot with the centroid over
            // all points gives cos(radius); cull threshold −sin(radius).
            var minDot = 1.0f
            for (p in 0 until count) {
                val dot = coords[p * 3] * cx + coords[p * 3 + 1] * cy + coords[p * 3 + 2] * cz
                if (dot < minDot) minDot = dot
            }
            val cullZ = -sqrt((1.0f - minDot * minDot).coerceIn(0f, 1f))
            out += GlobeRing(coords, cx, cy, cz, cullZ)
        }
    }
}
