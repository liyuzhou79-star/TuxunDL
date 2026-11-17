package com.tuxundl.android.service.util

import android.content.Context
import com.tuxundl.android.common.log.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * User-defined DNS override.
 *
 * Stored as one JSON object in `filesDir/custom-dns.json` (a file, not
 * SharedPreferences, because the settings UI runs in the app process while
 * [injectCustomDns] runs in the :background process — same pattern as the
 * custom rules). When enabled, the nameserver/fallback lists replace the
 * profile's own `dns:` entries right before the core loads the profile.
 */
data class CustomDnsSettings(
    val enabled: Boolean = false,
    val nameservers: List<String> = emptyList(),
    val fallback: List<String> = emptyList(),
)

fun Context.customDnsFile(): File = filesDir.resolve("custom-dns.json")

/** Tolerant read: a missing or broken file simply means "no override". */
fun loadCustomDns(file: File): CustomDnsSettings {
    if (!file.exists()) return CustomDnsSettings()

    return try {
        val item = JSONObject(file.readText())

        CustomDnsSettings(
            enabled = item.optBoolean("enabled", false),
            nameservers = item.optJSONArray("nameserver").toStringList(),
            fallback = item.optJSONArray("fallback").toStringList(),
        )
    } catch (e: Exception) {
        Log.w("Read custom DNS ${file.name} failed: $e", e)

        CustomDnsSettings()
    }
}

fun saveCustomDns(file: File, settings: CustomDnsSettings) {
    val item = JSONObject()
        .put("enabled", settings.enabled)
        .put("nameserver", JSONArray(settings.nameservers))
        .put("fallback", JSONArray(settings.fallback))

    file.parentFile?.mkdirs()
    file.writeText(item.toString())
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()

    return List(length()) { index -> optString(index, "") }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

private const val CUSTOM_DNS_BEGIN = "# TuxunDL-CUSTOM-DNS-BEGIN"
private const val CUSTOM_DNS_END = "# TuxunDL-CUSTOM-DNS-END"
private const val DNS_OVERRIDDEN_PREFIX = "# TuxunDL-DNS-OVERRIDDEN "
private const val DNS_SECTION_MARK = "TuxunDL-DNS-SECTION"

private val DNS_KEY = Regex("""^dns\s*:.*$""")
private val DNS_KEY_LINE = Regex("""^dns\s*:\s*(#.*)?$""")

/**
 * Splice the custom DNS settings into `config.yaml` inside [profileDir].
 *
 * The managed keys (enable / nameserver / fallback) are written into the
 * profile's top-level `dns:` section inside a marker-wrapped block placed
 * right under the `dns:` line. Pre-existing entries for those keys are
 * commented out with an OVERRIDDEN marker instead of being deleted, and
 * any earlier injection is stripped (and the originals restored) before a
 * new one is applied, so repeated loads never stack duplicates and turning
 * the feature off restores the profile exactly. A profile without a `dns:`
 * section gets one appended (marked, and removed again when it ends up
 * empty). Flow-style `dns: {...}` sections are left untouched.
 *
 * Must never break profile loading: every failure is logged and the
 * original file is loaded as-is.
 */
fun injectCustomDns(context: Context, profileDir: File) {
    try {
        val configFile = profileDir.resolve("config.yaml")

        if (!configFile.exists()) return

        val settings = loadCustomDns(context.customDnsFile())

        val original = configFile.readText()
        val lines = original.split("\n")
            .map { it.trimEnd('\r') }
            .toMutableList()

        if (lines.isNotEmpty() && lines.last().isEmpty()) {
            lines.removeAt(lines.lastIndex)
        }

        stripCustomDns(lines)

        val nameservers = settings.nameservers.sanitized()
        val fallback = settings.fallback.sanitized()

        if (settings.enabled && (nameservers.isNotEmpty() || fallback.isNotEmpty())) {
            insertDnsBlock(lines, nameservers, fallback)
        }

        val updated = lines.joinToString("\n") + "\n"

        if (updated != original) {
            configFile.writeText(updated)
        }
    } catch (e: Exception) {
        Log.w("Inject custom DNS failed: $e", e)
    }
}

/** Entries must stay single YAML scalars: no whitespace, no comment starts. */
private fun List<String>.sanitized(): List<String> =
    map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && it.none { c -> c.isWhitespace() } }

/** Remove a previously injected block and restore overridden lines. */
private fun stripCustomDns(lines: MutableList<String>) {
    val begin = lines.indexOfFirst { it.trim() == CUSTOM_DNS_BEGIN }

    if (begin >= 0) {
        var end = -1
        for (i in begin until lines.size) {
            if (lines[i].trim() == CUSTOM_DNS_END) {
                end = i
                break
            }
            // Only indented lines may sit between the markers.
            if (i > begin && lines[i].isNotEmpty() && !lines[i].startsWith(" ")) break
        }

        val last = if (end >= 0) end else begin
        lines.subList(begin, last + 1).clear()
    }

    for (i in lines.indices) {
        val line = lines[i]
        val marker = line.indexOf(DNS_OVERRIDDEN_PREFIX)

        if (marker >= 0 && line.trimStart().startsWith("#")) {
            lines[i] = line.substring(marker + DNS_OVERRIDDEN_PREFIX.length)
        }
    }

    // If we created the dns: section ourselves and it is empty again,
    // drop its header line as well.
    val header = lines.indexOfFirst { DNS_KEY.matches(it) && it.contains(DNS_SECTION_MARK) }

    if (header >= 0) {
        var hasChild = false
        for (i in header + 1 until lines.size) {
            val line = lines[i]
            if (line.isBlank() || line.startsWith("#")) continue
            if (line.startsWith(" ")) {
                hasChild = true
            }
            break
        }

        if (!hasChild) {
            lines.removeAt(header)
        }
    }
}

/** Index just past the end of the section starting at [start] (the key line). */
private fun sectionEnd(lines: List<String>, start: Int): Int {
    for (i in start + 1 until lines.size) {
        val line = lines[i]
        if (line.isNotBlank() && !line.startsWith(" ") && !line.startsWith("#")) {
            return i
        }
    }

    return lines.size
}

private fun insertDnsBlock(
    lines: MutableList<String>,
    nameservers: List<String>,
    fallback: List<String>,
) {
    var dnsIndex = lines.indexOfFirst { DNS_KEY.matches(it) }

    if (dnsIndex >= 0 && !DNS_KEY_LINE.matches(lines[dnsIndex])) {
        // dns: has an inline value (e.g. flow-style mapping); splicing
        // block entries into it would corrupt the file, so leave it alone.
        Log.w("Custom DNS skipped: profile uses a flow-style dns section")
        return
    }

    if (dnsIndex < 0) {
        lines += "dns: # $DNS_SECTION_MARK"
        dnsIndex = lines.lastIndex
    }

    val end = sectionEnd(lines, dnsIndex)

    // Child indent: learn it from the first indented child, default 2.
    var childIndent = 2
    for (i in dnsIndex + 1 until end) {
        if (lines[i].startsWith(" ") && lines[i].isNotBlank()) {
            childIndent = lines[i].length - lines[i].trimStart().length
            break
        }
    }

    val pad = " ".repeat(childIndent)
    val keyName = Regex("""(enable|nameserver|fallback)\s*:.*""")

    fun isManagedKey(line: String): Boolean =
        line.startsWith(pad) &&
            !line.substring(childIndent).startsWith(" ") &&
            keyName.matches(line.substring(childIndent))

    // Comment out the profile's own managed keys (and their list items)
    // so the injected block below is the only definition left.
    var i = dnsIndex + 1
    while (i < end) {
        if (isManagedKey(lines[i])) {
            val keyIndent = lines[i].length - lines[i].trimStart().length
            lines[i] = DNS_OVERRIDDEN_PREFIX + lines[i]
            i++

            // List items / continuation lines of this key sit deeper
            // (or are same-indent sequence entries).
            while (i < end) {
                val item = lines[i]
                if (item.isBlank()) {
                    i++
                    continue
                }
                val itemIndent = item.length - item.trimStart().length
                if (itemIndent > keyIndent || (itemIndent >= keyIndent && item.trimStart().startsWith("-"))) {
                    lines[i] = DNS_OVERRIDDEN_PREFIX + item
                    i++
                } else {
                    break
                }
            }
        } else {
            i++
        }
    }

    val block = mutableListOf(pad + CUSTOM_DNS_BEGIN)
    block += pad + "enable: true"
    if (nameservers.isNotEmpty()) {
        block += pad + "nameserver:"
        nameservers.mapTo(block) { "$pad  - $it" }
    }
    if (fallback.isNotEmpty()) {
        block += pad + "fallback:"
        fallback.mapTo(block) { "$pad  - $it" }
    }
    block += pad + CUSTOM_DNS_END

    lines.addAll(dnsIndex + 1, block)
}
