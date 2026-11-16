package com.tuxundl.android.design.model

/**
 * Selectable colour palette for the Compose UI.
 *
 * Stored in UiStore; [Default] is the original slate-blue palette
 * (with dynamic colour on Android 12+), the others are fixed schemes.
 */
enum class ThemePalette {
    Default, Blue, Sakura, Mono
}
