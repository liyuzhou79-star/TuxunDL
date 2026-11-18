package com.tuxundl.windows

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * Accent color shared by every page. Pages read it through their
 * local `Indigo` property (a getter onto [accent]), so picking a new
 * color recolors the whole app at once. The choice is stored in the
 * settings file and survives restarts.
 */
object ThemeState {

    /** Preset accent colors: display name to color. */
    val presets: List<Pair<String, Color>> = listOf(
        "靛蓝" to Color(0xFF5B5BD6),
        "天蓝" to Color(0xFF2E9BE6),
        "紫色" to Color(0xFF9B59D0),
        "绿色" to Color(0xFF3FA46A),
        "橙色" to Color(0xFFE67E22),
        "粉色" to Color(0xFFE5639A),
    )

    var accent: Color by mutableStateOf(loadInitial())
        private set

    /** A lighter tint of the accent, for status text and soft highlights. */
    val accentSoft: Color
        get() = accent.let { c ->
            Color(
                red = c.red + (1f - c.red) * 0.35f,
                green = c.green + (1f - c.green) * 0.35f,
                blue = c.blue + (1f - c.blue) * 0.35f,
            )
        }

    private fun loadInitial(): Color {
        val saved = SettingsStore.themeColorHex()
        return presets.firstOrNull { hexOf(it.second) == saved }?.second
            ?: presets[0].second
    }

    /** Switch the accent color and persist the choice. */
    fun select(color: Color) {
        accent = color
        SettingsStore.setThemeColorHex(hexOf(color))
    }

    /**
     * Back to the default preset, in memory only — used by the
     * settings page's "恢复默认设置", which removes the stored key
     * itself, so there is nothing to persist here.
     */
    fun resetAccent() {
        accent = presets[0].second
    }

    private fun hexOf(color: Color): String =
        "%08X".format(color.toArgb())
}
