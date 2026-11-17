package com.tuxundl.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.tuxundl.android.design.model.ThemePalette
import com.tuxundl.android.design.store.UiStore
import kotlin.math.max
import kotlin.math.min

// ---------------------------------------------------------------------------
// Custom seed colour: derive a full Material 3-ish scheme from one colour.
//
// The derivation is plain HSL maths (no extra library): the seed sets the
// hue, containers are the same hue lightened/darkened, and the secondary /
// tertiary hues are the seed hue rotated by +/-40 degrees. Light text on
// dark primaries and dark text on light primaries are picked by luminance,
// so any seed stays readable in both light and dark mode.
// ---------------------------------------------------------------------------

/** RGB (0..1) -> HSL (hue 0..360, saturation/lightness 0..1). */
private fun rgbToHsl(r: Float, g: Float, b: Float): FloatArray {
    val maxC = max(r, max(g, b))
    val minC = min(r, min(g, b))
    val l = (maxC + minC) / 2f

    if (maxC == minC) return floatArrayOf(0f, 0f, l)

    val d = maxC - minC
    val s = if (l > 0.5f) d / (2f - maxC - minC) else d / (maxC + minC)
    val h = when (maxC) {
        r -> (g - b) / d + (if (g < b) 6f else 0f)
        g -> (b - r) / d + 2f
        else -> (r - g) / d + 4f
    } * 60f

    return floatArrayOf(h, s, l)
}

/** HSL (hue 0..360, saturation/lightness 0..1) -> Color. */
fun hslToColor(hue: Float, saturation: Float, lightness: Float): Color {
    val h = ((hue % 360f) + 360f) % 360f / 360f
    val s = saturation.coerceIn(0f, 1f)
    val l = lightness.coerceIn(0f, 1f)

    if (s == 0f) return Color(l, l, l)

    val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
    val p = 2f * l - q

    fun channel(t: Float): Float {
        var tt = t
        if (tt < 0f) tt += 1f
        if (tt > 1f) tt -= 1f
        return when {
            tt < 1f / 6f -> p + (q - p) * 6f * tt
            tt < 1f / 2f -> q
            tt < 2f / 3f -> p + (q - p) * (2f / 3f - tt) * 6f
            else -> p
        }
    }

    return Color(channel(h + 1f / 3f), channel(h), channel(h - 1f / 3f))
}

/** Seed colour as HSL; grey seeds keep hue 0 and rely on lightness. */
fun seedHsl(seed: Color): FloatArray =
    rgbToHsl(seed.red, seed.green, seed.blue)

/** White text on dark colours, near-black text on light ones. */
private fun onColorFor(color: Color): Color =
    if (color.luminance() < 0.5f) Color.White else Color(0xFF1A1A1A)

fun customLightScheme(seed: Color): ColorScheme {
    val (h, s, _) = seedHsl(seed)
    val sat = max(s, 0.35f)
    val primary = hslToColor(h, sat, 0.38f)
    val secondary = hslToColor(h + 40f, sat * 0.8f, 0.35f)
    val tertiary = hslToColor(h - 40f, sat * 0.8f, 0.4f)

    return lightColorScheme(
        primary = primary,
        onPrimary = onColorFor(primary),
        primaryContainer = hslToColor(h, sat * 0.7f, 0.88f),
        onPrimaryContainer = hslToColor(h, sat, 0.15f),
        secondary = secondary,
        onSecondary = onColorFor(secondary),
        secondaryContainer = hslToColor(h + 40f, sat * 0.5f, 0.88f),
        onSecondaryContainer = hslToColor(h + 40f, sat, 0.15f),
        tertiary = tertiary,
        onTertiary = onColorFor(tertiary),
        tertiaryContainer = hslToColor(h - 40f, sat * 0.6f, 0.88f),
        onTertiaryContainer = hslToColor(h - 40f, sat, 0.15f),
        error = SlateError,
        onError = SlateOnError,
        errorContainer = SlateErrorContainer,
        onErrorContainer = SlateOnErrorContainer,
        background = hslToColor(h, 0.08f, 0.98f),
        onBackground = hslToColor(h, 0.1f, 0.1f),
        surface = hslToColor(h, 0.08f, 0.98f),
        onSurface = hslToColor(h, 0.1f, 0.1f),
        surfaceVariant = hslToColor(h, 0.1f, 0.9f),
        onSurfaceVariant = hslToColor(h, 0.08f, 0.3f),
        outline = hslToColor(h, 0.05f, 0.5f),
        outlineVariant = hslToColor(h, 0.08f, 0.82f),
    )
}

fun customDarkScheme(seed: Color): ColorScheme {
    val (h, s, _) = seedHsl(seed)
    val sat = max(s, 0.35f)
    val primary = hslToColor(h, sat, 0.8f)
    val secondary = hslToColor(h + 40f, sat * 0.7f, 0.75f)
    val tertiary = hslToColor(h - 40f, sat * 0.7f, 0.75f)

    return darkColorScheme(
        primary = primary,
        onPrimary = hslToColor(h, sat, 0.15f),
        primaryContainer = hslToColor(h, sat * 0.7f, 0.3f),
        onPrimaryContainer = hslToColor(h, sat * 0.6f, 0.9f),
        secondary = secondary,
        onSecondary = hslToColor(h + 40f, sat, 0.15f),
        secondaryContainer = hslToColor(h + 40f, sat * 0.5f, 0.3f),
        onSecondaryContainer = hslToColor(h + 40f, sat * 0.5f, 0.9f),
        tertiary = tertiary,
        onTertiary = hslToColor(h - 40f, sat, 0.15f),
        tertiaryContainer = hslToColor(h - 40f, sat * 0.5f, 0.3f),
        onTertiaryContainer = hslToColor(h - 40f, sat * 0.5f, 0.9f),
        error = SlateErrorDark,
        onError = SlateOnErrorDark,
        errorContainer = SlateErrorContainerDark,
        onErrorContainer = SlateOnErrorContainerDark,
        background = hslToColor(h, 0.15f, 0.07f),
        onBackground = hslToColor(h, 0.1f, 0.9f),
        surface = hslToColor(h, 0.15f, 0.07f),
        onSurface = hslToColor(h, 0.1f, 0.9f),
        surfaceVariant = hslToColor(h, 0.12f, 0.25f),
        onSurfaceVariant = hslToColor(h, 0.08f, 0.8f),
        outline = hslToColor(h, 0.05f, 0.6f),
        outlineVariant = hslToColor(h, 0.1f, 0.28f),
    )
}

/** Stored custom seed as a Compose colour, or null when no custom colour is set. */
fun customSeedColor(uiStore: UiStore): Color? =
    if (uiStore.customThemeColor != 0) Color(uiStore.customThemeColor) else null

private val LightColorScheme = lightColorScheme(
    primary = SlateBluePrimary,
    onPrimary = SlateBlueOnPrimary,
    primaryContainer = SlateBluePrimaryContainer,
    onPrimaryContainer = SlateBlueOnPrimaryContainer,
    secondary = SlateSecondary,
    onSecondary = SlateOnSecondary,
    secondaryContainer = SlateSecondaryContainer,
    onSecondaryContainer = SlateOnSecondaryContainer,
    tertiary = SlateTertiary,
    onTertiary = SlateOnTertiary,
    tertiaryContainer = SlateTertiaryContainer,
    onTertiaryContainer = SlateOnTertiaryContainer,
    error = SlateError,
    onError = SlateOnError,
    errorContainer = SlateErrorContainer,
    onErrorContainer = SlateOnErrorContainer,
    background = SlateBackground,
    onBackground = SlateOnBackground,
    surface = SlateSurface,
    onSurface = SlateOnSurface,
    surfaceVariant = SlateSurfaceVariant,
    onSurfaceVariant = SlateOnSurfaceVariant,
    outline = SlateOutline,
    outlineVariant = SlateOutlineVariant,
)

private val DarkColorScheme = darkColorScheme(
    primary = SlateBluePrimaryDark,
    onPrimary = SlateBlueOnPrimaryDark,
    primaryContainer = SlateBluePrimaryContainerDark,
    onPrimaryContainer = SlateBlueOnPrimaryContainerDark,
    secondary = SlateSecondaryDark,
    onSecondary = SlateOnSecondaryDark,
    secondaryContainer = SlateSecondaryContainerDark,
    onSecondaryContainer = SlateOnSecondaryContainerDark,
    tertiary = SlateTertiaryDark,
    onTertiary = SlateOnTertiaryDark,
    tertiaryContainer = SlateTertiaryContainerDark,
    onTertiaryContainer = SlateOnTertiaryContainerDark,
    error = SlateErrorDark,
    onError = SlateOnErrorDark,
    errorContainer = SlateErrorContainerDark,
    onErrorContainer = SlateOnErrorContainerDark,
    background = SlateBackgroundDark,
    onBackground = SlateOnBackgroundDark,
    surface = SlateSurfaceDark,
    onSurface = SlateOnSurfaceDark,
    surfaceVariant = SlateSurfaceVariantDark,
    onSurfaceVariant = SlateOnSurfaceVariantDark,
    outline = SlateOutlineDark,
    outlineVariant = SlateOutlineVariantDark,
)

private val BlueLightColorScheme = lightColorScheme(
    primary = BluePrimary,
    onPrimary = BlueOnPrimary,
    primaryContainer = BluePrimaryContainer,
    onPrimaryContainer = BlueOnPrimaryContainer,
    secondary = BlueSecondary,
    onSecondary = BlueOnSecondary,
    secondaryContainer = BlueSecondaryContainer,
    onSecondaryContainer = BlueOnSecondaryContainer,
    tertiary = BlueTertiary,
    onTertiary = BlueOnTertiary,
    tertiaryContainer = BlueTertiaryContainer,
    onTertiaryContainer = BlueOnTertiaryContainer,
    error = SlateError,
    onError = SlateOnError,
    errorContainer = SlateErrorContainer,
    onErrorContainer = SlateOnErrorContainer,
    background = BlueBackground,
    onBackground = BlueOnBackground,
    surface = BlueSurface,
    onSurface = BlueOnSurface,
    surfaceVariant = BlueSurfaceVariant,
    onSurfaceVariant = BlueOnSurfaceVariant,
    outline = BlueOutline,
    outlineVariant = BlueOutlineVariant,
)

private val BlueDarkColorScheme = darkColorScheme(
    primary = BluePrimaryDark,
    onPrimary = BlueOnPrimaryDark,
    primaryContainer = BluePrimaryContainerDark,
    onPrimaryContainer = BlueOnPrimaryContainerDark,
    secondary = BlueSecondaryDark,
    onSecondary = BlueOnSecondaryDark,
    secondaryContainer = BlueSecondaryContainerDark,
    onSecondaryContainer = BlueOnSecondaryContainerDark,
    tertiary = BlueTertiaryDark,
    onTertiary = BlueOnTertiaryDark,
    tertiaryContainer = BlueTertiaryContainerDark,
    onTertiaryContainer = BlueOnTertiaryContainerDark,
    error = SlateErrorDark,
    onError = SlateOnErrorDark,
    errorContainer = SlateErrorContainerDark,
    onErrorContainer = SlateOnErrorContainerDark,
    background = BlueBackgroundDark,
    onBackground = BlueOnBackgroundDark,
    surface = BlueSurfaceDark,
    onSurface = BlueOnSurfaceDark,
    surfaceVariant = BlueSurfaceVariantDark,
    onSurfaceVariant = BlueOnSurfaceVariantDark,
    outline = BlueOutlineDark,
    outlineVariant = BlueOutlineVariantDark,
)

private val SakuraLightColorScheme = lightColorScheme(
    primary = SakuraPrimary,
    onPrimary = SakuraOnPrimary,
    primaryContainer = SakuraPrimaryContainer,
    onPrimaryContainer = SakuraOnPrimaryContainer,
    secondary = SakuraSecondary,
    onSecondary = SakuraOnSecondary,
    secondaryContainer = SakuraSecondaryContainer,
    onSecondaryContainer = SakuraOnSecondaryContainer,
    tertiary = SakuraTertiary,
    onTertiary = SakuraOnTertiary,
    tertiaryContainer = SakuraTertiaryContainer,
    onTertiaryContainer = SakuraOnTertiaryContainer,
    error = SlateError,
    onError = SlateOnError,
    errorContainer = SlateErrorContainer,
    onErrorContainer = SlateOnErrorContainer,
    background = SakuraBackground,
    onBackground = SakuraOnBackground,
    surface = SakuraSurface,
    onSurface = SakuraOnSurface,
    surfaceVariant = SakuraSurfaceVariant,
    onSurfaceVariant = SakuraOnSurfaceVariant,
    outline = SakuraOutline,
    outlineVariant = SakuraOutlineVariant,
)

private val SakuraDarkColorScheme = darkColorScheme(
    primary = SakuraPrimaryDark,
    onPrimary = SakuraOnPrimaryDark,
    primaryContainer = SakuraPrimaryContainerDark,
    onPrimaryContainer = SakuraOnPrimaryContainerDark,
    secondary = SakuraSecondaryDark,
    onSecondary = SakuraOnSecondaryDark,
    secondaryContainer = SakuraSecondaryContainerDark,
    onSecondaryContainer = SakuraOnSecondaryContainerDark,
    tertiary = SakuraTertiaryDark,
    onTertiary = SakuraOnTertiaryDark,
    tertiaryContainer = SakuraTertiaryContainerDark,
    onTertiaryContainer = SakuraOnTertiaryContainerDark,
    error = SlateErrorDark,
    onError = SlateOnErrorDark,
    errorContainer = SlateErrorContainerDark,
    onErrorContainer = SlateOnErrorContainerDark,
    background = SakuraBackgroundDark,
    onBackground = SakuraOnBackgroundDark,
    surface = SakuraSurfaceDark,
    onSurface = SakuraOnSurfaceDark,
    surfaceVariant = SakuraSurfaceVariantDark,
    onSurfaceVariant = SakuraOnSurfaceVariantDark,
    outline = SakuraOutlineDark,
    outlineVariant = SakuraOutlineVariantDark,
)

private val MonoLightColorScheme = lightColorScheme(
    primary = MonoPrimary,
    onPrimary = MonoOnPrimary,
    primaryContainer = MonoPrimaryContainer,
    onPrimaryContainer = MonoOnPrimaryContainer,
    secondary = MonoSecondary,
    onSecondary = MonoOnSecondary,
    secondaryContainer = MonoSecondaryContainer,
    onSecondaryContainer = MonoOnSecondaryContainer,
    tertiary = MonoTertiary,
    onTertiary = MonoOnTertiary,
    tertiaryContainer = MonoTertiaryContainer,
    onTertiaryContainer = MonoOnTertiaryContainer,
    error = SlateError,
    onError = SlateOnError,
    errorContainer = SlateErrorContainer,
    onErrorContainer = SlateOnErrorContainer,
    background = MonoBackground,
    onBackground = MonoOnBackground,
    surface = MonoSurface,
    onSurface = MonoOnSurface,
    surfaceVariant = MonoSurfaceVariant,
    onSurfaceVariant = MonoOnSurfaceVariant,
    outline = MonoOutline,
    outlineVariant = MonoOutlineVariant,
)

private val MonoDarkColorScheme = darkColorScheme(
    primary = MonoPrimaryDark,
    onPrimary = MonoOnPrimaryDark,
    primaryContainer = MonoPrimaryContainerDark,
    onPrimaryContainer = MonoOnPrimaryContainerDark,
    secondary = MonoSecondaryDark,
    onSecondary = MonoOnSecondaryDark,
    secondaryContainer = MonoSecondaryContainerDark,
    onSecondaryContainer = MonoOnSecondaryContainerDark,
    tertiary = MonoTertiaryDark,
    onTertiary = MonoOnTertiaryDark,
    tertiaryContainer = MonoTertiaryContainerDark,
    onTertiaryContainer = MonoOnTertiaryContainerDark,
    error = SlateErrorDark,
    onError = SlateOnErrorDark,
    errorContainer = SlateErrorContainerDark,
    onErrorContainer = SlateOnErrorContainerDark,
    background = MonoBackgroundDark,
    onBackground = MonoOnBackgroundDark,
    surface = MonoSurfaceDark,
    onSurface = MonoOnSurfaceDark,
    surfaceVariant = MonoSurfaceVariantDark,
    onSurfaceVariant = MonoOnSurfaceVariantDark,
    outline = MonoOutlineDark,
    outlineVariant = MonoOutlineVariantDark,
)

/** Light scheme of a palette, used for previews in the appearance screen. */
fun lightSchemeOf(palette: ThemePalette): ColorScheme = when (palette) {
    ThemePalette.Default -> LightColorScheme
    ThemePalette.Blue -> BlueLightColorScheme
    ThemePalette.Sakura -> SakuraLightColorScheme
    ThemePalette.Mono -> MonoLightColorScheme
}

/** Dark scheme of a palette, used for previews in the appearance screen. */
fun darkSchemeOf(palette: ThemePalette): ColorScheme = when (palette) {
    ThemePalette.Default -> DarkColorScheme
    ThemePalette.Blue -> BlueDarkColorScheme
    ThemePalette.Sakura -> SakuraDarkColorScheme
    ThemePalette.Mono -> MonoDarkColorScheme
}

@Composable
fun TuxunDLTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    palette: ThemePalette = ThemePalette.Default,
    dynamicColor: Boolean = true,
    customSeed: Color? = null,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        // A custom seed colour beats everything else (presets and dynamic).
        customSeed != null -> {
            if (darkTheme) customDarkScheme(customSeed) else customLightScheme(customSeed)
        }
        // Dynamic (wallpaper) colour is kept for the default palette only;
        // the named palettes always use their fixed schemes.
        palette == ThemePalette.Default && dynamicColor &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkSchemeOf(palette)
        else -> lightSchemeOf(palette)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
