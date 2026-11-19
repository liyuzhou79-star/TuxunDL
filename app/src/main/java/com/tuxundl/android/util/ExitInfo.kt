package com.tuxundl.android.util

import com.tuxundl.android.common.log.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * One exit-IP lookup result: the public IP the current node egresses
 * from, and where that IP geolocates to.
 */
data class ExitInfo(
    val ip: String,
    val country: String,
    val city: String,
    val countryCode: String,
)

/**
 * Exit-IP lookup for the home screen's exit info card.
 *
 * The request is a plain HTTPS call from the app process. That is
 * tunnel-routed by construction: TunService always keeps this app
 * inside its own tunnel (the AcceptSelected mode adds the app's own
 * package to the allowed list, DenySelected never disallows it), so
 * the answer describes the current node's exit, not the device's
 * real IP.
 *
 * Two keyless JSON endpoints are tried in order; any failure yields
 * null and the card honestly shows "exit IP unknown" instead of a
 * guessed location. Blocking — call on an IO dispatcher.
 */
object ExitInfoLookup {
    private const val TIMEOUT_MS = 8_000

    fun fetch(): ExitInfo? = fetchIpwho() ?: fetchIpapi()

    private fun fetchIpwho(): ExitInfo? = try {
        val json = getJson("https://ipwho.is/")

        if (json != null && json.optBoolean("success", false)) {
            val ip = json.optString("ip")

            if (ip.isNotBlank()) {
                ExitInfo(
                    ip = ip,
                    country = json.optString("country"),
                    city = json.optString("city"),
                    countryCode = json.optString("country_code"),
                )
            } else {
                null
            }
        } else {
            null
        }
    } catch (e: Exception) {
        Log.w("Exit lookup (ipwho) failed", e)
        null
    }

    private fun fetchIpapi(): ExitInfo? = try {
        val json = getJson("https://ipapi.co/json/")
        val ip = json?.optString("ip").orEmpty()

        if (json != null && ip.isNotBlank()) {
            ExitInfo(
                ip = ip,
                country = json.optString("country_name"),
                city = json.optString("city"),
                countryCode = json.optString("country_code"),
            )
        } else {
            null
        }
    } catch (e: Exception) {
        Log.w("Exit lookup (ipapi) failed", e)
        null
    }

    private fun getJson(url: String): JSONObject? {
        val connection = URL(url).openConnection() as HttpURLConnection

        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                null
            } else {
                JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            }
        } finally {
            connection.disconnect()
        }
    }
}

/** Regional-indicator flag emoji for an ISO 3166-1 alpha-2 code; "" when unusable. */
fun countryFlagEmoji(countryCode: String): String {
    val code = countryCode.trim().uppercase()

    if (code.length != 2 || !code.all { it in 'A'..'Z' }) return ""

    val base = 0x1F1E6

    return String(Character.toChars(base + (code[0] - 'A'))) +
        String(Character.toChars(base + (code[1] - 'A')))
}
