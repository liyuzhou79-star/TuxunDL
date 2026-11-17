package com.tuxundl.android.service.util

import android.content.Context
import com.tuxundl.android.common.log.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * One user-defined routing rule from the visual rule editor.
 *
 * Rules are stored per profile as a JSON array in
 * `filesDir/custom-rules/<uuid>.json`. The editor (app process) writes the
 * file; [injectCustomRules] (background process, see ConfigurationModule)
 * reads it right before the core loads the profile and splices the enabled
 * rules into the top of the profile's `rules:` section. A file is used
 * instead of SharedPreferences because the two processes cannot reliably
 * share preferences.
 */
data class CustomRule(
    val type: String,
    val payload: String,
    val target: String,
    val noResolve: Boolean = false,
    val enabled: Boolean = true,
) {
    /** One mihomo rule line, without the leading "- ". */
    fun toRuleText(): String {
        val cleanPayload = payload.trim().replace("\n", "")
        val cleanTarget = target.trim().replace("\n", "")

        return if (type == "MATCH") {
            "MATCH,$cleanTarget"
        } else {
            val base = "$type,$cleanPayload,$cleanTarget"

            if (noResolve) "$base,no-resolve" else base
        }
    }

    companion object {
        /** Rule types offered by the editor, in display order. */
        val TYPES = listOf(
            "DOMAIN",
            "DOMAIN-SUFFIX",
            "DOMAIN-KEYWORD",
            "IP-CIDR",
            "IP-CIDR6",
            "GEOIP",
            "GEOSITE",
            "DST-PORT",
            "PROCESS-NAME",
            "MATCH",
        )

        /** Types where the no-resolve flag is meaningful. */
        val NO_RESOLVE_TYPES = setOf("IP-CIDR", "IP-CIDR6", "GEOIP")
    }
}

fun Context.customRulesDir(): File = filesDir.resolve("custom-rules")

fun Context.customRulesFile(uuid: UUID): File =
    customRulesDir().resolve("$uuid.json")

/** Tolerant read: a missing or broken file simply means "no rules". */
fun loadCustomRules(file: File): List<CustomRule> {
    if (!file.exists()) return emptyList()

    return try {
        val array = JSONArray(file.readText())

        List(array.length()) { index ->
            val item = array.getJSONObject(index)

            CustomRule(
                type = item.optString("type", "DOMAIN"),
                payload = item.optString("payload", ""),
                target = item.optString("target", "DIRECT"),
                noResolve = item.optBoolean("noResolve", false),
                enabled = item.optBoolean("enabled", true),
            )
        }
    } catch (e: Exception) {
        Log.w("Read custom rules ${file.name} failed: $e", e)

        emptyList()
    }
}

fun saveCustomRules(file: File, rules: List<CustomRule>) {
    val array = JSONArray()

    rules.forEach { rule ->
        array.put(
            JSONObject()
                .put("type", rule.type)
                .put("payload", rule.payload)
                .put("target", rule.target)
                .put("noResolve", rule.noResolve)
                .put("enabled", rule.enabled)
        )
    }

    file.parentFile?.mkdirs()
    file.writeText(array.toString())
}

private const val CUSTOM_RULES_BEGIN = "# TuxunDL-CUSTOM-RULES-BEGIN"
private const val CUSTOM_RULES_END = "# TuxunDL-CUSTOM-RULES-END"

/**
 * Splice the enabled custom rules of [uuid] into `config.yaml` inside
 * [profileDir], right under the top-level `rules:` key so they win over the
 * profile's own rules. The injected block is wrapped in marker comments and
 * any previously injected block is removed first, so repeated loads never
 * stack duplicates.
 *
 * Must never break profile loading: every failure is logged and the
 * original file is loaded as-is.
 */
fun injectCustomRules(context: Context, profileDir: File, uuid: UUID) {
    try {
        val configFile = profileDir.resolve("config.yaml")

        if (!configFile.exists()) return

        val rules = loadCustomRules(context.customRulesFile(uuid))
            .filter { it.enabled }

        val original = configFile.readText()
        val lines = original.split("\n")
            .map { it.trimEnd('\r') }
            .toMutableList()

        // Drop the trailing empty entry produced by a final newline; the
        // file is re-terminated when written back.
        if (lines.isNotEmpty() && lines.last().isEmpty()) {
            lines.removeAt(lines.lastIndex)
        }

        stripInjectedBlock(lines)

        if (rules.isNotEmpty()) {
            val block = mutableListOf(CUSTOM_RULES_BEGIN)
            rules.mapTo(block) { "  - " + it.toRuleText() }
            block += CUSTOM_RULES_END

            insertRulesBlock(lines, block)
        }

        val updated = lines.joinToString("\n") + "\n"

        if (updated != original) {
            configFile.writeText(updated)
        }
    } catch (e: Exception) {
        Log.w("Inject custom rules for $uuid failed: $e", e)
    }
}

/** Remove a previously injected block (markers inclusive), if present. */
private fun stripInjectedBlock(lines: MutableList<String>) {
    val begin = lines.indexOfFirst { it.trim() == CUSTOM_RULES_BEGIN }

    if (begin < 0) return

    var end = -1
    for (i in begin until lines.size) {
        if (lines[i].trim() == CUSTOM_RULES_END) {
            end = i
            break
        }
        // Only rule lines may sit between the markers; anything else means
        // the block is malformed, so stop at the marker line itself.
        if (i > begin && !lines[i].startsWith("  -")) break
    }

    val last = if (end >= 0) end else begin
    lines.subList(begin, last + 1).clear()
}

private val RULES_KEY = Regex("""^rules\s*:.*$""")
private val RULES_EMPTY_FLOW = Regex("""^rules\s*:\s*\[\s*]\s*$""")
private val RULES_FLOW = Regex("""^rules\s*:\s*\[.*$""")

/** Insert [block] at the top of the top-level `rules:` section. */
private fun insertRulesBlock(lines: MutableList<String>, block: List<String>) {
    val index = lines.indexOfFirst { RULES_KEY.matches(it) }

    when {
        index < 0 -> {
            // No rules section at all: append one at the end of the file.
            lines += "rules:"
            lines += block
        }
        RULES_EMPTY_FLOW.matches(lines[index]) -> {
            lines[index] = "rules:"
            lines.addAll(index + 1, block)
        }
        RULES_FLOW.matches(lines[index]) -> {
            // A non-empty flow-style list cannot take block entries;
            // leave the profile untouched rather than corrupting it.
            Log.w("Custom rules skipped: profile uses a flow-style rules list")
        }
        else -> {
            lines.addAll(index + 1, block)
        }
    }
}
