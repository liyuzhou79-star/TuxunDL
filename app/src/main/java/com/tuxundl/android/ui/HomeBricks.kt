package com.tuxundl.android.ui

/**
 * The single definition of the home-screen brick set.
 *
 * [HOME_BRICK_ALL_IDS] is every brick the drag system knows about,
 * including bricks retired from the default set (traffic, providers,
 * help, about): they can still appear in orders stored by older
 * versions, and those stored orders are honoured exactly as written.
 *
 * [HOME_BRICK_DEFAULT_ORDER] is what a fresh install (or an empty
 * stored order) gets since 1.2.1: the five core bricks. The traffic
 * brick duplicated the monitor card, providers live inside the
 * profiles flow, and Help/About remain reachable from Settings.
 */
internal val HOME_BRICK_ALL_IDS: List<String> = listOf(
    "node", "traffic", "proxy", "profile", "providers",
    "logs", "settings", "help", "about",
)

/** Default brick order for fresh installs: the five core bricks. */
internal val HOME_BRICK_DEFAULT_ORDER: List<String> = listOf(
    "node", "proxy", "profile", "logs", "settings",
)
