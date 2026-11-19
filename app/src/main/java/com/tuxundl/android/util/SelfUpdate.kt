package com.tuxundl.android.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app update downloads (1.6.1).
 *
 * Before 1.6.1 the update page handed the APK link to the browser:
 * the user left the app, downloaded from the GitHub release page and
 * installed the file by hand. Now the page can do the whole trip
 * itself — download the asset [UpdateChecker] already picked for
 * this device, verify it, and hand it to the system installer.
 *
 * Verification is two-layered, and both layers must pass before the
 * installer is ever offered:
 *
 *  1. Size: the downloaded byte count must equal the asset size the
 *     release list reported (when it reported one), so a truncated
 *     or substituted download is caught even before parsing.
 *  2. Signature: the APK's package name must be this app's own, and
 *     its signing certificates must equal the installed app's —
 *     compared dynamically through [PackageManager], never against
 *     a hardcoded fingerprint, so the check stays correct across
 *     signing-key rotations that the system itself would accept.
 *
 * Any failure lands in [State.Failed] and the page falls back to
 * the old browser route; nothing here ever installs silently — the
 * final install confirmation is the system's own dialog, as Android
 * requires.
 *
 * Like the update checker, the download runs on this object's own
 * scope: closing the update page must not kill a download the user
 * explicitly started, and reopening the page reattaches to the same
 * state.
 */
object SelfUpdate {

    /** Why a self-update attempt failed, in user-presentable terms. */
    enum class FailReason {
        /** The download never completed (offline, blocked, timed out). */
        NETWORK,

        /** The downloaded bytes do not match the reported asset size. */
        SIZE_MISMATCH,

        /**
         * The APK is not an update of this app: different package
         * name, or signed with different certificates.
         */
        SIGNATURE_MISMATCH,

        /** Anything else (I/O errors, unparseable APK, …). */
        OTHER,
    }

    sealed interface State {
        /** The version this state belongs to; null only for [Idle]. */
        val version: String?

        object Idle : State {
            override val version: String? = null
        }

        data class Downloading(
            override val version: String,
            val downloadedBytes: Long,
            /** Expected total bytes; -1 when the server did not say. */
            val totalBytes: Long,
        ) : State

        data class Verifying(override val version: String) : State

        data class Ready(
            override val version: String,
            val file: File,
        ) : State

        data class Failed(
            override val version: String,
            val reason: FailReason,
        ) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<State>(State.Idle)

    /** The one download state; the update page renders from this. */
    val state: StateFlow<State> = _state.asStateFlow()

    private var job: Job? = null

    /** True when the system will let this app start an install. */
    fun canInstall(context: Context): Boolean {
        // canRequestPackageInstalls exists from API 26; before that,
        // installing a downloaded APK goes through the legacy global
        // "unknown sources" switch and the installer handles it.
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()
    }

    /** Settings page where the user allows installs from this app. */
    fun unknownSourcesSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            )
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /**
     * Start (or reattach to) the download of [info]'s APK. A call
     * for a version already downloading or already downloaded is a
     * no-op; a call after a failure retries from scratch.
     */
    fun start(context: Context, info: UpdateInfo) {
        val current = _state.value

        if (current.version == info.version &&
            (current is State.Downloading ||
                current is State.Verifying ||
                current is State.Ready)
        ) {
            return
        }

        val url = info.downloadUrl
        if (url == null) {
            _state.value = State.Failed(info.version, FailReason.OTHER)
            return
        }

        val appContext = context.applicationContext

        job?.cancel()
        job = scope.launch {
            runDownload(appContext, info, url)
        }
    }

    /** Cancel an in-flight download and drop the partial file. */
    fun cancel() {
        job?.cancel()
        job = null

        val current = _state.value
        if (current is State.Downloading || current is State.Verifying) {
            _state.value = State.Idle
        }
    }

    /** Hand a verified APK to the system installer. */
    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(intent)
    }

    private suspend fun runDownload(
        context: Context,
        info: UpdateInfo,
        url: String,
    ) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "tuxundl-update-${info.version}.apk")
        val partial = File(dir, "tuxundl-update-${info.version}.apk.part")

        try {
            downloadTo(url, partial, info)
            currentCoroutineContext().ensureActive()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            partial.delete()
            _state.value = State.Failed(info.version, FailReason.NETWORK)
            return
        }

        _state.value = State.Verifying(info.version)

        // Layer 1: exact size, when the release reported one.
        if (info.downloadSize > 0 && partial.length() != info.downloadSize) {
            partial.delete()
            _state.value = State.Failed(info.version, FailReason.SIZE_MISMATCH)
            return
        }

        // Layer 2: the APK must be an update of this very app.
        if (!signatureMatches(context, partial)) {
            partial.delete()
            _state.value =
                State.Failed(info.version, FailReason.SIGNATURE_MISMATCH)
            return
        }

        if (target.exists()) target.delete()
        if (!partial.renameTo(target)) {
            partial.delete()
            _state.value = State.Failed(info.version, FailReason.OTHER)
            return
        }

        _state.value = State.Ready(info.version, target)
    }

    /**
     * GET [url] into [dest], following redirects by hand (GitHub
     * asset URLs redirect to storage hosts) and publishing progress
     * into [state] as it goes.
     */
    private suspend fun downloadTo(
        url: String,
        dest: File,
        info: UpdateInfo,
    ) {
        var currentUrl = url
        var redirects = 0

        while (true) {
            val connection = URL(currentUrl).openConnection() as HttpURLConnection

            try {
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", "TuxunDL-Android")

                val code = connection.responseCode

                if (code in 300..399) {
                    val location = connection.getHeaderField("Location")
                    if (location == null || ++redirects > 5) {
                        throw java.io.IOException("bad redirect")
                    }
                    currentUrl = URL(URL(currentUrl), location).toString()
                    continue
                }

                if (code != HttpURLConnection.HTTP_OK) {
                    throw java.io.IOException("HTTP $code")
                }

                val total = when {
                    info.downloadSize > 0 -> info.downloadSize
                    connection.contentLengthLong > 0 -> connection.contentLengthLong
                    else -> -1L
                }

                var downloaded = 0L
                var lastReport = 0L

                connection.inputStream.use { input ->
                    dest.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read

                            if (downloaded - lastReport >= 256 * 1024) {
                                lastReport = downloaded
                                // The read loop blocks on I/O, so
                                // cancellation is only observed here,
                                // at each progress checkpoint.
                                currentCoroutineContext().ensureActive()
                                _state.value = State.Downloading(
                                    info.version, downloaded, total,
                                )
                            }
                        }
                    }
                }

                _state.value = State.Downloading(info.version, downloaded, total)
                return
            } finally {
                connection.disconnect()
            }
        }
    }

    /**
     * True when [apk] parses, belongs to this app's package, and is
     * signed with exactly the installed app's certificates.
     */
    private fun signatureMatches(context: Context, apk: File): Boolean {
        val pm = context.packageManager

        val archive = packageArchiveInfo(pm, apk) ?: return false
        if (archive.packageName != context.packageName) return false

        val installed = try {
            packageInfoWithSigners(pm, context.packageName)
        } catch (e: Exception) {
            return false
        } ?: return false

        val apkSigners = signerBytes(archive)
        val installedSigners = signerBytes(installed)

        if (apkSigners.isEmpty() || installedSigners.isEmpty()) return false

        return apkSigners.map { it.toList() }.toSet() ==
            installedSigners.map { it.toList() }.toSet()
    }

    private fun packageArchiveInfo(pm: PackageManager, apk: File): PackageInfo? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageArchiveInfo(
                    apk.absolutePath,
                    PackageManager.GET_SIGNING_CERTIFICATES,
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(
                    apk.absolutePath,
                    PackageManager.GET_SIGNATURES,
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun packageInfoWithSigners(
        pm: PackageManager,
        packageName: String,
    ): PackageInfo? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
        }
    }

    private fun signerBytes(info: PackageInfo): List<ByteArray> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
                ?.map { it.toByteArray() } ?: emptyList()
        } else {
            @Suppress("DEPRECATION")
            info.signatures?.map { it.toByteArray() } ?: emptyList()
        }
    }
}
