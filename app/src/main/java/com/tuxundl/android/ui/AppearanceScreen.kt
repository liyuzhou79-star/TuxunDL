package com.tuxundl.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tuxundl.android.R
import com.tuxundl.android.design.model.DarkMode
import com.tuxundl.android.design.model.ThemePalette
import com.tuxundl.android.design.store.UiStore
import com.tuxundl.android.ui.theme.darkSchemeOf
import com.tuxundl.android.ui.theme.lightSchemeOf
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
    onBack: () -> Unit,
) {
    var palette by remember { mutableStateOf(uiStore.themePalette) }
    var darkMode by remember { mutableStateOf(uiStore.darkMode) }
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
                .padding(vertical = 8.dp),
        ) {
            SectionCard(title = stringResource(R.string.theme_palette)) {
                PaletteRow(
                    name = stringResource(R.string.theme_default),
                    palette = ThemePalette.Default,
                    darkTheme = darkTheme,
                    selected = palette == ThemePalette.Default,
                    onClick = {
                        palette = ThemePalette.Default
                        onPaletteChange(ThemePalette.Default)
                    },
                )
                PaletteRow(
                    name = stringResource(R.string.theme_blue),
                    palette = ThemePalette.Blue,
                    darkTheme = darkTheme,
                    selected = palette == ThemePalette.Blue,
                    onClick = {
                        palette = ThemePalette.Blue
                        onPaletteChange(ThemePalette.Blue)
                    },
                )
                PaletteRow(
                    name = stringResource(R.string.theme_sakura),
                    palette = ThemePalette.Sakura,
                    darkTheme = darkTheme,
                    selected = palette == ThemePalette.Sakura,
                    onClick = {
                        palette = ThemePalette.Sakura
                        onPaletteChange(ThemePalette.Sakura)
                    },
                )
                PaletteRow(
                    name = stringResource(R.string.theme_mono),
                    palette = ThemePalette.Mono,
                    darkTheme = darkTheme,
                    selected = palette == ThemePalette.Mono,
                    onClick = {
                        palette = ThemePalette.Mono
                        onPaletteChange(ThemePalette.Mono)
                    },
                )
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
        androidx.compose.material3.Switch(
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
                .background(scheme.primary)
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
