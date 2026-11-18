package com.tuxundl.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Corner radius for standard tiles/cards across the app. */
val TuxunCardCorner: Dp = 20.dp

/** Corner radius for large emphasis cards. */
val TuxunCardCornerLarge: Dp = 24.dp

/**
 * The one card style used across the app: a 1dp outlineVariant border, a
 * unified corner radius and a whisper of elevation, so every screen draws
 * its contours the same way. All Compose cards should go through here
 * instead of using [Card] directly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TuxunCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(TuxunCardCorner),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = shape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tuxunCardBorder()),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        content = content,
    )
}

/** Clickable variant of [TuxunCard]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TuxunCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(TuxunCardCorner),
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }

    // Remote / D-pad focus visibility (TV Beta): Material3's Card
    // is focusable but draws nothing for focus, so a remote user
    // could not see where they were. While focused, the card's
    // border steps up to a thick primary outline. Focus never
    // happens under touch input, so phones see no change.
    var focused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .tuxunPressScale(interactionSource, pressedScale = 0.98f),
        shape = shape,
        border = if (focused) {
            BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.tuxunCardBorder())
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        interactionSource = interactionSource,
        content = content,
    )
}

/**
 * Card border colour: in dark mode shadows all but disappear, so the
 * border steps up from outlineVariant to the stronger outline tone to
 * keep cards separated from the background.
 */
@Composable
private fun ColorScheme.tuxunCardBorder(): Color =
    if (surface.luminance() < 0.4f) outline else outlineVariant
