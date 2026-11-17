package com.tuxundl.android.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tuxundl.android.R
import com.tuxundl.android.design.model.AnimationStyle
import com.tuxundl.android.design.model.DarkMode
import com.tuxundl.android.design.model.ThemePalette
import com.tuxundl.android.design.store.UiStore
import com.tuxundl.android.ui.theme.darkSchemeOf
import com.tuxundl.android.ui.theme.hslToColor
import com.tuxundl.android.ui.theme.lightSchemeOf
import com.tuxundl.android.ui.theme.seedHsl
import com.tuxundl.android.design.R as DesignR

/**
 * Appearance settings: palette picker (with a colour-dot preview of each
 * palette's primary), dark mode, and home-screen card visibility switches.
 *
 * Palette and dark mode live in the same UiStore fields the old settings
 * pages used, so this screen and AppSettings never disagree.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    uiStore: UiStore,
    darkTheme: Boolean,
    onPaletteChange: (ThemePalette) -> Unit,
    onDarkModeChange: (DarkMode) -> Unit,
    onCustomColorChange: (Int) -> Unit,
    onAnimStyleChange: (AnimationStyle) -> Unit,
    onBack: () -> Unit,
) {
    var palette by remember { mutableStateOf(uiStore.themePalette) }
    var darkMode by remember { mutableStateOf(uiStore.darkMode) }
    var animStyle by remember { mutableStateOf(uiStore.animationStyle) }
    var customSeed by remember { mutableStateOf(uiStore.customThemeColor) }
    var showTraffic by remember { mutableStateOf(uiStore.showTrafficCard) }
    var showProxy by remember { mutableStateOf(uiStore.showProxyCard) }
    var showProfile by remember { mutableStateOf(uiStore.showProfileCard) }
    var showProviders by remember { mutableStateOf(uiStore.showProvidersCard) }
    var showMonitor by remember { mutableStateOf(uiStore.showTrafficMonitorCard) }
    var cardOrder by remember { mutableStateOf(parseHomeCardOrder(uiStore.homeCardOrder)) }

    fun checkedOf(key: String): Boolean = when (key) {
        "monitor" -> showMonitor
        "traffic" -> showTraffic
        "proxy" -> showProxy
        "profile" -> showProfile
        else -> showProviders
    }

    fun setChecked(key: String, value: Boolean) {
        when (key) {
            "monitor" -> {
                showMonitor = value
                uiStore.showTrafficMonitorCard = value
            }
            "traffic" -> {
                showTraffic = value
                uiStore.showTrafficCard = value
            }
            "proxy" -> {
                showProxy = value
                uiStore.showProxyCard = value
            }
            "profile" -> {
                showProfile = value
                uiStore.showProfileCard = value
            }
            else -> {
                showProviders = value
                uiStore.showProvidersCard = value
            }
        }
    }

    fun moveCard(from: Int, to: Int) {
        if (to !in cardOrder.indices) return

        cardOrder = cardOrder.toMutableList().apply {
            add(to, removeAt(from))
        }
        uiStore.homeCardOrder = cardOrder.joinToString(",")
    }

    /** Pick a preset palette: clears any custom colour (mutually exclusive). */
    fun selectPalette(value: ThemePalette) {
        palette = value
        customSeed = 0
        uiStore.customThemeColor = 0
        onPaletteChange(value)
    }

    /** Pick a custom seed colour: the theme switches to it at once. */
    fun selectCustomColor(argb: Int) {
        customSeed = argb
        uiStore.customThemeColor = argb
        onCustomColorChange(argb)
    }

    Scaffold(
        topBar = {
            SubTopBar(
                title = stringResource(R.string.appearance),
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
                .tuxunPageEnter(),
        ) {
            SectionCard(title = stringResource(R.string.theme_palette)) {
                PaletteRow(
                    name = stringResource(R.string.theme_default),
                    palette = ThemePalette.Default,
                    darkTheme = darkTheme,
                    selected = palette == ThemePalette.Default && customSeed == 0,
                    onClick = { selectPalette(ThemePalette.Default) },
                )
                PaletteRow(
                    name = stringResource(R.string.theme_blue),
                    palette = ThemePalette.Blue,
                    darkTheme = darkTheme,
                    selected = palette == ThemePalette.Blue && customSeed == 0,
                    onClick = { selectPalette(ThemePalette.Blue) },
                )
                PaletteRow(
                    name = stringResource(R.string.theme_sakura),
                    palette = ThemePalette.Sakura,
                    darkTheme = darkTheme,
                    selected = palette == ThemePalette.Sakura && customSeed == 0,
                    onClick = { selectPalette(ThemePalette.Sakura) },
                )
                PaletteRow(
                    name = stringResource(R.string.theme_mono),
                    palette = ThemePalette.Mono,
                    darkTheme = darkTheme,
                    selected = palette == ThemePalette.Mono && customSeed == 0,
                    onClick = { selectPalette(ThemePalette.Mono) },
                )
            }

            SectionCard(title = stringResource(R.string.theme_custom)) {
                CustomColorRow(
                    seedArgb = customSeed,
                    selected = customSeed != 0,
                    onClick = {
                        if (customSeed == 0) {
                            // First pick: start from the current palette's primary.
                            val start = if (darkTheme) darkSchemeOf(palette).primary
                            else lightSchemeOf(palette).primary
                            selectCustomColor(
                                (0xFF shl 24) or
                                    (start.red * 255).toInt().shl(16) or
                                    (start.green * 255).toInt().shl(8) or
                                    (start.blue * 255).toInt(),
                            )
                        }
                    },
                )
                if (customSeed != 0) {
                    ColorPicker(
                        seedArgb = customSeed,
                        onSeedChange = { argb ->
                            // Live preview only; the theme applies on release.
                            customSeed = argb
                            uiStore.customThemeColor = argb
                        },
                        onSeedChangeFinished = { selectCustomColor(customSeed) },
                    )
                }
            }

            SectionCard(title = stringResource(DesignR.string.dark_mode)) {
                SelectRow(
                    title = stringResource(DesignR.string.dark_mode),
                    options = listOf(
                        stringResource(DesignR.string.follow_system_android_10),
                        stringResource(DesignR.string.always_light),
                        stringResource(DesignR.string.always_dark),
                    ),
                    selectedIndex = DarkMode.values().indexOf(darkMode),
                    onSelect = { index ->
                        val mode = DarkMode.values()[index]
                        darkMode = mode
                        onDarkModeChange(mode)
                    },
                )
            }

            SectionCard(title = stringResource(R.string.animation_style)) {
                SelectRow(
                    title = stringResource(R.string.animation_style),
                    options = listOf(
                        stringResource(R.string.anim_style_spring),
                        stringResource(R.string.anim_style_soft),
                        stringResource(R.string.anim_style_reduced),
                    ),
                    selectedIndex = AnimationStyle.values().indexOf(animStyle),
                    onSelect = { index ->
                        val style = AnimationStyle.values()[index]
                        animStyle = style
                        onAnimStyleChange(style)
                    },
                )
            }

            SectionCard(title = stringResource(R.string.home_cards)) {
                cardOrder.forEachIndexed { index, key ->
                    CardOrderRow(
                        title = stringResource(
                            when (key) {
                                "monitor" -> R.string.card_traffic_monitor
                                "traffic" -> R.string.card_traffic
                                "proxy" -> R.string.card_proxy
                                "profile" -> R.string.card_profile
                                else -> R.string.card_providers
                            },
                        ),
                        checked = checkedOf(key),
                        onCheckedChange = { setChecked(key, it) },
                        canMoveUp = index > 0,
                        canMoveDown = index < cardOrder.lastIndex,
                        onMoveUp = { moveCard(index, index - 1) },
                        onMoveDown = { moveCard(index, index + 1) },
                    )
                }
            }
        }
    }
}

/** All orderable home cards, in their default display order. */
private val HOME_CARD_KEYS =
    listOf("monitor", "traffic", "proxy", "profile", "providers")

private fun parseHomeCardOrder(stored: String): List<String> {
    val parsed = stored.split(",")
        .map { it.trim() }
        .filter { it in HOME_CARD_KEYS }
        .distinct()

    return parsed + HOME_CARD_KEYS.filter { it !in parsed }
}

/** One home-card row in Appearance: visibility switch plus up/down order buttons. */
@Composable
private fun CardOrderRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(
                painter = painterResource(DesignR.drawable.ic_baseline_arrow_upward),
                contentDescription = stringResource(R.string.move_up),
            )
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(
                painter = painterResource(DesignR.drawable.ic_baseline_arrow_downward),
                contentDescription = stringResource(R.string.move_down),
            )
        }
        TuxunSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun PaletteRow(
    name: String,
    palette: ThemePalette,
    darkTheme: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = if (darkTheme) darkSchemeOf(palette) else lightSchemeOf(palette)
    // The preview dot cross-fades when the palette (or dark mode) flips.
    val dotColor by animateColorAsState(
        targetValue = scheme.primary,
        animationSpec = tuxunColorSpec(LocalAnimStyle.current),
        label = "paletteDot",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(dotColor)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape,
                ),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                painter = painterResource(DesignR.drawable.ic_outline_check_circle),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}

/** The "custom colour" row: a preview dot (the seed) plus a radio. */
@Composable
private fun CustomColorRow(
    seedArgb: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // The preview dot follows the seed live while the sliders move.
    val dotColor by animateColorAsState(
        targetValue = if (seedArgb != 0) Color(seedArgb)
        else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tuxunColorSpec(LocalAnimStyle.current),
        label = "customColorDot",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(dotColor)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape,
                ),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = stringResource(R.string.theme_custom),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                painter = painterResource(DesignR.drawable.ic_outline_check_circle),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}

/** Sixteen curated seeds for one-tap picking. */
// Full ARGB ints (the hex literals exceed Int.MAX_VALUE, so .toInt() pins
// the bit pattern; Color(Int) then reads them as ARGB).
private val CUSTOM_SEED_SWATCHES = listOf(
    0xFFE53935.toInt(), 0xFFD81B60.toInt(), 0xFF8E24AA.toInt(),
    0xFF5E35B1.toInt(), 0xFF3949AB.toInt(), 0xFF1E88E5.toInt(),
    0xFF039BE5.toInt(), 0xFF00897B.toInt(), 0xFF43A047.toInt(),
    0xFF7CB342.toInt(), 0xFFFB8C00.toInt(), 0xFF6D4C41.toInt(),
    0xFF26A69A.toInt(), 0xFFFF7043.toInt(), 0xFF6A1B9A.toInt(),
    0xFF4FC3F7.toInt(),
)

/**
 * HSV colour picker: swatches plus hue / saturation / brightness sliders.
 * Dragging updates the preview live; [onSeedChangeFinished] applies the
 * theme (recreates activities) once the finger lifts.
 */
@Composable
private fun ColorPicker(
    seedArgb: Int,
    onSeedChange: (Int) -> Unit,
    onSeedChangeFinished: () -> Unit,
) {
    val seed = Color(seedArgb)
    val (hue, saturation, value) = seedHsl(seed)
    // The big preview cross-fades as the seed changes under the sliders.
    val previewColor by animateColorAsState(
        targetValue = seed,
        animationSpec = tuxunColorSpec(LocalAnimStyle.current),
        label = "pickerPreview",
    )

    fun emit(h: Float, s: Float, v: Float) {
        val color = hslToColor(h, s, v)
        onSeedChange(
            (0xFF shl 24) or
                (color.red * 255).toInt().shl(16) or
                (color.green * 255).toInt().shl(8) or
                (color.blue * 255).toInt(),
        )
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(previewColor)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    ),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "#%06X".format(seedArgb and 0xFFFFFF),
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(bottom = 4.dp),
        ) {
            CUSTOM_SEED_SWATCHES.take(6).forEach { swatch ->
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(swatch))
                        .border(
                            width = if (swatch == seedArgb) 2.dp else 1.dp,
                            color = if (swatch == seedArgb) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape,
                        )
                        .clickable { onSeedChange(swatch); onSeedChangeFinished() },
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(bottom = 4.dp),
        ) {
            CUSTOM_SEED_SWATCHES.drop(6).take(6).forEach { swatch ->
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(swatch))
                        .border(
                            width = if (swatch == seedArgb) 2.dp else 1.dp,
                            color = if (swatch == seedArgb) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape,
                        )
                        .clickable { onSeedChange(swatch); onSeedChangeFinished() },
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(bottom = 12.dp),
        ) {
            CUSTOM_SEED_SWATCHES.drop(12).forEach { swatch ->
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(swatch))
                        .border(
                            width = if (swatch == seedArgb) 2.dp else 1.dp,
                            color = if (swatch == seedArgb) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape,
                        )
                        .clickable { onSeedChange(swatch); onSeedChangeFinished() },
                )
            }
        }

        GradientSlider(
            label = stringResource(R.string.color_hue),
            value = hue,
            valueRange = 0f..359f,
            gradient = Brush.horizontalGradient(
                (0..6).map { i -> hslToColor(i * 60f, 1f, 0.5f) },
            ),
            onValueChange = { emit(it, saturation, value) },
            onValueChangeFinished = onSeedChangeFinished,
        )
        GradientSlider(
            label = stringResource(R.string.color_saturation),
            value = saturation,
            valueRange = 0f..1f,
            gradient = Brush.horizontalGradient(
                listOf(hslToColor(hue, 0f, value), hslToColor(hue, 1f, value)),
            ),
            onValueChange = { emit(hue, it, value) },
            onValueChangeFinished = onSeedChangeFinished,
        )
        GradientSlider(
            label = stringResource(R.string.color_value),
            value = value,
            valueRange = 0f..1f,
            gradient = Brush.horizontalGradient(
                listOf(hslToColor(hue, saturation, 0f), hslToColor(hue, saturation, 1f)),
            ),
            onValueChange = { emit(hue, saturation, it) },
            onValueChangeFinished = onSeedChangeFinished,
        )
    }
}

/** A labelled slider whose track is a colour gradient (thumb stays white). */
@Composable
private fun GradientSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    gradient: Brush,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(6.dp))
                    .background(gradient),
            )
            Slider(
                value = value,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = valueRange,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
            )
        }
    }
}
