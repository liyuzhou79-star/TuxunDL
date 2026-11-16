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
import androidx.compose.ui.platform.LocalContext
import com.tuxundl.android.design.model.ThemePalette

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
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
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
