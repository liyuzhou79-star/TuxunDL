package com.tuxundl.android.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tuxundl.android.R
import com.tuxundl.android.design.model.ThemePalette
import com.tuxundl.android.design.store.UiStore
import com.tuxundl.android.ui.theme.darkSchemeOf
import com.tuxundl.android.ui.theme.lightSchemeOf
import com.tuxundl.android.util.Haptics
import kotlinx.coroutines.launch
import com.tuxundl.android.design.R as DesignR

/**
 * First-run onboarding (1.3.0), shown by MainActivity only for
 * genuinely fresh installs: users who already accepted the disclaimer
 * on an older version count as completed and never see this.
 *
 * Steps: 1 language, 2 disclaimer & terms, 3 add a subscription URL,
 * 4 theme palette + haptics tuning, 5 done. Pages slide horizontally
 * with a spring and fade, the progress dots morph (the active one
 * stretches into a pill), and each step's elements stagger in.
 *
 * Every choice persists to [UiStore] the moment it is made: palette
 * picks recompose the host theme live via [onPaletteSelected],
 * haptics picks buzz a preview at the picked strength, and the
 * language pick is applied by the host when the flow finishes (it
 * recreates into the main screen in the new language).
 */
@Composable
fun OnboardingScreen(
    store: UiStore,
    onPaletteSelected: (ThemePalette) -> Unit,
    onAddSubscription: suspend (url: String, name: String) -> Boolean,
    onImportScan: () -> Unit,
    onImportAlbum: () -> Unit,
    onImportFile: () -> Unit,
    importOutcome: Int,
    importNonce: Int,
    onFinish: () -> Unit,
) {
    val style = animStyle()
    var page by remember { mutableStateOf(0) }
    var disclaimerRead by remember { mutableStateOf(false) }
    val pageCount = 5

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            // Morphing progress dots.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(pageCount) { index ->
                    val dotWidth by animateDpAsState(
                        targetValue = if (index == page) 24.dp else 8.dp,
                        animationSpec = tuxunSizeSpec(style),
                        label = "onboardingDot",
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .height(8.dp)
                            .width(dotWidth)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(
                                if (index == page) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                            ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally(
                        animationSpec = tuxunSnapSpec(style),
                    ) { width -> if (forward) width else -width } +
                        fadeIn(tuxunFadeSpec(style, entering = true))) togetherWith
                        (slideOutHorizontally(
                            animationSpec = tuxunSnapSpec(style),
                        ) { width -> if (forward) -width else width } +
                            fadeOut(tuxunFadeSpec(style, entering = false)))
                },
                label = "onboardingPage",
                modifier = Modifier.weight(1f),
            ) { current ->
                when (current) {
                    0 -> LanguageStep(store = store)
                    1 -> DisclaimerStep(
                        onReadChange = { disclaimerRead = it },
                    )
                    2 -> SubscriptionStep(
                        onAddSubscription = onAddSubscription,
                        onImportScan = onImportScan,
                        onImportAlbum = onImportAlbum,
                        onImportFile = onImportFile,
                        importOutcome = importOutcome,
                        importNonce = importNonce,
                        onAdded = { page = 3 },
                    )
                    3 -> ThemeStep(
                        store = store,
                        onPaletteSelected = onPaletteSelected,
                    )
                    else -> DoneStep()
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom navigation: Back on the middle steps, the
            // primary action on the right.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (page in 1..3) {
                    TextButton(onClick = { page-- }) {
                        Text(text = stringResource(R.string.onboarding_back))
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                when (page) {
                    0 -> Button(onClick = { page = 1 }) {
                        Text(text = stringResource(R.string.onboarding_next))
                    }
                    1 -> Button(
                        onClick = { page = 2 },
                        enabled = disclaimerRead,
                    ) {
                        Text(text = stringResource(R.string.onboarding_agree_continue))
                    }
                    2 -> TextButton(onClick = { page = 3 }) {
                        Text(text = stringResource(R.string.onboarding_skip))
                    }
                    3 -> Button(onClick = { page = 4 }) {
                        Text(text = stringResource(R.string.onboarding_next))
                    }
                    else -> Button(onClick = onFinish) {
                        Text(text = stringResource(R.string.onboarding_start))
                    }
                }
            }
        }
    }
}

/** A step's headline block: title plus an optional subtitle. */
@Composable
private fun StepTitle(title: String, subtitle: String?, staggerIndex: Int) {
    Column(modifier = Modifier.tuxunStagger(staggerIndex)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Step 1: language — the same options as the Settings picker. */
@Composable
private fun LanguageStep(store: UiStore) {
    var selected by remember { mutableStateOf(store.appLanguage) }

    fun pick(tag: String) {
        selected = tag
        store.appLanguage = tag
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        StepTitle(
            title = stringResource(R.string.onboarding_language_title),
            subtitle = stringResource(R.string.onboarding_language_subtitle),
            staggerIndex = 0,
        )
        Spacer(modifier = Modifier.height(16.dp))

        Column(modifier = Modifier.tuxunStagger(1)) {
            LanguageRow(
                name = stringResource(R.string.language_follow_system),
                selected = selected.isEmpty(),
                onClick = { pick("") },
            )
            LANGUAGE_OPTIONS.forEach { (tag, name) ->
                LanguageRow(
                    name = name,
                    selected = selected == tag,
                    onClick = { pick(tag) },
                )
            }
        }
    }
}

@Composable
private fun LanguageRow(name: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = name, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Step 2: the disclaimer & terms. The Agree button unlocks once the
 * text has been scrolled to the end (or fits on screen outright).
 */
@Composable
private fun DisclaimerStep(onReadChange: (Boolean) -> Unit) {
    val scrollState = rememberScrollState()
    val read by remember {
        derivedStateOf { !scrollState.canScrollForward }
    }
    LaunchedEffect(read) { onReadChange(read) }

    Column(modifier = Modifier.fillMaxSize()) {
        StepTitle(
            title = stringResource(R.string.disclaimer_title),
            subtitle = null,
            staggerIndex = 0,
        )
        Spacer(modifier = Modifier.height(16.dp))

        TuxunCard(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .tuxunStagger(1),
        ) {
            Text(
                text = stringResource(R.string.disclaimer_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(scrollState),
            )
        }

        if (!read) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.onboarding_read_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private enum class AddState { Idle, Adding, Added, Failed }

/** Step 3: add a subscription URL now, or skip from the bottom bar. */
@Composable
private fun SubscriptionStep(
    onAddSubscription: suspend (url: String, name: String) -> Boolean,
    onImportScan: () -> Unit,
    onImportAlbum: () -> Unit,
    onImportFile: () -> Unit,
    importOutcome: Int,
    importNonce: Int,
    onAdded: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var addState by remember { mutableStateOf(AddState.Idle) }
    val defaultName = stringResource(DesignR.string.new_profile)

    // Import launchers (scan / album / file) live in MainActivity;
    // their outcome lands here as (nonce, outcome): 1 added,
    // 2 failed, 3 cancelled back to idle.
    LaunchedEffect(importNonce) {
        if (importNonce <= 0) return@LaunchedEffect
        when (importOutcome) {
            1 -> {
                addState = AddState.Added
                onAdded()
            }
            2 -> addState = AddState.Failed
            else -> if (addState == AddState.Adding) addState = AddState.Idle
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        StepTitle(
            title = stringResource(R.string.onboarding_sub_title),
            subtitle = stringResource(R.string.onboarding_sub_subtitle),
            staggerIndex = 0,
        )
        Spacer(modifier = Modifier.height(20.dp))

        Column(modifier = Modifier.tuxunStagger(1)) {
            PasteUrlField(
                value = url,
                onValueChange = {
                    url = it
                    if (addState == AddState.Failed) addState = AddState.Idle
                },
                label = stringResource(R.string.onboarding_sub_url),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(text = stringResource(R.string.onboarding_sub_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    addState = AddState.Adding
                    scope.launch {
                        val ok = onAddSubscription(
                            url.trim(),
                            name.ifBlank { defaultName },
                        )
                        addState = if (ok) AddState.Added else AddState.Failed
                        if (ok) onAdded()
                    }
                },
                enabled = url.isNotBlank() &&
                    addState != AddState.Adding &&
                    addState != AddState.Added,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (addState == AddState.Adding) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.onboarding_sub_adding))
                } else {
                    Text(text = stringResource(R.string.onboarding_sub_add))
                }
            }

            when (addState) {
                AddState.Added -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.onboarding_sub_added),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                AddState.Failed -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.onboarding_sub_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                else -> Unit
            }

            // The same three import ways the profiles page offers
            // (1.4.0): scan a QR code, pick an album image, pick a
            // file. The launchers live in MainActivity; the outcome
            // comes back through the import signal above.
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.onboarding_import_or),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            ImportActionsRow(
                onScan = {
                    addState = AddState.Adding
                    onImportScan()
                },
                onAlbum = {
                    addState = AddState.Adding
                    onImportAlbum()
                },
                onFile = {
                    addState = AddState.Adding
                    onImportFile()
                },
                enabled = addState != AddState.Adding && addState != AddState.Added,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Step 4: preset theme palette + haptics switch and strength. */
@Composable
private fun ThemeStep(
    store: UiStore,
    onPaletteSelected: (ThemePalette) -> Unit,
) {
    val context = LocalContext.current
    var palette by remember { mutableStateOf(store.themePalette) }
    var customSeed by remember { mutableStateOf(store.customThemeColor) }
    var hapticsEnabled by remember { mutableStateOf(store.hapticsEnabled) }
    var hapticsStrength by remember { mutableStateOf(store.hapticsStrength) }
    val darkTheme =
        MaterialTheme.colorScheme.background.luminance() < 0.5f

    fun selectPalette(value: ThemePalette) {
        palette = value
        customSeed = 0
        store.customThemeColor = 0
        onPaletteSelected(value)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        StepTitle(
            title = stringResource(R.string.onboarding_theme_title),
            subtitle = stringResource(R.string.onboarding_theme_subtitle),
            staggerIndex = 0,
        )
        Spacer(modifier = Modifier.height(16.dp))

        Column(modifier = Modifier.tuxunStagger(1)) {
            listOf(
                ThemePalette.Default to R.string.theme_default,
                ThemePalette.Blue to R.string.theme_blue,
                ThemePalette.Sakura to R.string.theme_sakura,
                ThemePalette.Mono to R.string.theme_mono,
            ).forEach { (value, nameRes) ->
                PalettePickRow(
                    name = stringResource(nameRes),
                    palette = value,
                    darkTheme = darkTheme,
                    selected = palette == value && customSeed == 0,
                    onClick = { selectPalette(value) },
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Column(modifier = Modifier.tuxunStagger(2)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.haptics),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                TuxunSwitch(
                    checked = hapticsEnabled,
                    onCheckedChange = {
                        hapticsEnabled = it
                        store.hapticsEnabled = it
                    },
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.haptics_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.haptics_strength),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            TuxunSegmentedRow(
                options = listOf(
                    stringResource(R.string.haptics_light),
                    stringResource(R.string.haptics_medium),
                    stringResource(R.string.haptics_strong),
                ),
                selectedIndex = hapticsStrength,
                onSelect = { index ->
                    hapticsStrength = index
                    store.hapticsStrength = index
                    // Preview the picked strength right away.
                    if (hapticsEnabled) {
                        Haptics.buzz(context, index, Haptics.Kind.Tap)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** One preset-palette row: colour dot, name, radio. */
@Composable
private fun PalettePickRow(
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
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
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
        RadioButton(selected = selected, onClick = onClick)
    }
}

/** Step 5: a short welcome and the way in. */
@Composable
private fun DoneStep() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .tuxunStagger(0),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(DesignR.drawable.ic_outline_check_circle),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(52.dp),
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.onboarding_done_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.tuxunStagger(1),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_done_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.tuxunStagger(2),
        )
    }
}
