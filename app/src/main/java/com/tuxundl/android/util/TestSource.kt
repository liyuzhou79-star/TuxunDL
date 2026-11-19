package com.tuxundl.android.util

/**
 * Delay-test sources: the URL the app probes nodes with.
 *
 * The stored preference is the URL itself ("" = the built-in
 * default); the proxy screen's menu matches it against the presets
 * and treats anything else as the user's custom source.
 *
 * Scope: app-initiated tests (single node, whole group, fastest
 * pick, the home screen's periodic quality probe) use the chosen
 * URL. The automatic health checks inside URLTest / Fallback
 * groups are the core's own business and keep the subscription's
 * URLs — the app has no override point for them.
 */
object TestSource {
    /** mihomo's built-in default — what every test used before this preference existed. */
    const val DEFAULT_URL = "https://www.gstatic.com/generate_204"

    const val GOOGLE_URL = "https://www.google.com/generate_204"
    const val CLOUDFLARE_URL = "https://cp.cloudflare.com/generate_204"

    val PRESET_URLS: List<String> = listOf(DEFAULT_URL, GOOGLE_URL, CLOUDFLARE_URL)

    /** The URL tests should use for a stored preference value. */
    fun effectiveUrl(stored: String): String =
        stored.trim().ifEmpty { DEFAULT_URL }

    fun isCustom(stored: String): Boolean =
        stored.isNotEmpty() && stored !in PRESET_URLS

    /** A custom URL is only accepted as an http(s) address. */
    fun isValidCustom(url: String): Boolean {
        val trimmed = url.trim()

        return (trimmed.startsWith("http://") || trimmed.startsWith("https://")) &&
            trimmed.length > 10 && !trimmed.contains(' ')
    }

    /** Short label for a custom URL: its host. */
    fun hostLabel(url: String): String =
        url.substringAfter("://", url).substringBefore('/').substringBefore(':')
            .ifEmpty { url }
}
