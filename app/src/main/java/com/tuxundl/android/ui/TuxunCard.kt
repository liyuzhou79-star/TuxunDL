package com.tuxundl.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        content = content,
    )
}
