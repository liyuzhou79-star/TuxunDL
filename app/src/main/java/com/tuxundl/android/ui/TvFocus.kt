package com.tuxundl.android.ui

import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Visible focus state for remote / D-pad navigation (TV Beta).
 *
 * The app's controls are built on clickable / combinedClickable /
 * Material3 buttons, which are all focusable by default — a remote
 * can already *reach* them. What was missing is any way to *see*
 * where the focus is: the power circle even passes indication =
 * null. This modifier draws a primary-colour outline around the
 * element while it holds focus and nothing at all otherwise.
 *
 * It is deliberately not gated on the TV build: focus only ever
 * happens when a non-touch input drives it, so on phones (pure
 * touch) the border simply never appears and touch behaviour is
 * unchanged.
 *
 * Placement: put it in the modifier chain *before* the clickable
 * modifier that creates the focus target, so onFocusChanged
 * observes that target.
 */
@Composable
fun Modifier.tvFocusBorder(
    shape: Shape,
    width: Dp = 2.5.dp,
    color: Color = MaterialTheme.colorScheme.primary,
): Modifier {
    var focused by remember { mutableStateOf(false) }

    return this
        .onFocusChanged { focused = it.isFocused }
        .then(
            if (focused) {
                Modifier.border(width, color, shape)
            } else {
                Modifier
            },
        )
}
