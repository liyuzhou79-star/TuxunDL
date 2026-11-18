package com.tuxundl.windows

/**
 * Turns a subscription profile (a mihomo YAML config, the same format
 * the Android edition consumes) into the config the Windows core runs
 * with: this app owns the ports and the controller address, and TUN
 * mode adds its block when the profile does not define one itself.
 */
object ConfigFactory {

    fun build(
        profileYaml: String,
        mixedPort: Int = 7890,
        controllerPort: Int = 9090,
        tunMode: Boolean = false,
        logLevel: String = "info",
    ): String {
        var out = profileYaml
        out = overrideTopLevel(out, "mixed-port", mixedPort.toString())
        out = overrideTopLevel(out, "external-controller", "127.0.0.1:$controllerPort")
        out = overrideTopLevel(out, "allow-lan", "false")
        out = overrideTopLevel(out, "log-level", logLevel)

        if (tunMode && !hasTopLevel(out, "tun")) {
            out = out.trimEnd() + "\n\n" + TUN_BLOCK
        }

        return out
    }

    private fun hasTopLevel(yaml: String, key: String): Boolean =
        yaml.lineSequence().any { it.startsWith("$key:") }

    /**
     * Replace a top-level `key:` line when present, otherwise prepend
     * the key. Only column-0 keys are touched, so nested profile
     * content is never rewritten.
     */
    private fun overrideTopLevel(yaml: String, key: String, value: String): String {
        val lines = yaml.lines()
        val index = lines.indexOfFirst { it.startsWith("$key:") }
        return if (index >= 0) {
            lines.toMutableList()
                .also { it[index] = "$key: $value" }
                .joinToString("\n")
        } else {
            "$key: $value\n$yaml"
        }
    }

    // The core runs elevated in TUN mode (see CoreBridge); the system
    // stack uses the bundled wintun.dll.
    private const val TUN_BLOCK = """tun:
  enable: true
  stack: system
  dns-hijack:
    - any:53
  auto-route: true
  auto-detect-interface: true
"""
}
