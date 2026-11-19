package com.tuxundl.android.tor

import android.content.Context
import org.json.JSONArray

/**
 * Region choices for the Tor page. [severeNetwork] marks the
 * built-in list of places where the network is heavily filtered;
 * there the 智能 chain is not offered (it would burn its attempts
 * in a fixed order the filter can fingerprint) and the page
 * defaults to a manual transport instead. Data-driven: add or
 * remove entries here and the picker follows.
 */
enum class TorRegion(val label: String, val severeNetwork: Boolean) {
    MAINLAND("中国大陆", true),
    HONG_KONG("香港", false),
    TAIWAN("台湾", false),
    JAPAN("日本", false),
    USA("美国", false),
    EUROPE("欧洲", false),
    IRAN("伊朗", true),
    RUSSIA("俄罗斯", true),
    BELARUS("白俄罗斯", true),
    TURKMENISTAN("土库曼斯坦", true),
    NORTH_KOREA("朝鲜", true),
    OTHER("其他", false);

    companion object {
        /** Picker order: common choices first. */
        val ordered: List<TorRegion> = listOf(
            MAINLAND, HONG_KONG, TAIWAN, JAPAN, USA, EUROPE,
            IRAN, RUSSIA, BELARUS, TURKMENISTAN, NORTH_KOREA, OTHER
        )
    }
}

/**
 * Small on-device store for the Tor page: the chosen region and
 * the user's custom bridge lines. Everything lives in the app's
 * private SharedPreferences — bridge lines in particular are
 * sensitive (a published bridge gets blocked fast), so they are
 * kept on this device only and never leave it.
 */
object TorLocalStore {

    private const val PREFS = "tor_local_store"
    private const val KEY_REGION = "region"
    private const val KEY_BRIDGES = "bridges"
    private const val MAX_BRIDGES = 50

    fun loadRegion(context: Context): TorRegion? {
        val name = prefs(context).getString(KEY_REGION, null) ?: return null
        return TorRegion.entries.firstOrNull { it.name == name }
    }

    fun saveRegion(context: Context, region: TorRegion) {
        prefs(context).edit().putString(KEY_REGION, region.name).apply()
    }

    fun loadBridges(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_BRIDGES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getString(it) }
                .filter { it.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Remember every bridge line in [text]; returns the new list. */
    fun addBridges(context: Context, text: String): List<String> {
        val incoming = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (incoming.isEmpty()) return loadBridges(context)
        val merged = (loadBridges(context) + incoming).distinct().takeLast(MAX_BRIDGES)
        saveBridges(context, merged)
        return merged
    }

    fun removeBridge(context: Context, line: String): List<String> {
        val kept = loadBridges(context).filter { it != line }
        saveBridges(context, kept)
        return kept
    }

    private fun saveBridges(context: Context, bridges: List<String>) {
        val array = JSONArray()
        bridges.forEach { array.put(it) }
        prefs(context).edit().putString(KEY_BRIDGES, array.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
