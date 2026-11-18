package com.tuxundl.android

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.tuxundl.android.common.util.intent
import com.tuxundl.android.common.util.setUUID
import com.tuxundl.android.common.util.ticker
import com.tuxundl.android.design.ProfilesDesign
import com.tuxundl.android.design.model.DarkMode
import com.tuxundl.android.service.model.Profile
import com.tuxundl.android.ui.ProfilesScreen
import com.tuxundl.android.ui.ProfilesUiState
import com.tuxundl.android.ui.theme.TuxunDLTheme
import com.tuxundl.android.ui.theme.customSeedColor
import com.tuxundl.android.util.withProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.TimeUnit
import com.tuxundl.android.design.R

class ProfilesActivity : BaseActivity<ProfilesDesign>() {
    private val uiState = ProfilesUiState()
    private val uiRequests = Channel<ProfilesDesign.Request>(Channel.UNLIMITED)

    /**
     * "Update all" batch tracking (1.3.5): while a batch is running, the
     * per-profile callbacks feed these counters instead of popping an
     * individual snackbar for every profile; when the last one lands,
     * a single summary is shown.
     */
    private var updateAllPending: MutableSet<UUID>? = null
    private var updateAllSucceeded = 0
    private val updateAllFailedNames = mutableListOf<String>()

    /** Failed batch members whose display name is still being looked up. */
    private var updateAllNameLookups = 0

    override suspend fun main() {
        val ticker = ticker(TimeUnit.MINUTES.toMillis(1))

        fetch()

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ActivityStart, Event.ProfileChanged -> {
                            fetch()
                        }
                        else -> Unit
                    }
                }
                uiRequests.onReceive {
                    when (it) {
                        ProfilesDesign.Request.Create ->
                            startActivity(NewProfileActivity::class.intent)
                        ProfilesDesign.Request.UpdateAll ->
                            withProfile {
                                var targets = emptyList<Profile>()
                                try {
                                    targets = queryAll().filter { p ->
                                        p.imported && p.type != Profile.Type.File
                                    }
                                    uiState.updatingUuids =
                                        uiState.updatingUuids + targets.map { it.uuid }
                                    if (targets.isNotEmpty()) {
                                        updateAllPending =
                                            targets.map { it.uuid }.toMutableSet()
                                        updateAllSucceeded = 0
                                        updateAllFailedNames.clear()
                                        updateAllNameLookups = 0
                                    }
                                    targets.forEach { p -> update(p.uuid) }
                                } catch (e: Exception) {
                                    // Firing the updates failed midway:
                                    // abandon the batch so no summary is
                                    // ever shown for it.
                                    updateAllPending = null
                                    throw e
                                }
                                finally {
                                    uiState.allUpdating = false
                                    // Safety net: per-profile callbacks
                                    // normally clear these one by one.
                                    uiState.updatingUuids =
                                        uiState.updatingUuids - targets.map { it.uuid }.toSet()
                                }
                            }
                        is ProfilesDesign.Request.Update -> {
                            uiState.updatingUuids =
                                uiState.updatingUuids + it.profile.uuid
                            withProfile { update(it.profile.uuid) }
                        }
                        is ProfilesDesign.Request.Delete ->
                            withProfile { delete(it.profile.uuid) }
                        is ProfilesDesign.Request.Edit ->
                            startActivity(PropertiesActivity::class.intent.setUUID(it.profile.uuid))
                        is ProfilesDesign.Request.Active -> {
                            withProfile {
                                if (it.profile.imported)
                                    setActive(it.profile)
                                else
                                    requestSave(it.profile)
                            }
                        }
                        is ProfilesDesign.Request.Duplicate -> {
                            val uuid = withProfile { clone(it.profile.uuid) }

                            startActivity(PropertiesActivity::class.intent.setUUID(uuid))
                        }
                        is ProfilesDesign.Request.ShareQrCode ->
                            showProfileQrCode(it.profile)
                    }
                }
                if (activityStarted) {
                    ticker.onReceive {
                        uiState.now = System.currentTimeMillis()
                    }
                }
            }
        }
    }

    private suspend fun fetch() {
        withProfile {
            val profiles = queryAll()

            uiState.profiles = profiles
            uiState.updatable = profiles.any { it.imported && it.type != Profile.Type.File }
            uiState.pinnedUuids = uiStore.pinnedProfiles
        }
    }

    /** Pin/unpin a profile; pinned profiles sort to the top of the list. */
    private fun togglePin(profile: Profile) {
        val key = profile.uuid.toString()
        val updated = uiStore.pinnedProfiles.toMutableSet().apply {
            if (!add(key)) remove(key)
        }

        uiStore.pinnedProfiles = updated
        uiState.pinnedUuids = updated
    }

    private suspend fun requestSave(profile: Profile) {
        val result = uiState.snackbarHostState.showSnackbar(
            message = getString(R.string.active_unsaved_tips),
            actionLabel = getString(R.string.edit),
            duration = SnackbarDuration.Long,
        )

        if (result == SnackbarResult.ActionPerformed) {
            uiRequests.trySend(ProfilesDesign.Request.Edit(profile))
        }
    }

    private suspend fun showProfileQrCode(profile: Profile) {
        if (profile.type != Profile.Type.Url || profile.source.isBlank()) return

        val bitmap = try {
            withContext(Dispatchers.Default) {
                val matrix = QRCodeWriter().encode(
                    profile.source, BarcodeFormat.QR_CODE, 768, 768,
                    mapOf(EncodeHintType.CHARACTER_SET to "UTF-8")
                )
                val pixels = IntArray(matrix.width * matrix.height) { index ->
                    if (matrix[index % matrix.width, index / matrix.width]) Color.BLACK else Color.WHITE
                }
                Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
            }
        } catch (_: WriterException) {
            uiState.snackbarHostState.showSnackbar(
                message = getString(R.string.qr_code_generation_failed),
                duration = SnackbarDuration.Long,
            )
            return
        }

        uiState.qrBitmap = bitmap
        uiState.qrProfile = profile
    }

    override fun onProfileUpdateCompleted(uuid: UUID?) {
        if (uuid == null)
            return
        uiState.updatingUuids = uiState.updatingUuids - uuid

        if (updateAllPending?.remove(uuid) == true) {
            updateAllSucceeded++
            showUpdateAllSummaryIfDone()
            return
        }

        launch {
            var name: String? = null
            withProfile {
                name = queryByUUID(uuid)?.name
            }
            uiState.snackbarHostState.showSnackbar(
                message = getString(R.string.toast_profile_updated_complete, name),
                duration = SnackbarDuration.Long,
            )
        }
    }

    override fun onProfileUpdateFailed(uuid: UUID?, reason: String?) {
        if (uuid == null)
            return
        uiState.updatingUuids = uiState.updatingUuids - uuid

        val inBatch = updateAllPending?.remove(uuid) == true
        if (inBatch)
            updateAllNameLookups++

        launch {
            var name: String? = null
            withProfile {
                name = queryByUUID(uuid)?.name
            }

            if (inBatch) {
                updateAllFailedNames += name ?: uuid.toString()
                updateAllNameLookups--
                showUpdateAllSummaryIfDone()
                return@launch
            }

            val result = uiState.snackbarHostState.showSnackbar(
                message = getString(R.string.toast_profile_updated_failed, name, reason),
                actionLabel = getString(R.string.edit),
                duration = SnackbarDuration.Long,
            )

            if (result == SnackbarResult.ActionPerformed) {
                startActivity(PropertiesActivity::class.intent.setUUID(uuid))
            }
        }
    }

    /**
     * Shows the one-line "update all" summary once every batched
     * profile has reported back; called from both update callbacks.
     * Batch members never pop individual snackbars, so this is the
     * only feedback a batch produces.
     */
    private fun showUpdateAllSummaryIfDone() {
        val pending = updateAllPending ?: return
        if (pending.isNotEmpty() || updateAllNameLookups > 0)
            return

        updateAllPending = null
        val succeeded = updateAllSucceeded
        val failedNames = updateAllFailedNames.toList()
        updateAllSucceeded = 0
        updateAllFailedNames.clear()

        launch {
            val message = if (failedNames.isEmpty()) {
                getString(R.string.toast_update_all_done, succeeded)
            } else {
                getString(
                    R.string.toast_update_all_summary,
                    succeeded,
                    failedNames.size,
                    failedNames.joinToString(", "),
                )
            }
            uiState.snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Long,
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val darkTheme = when (uiStore.darkMode) {
            DarkMode.Auto -> resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            DarkMode.ForceLight -> false
            DarkMode.ForceDark -> true
        }

        setContent {
            TuxunDLTheme(darkTheme = darkTheme, palette = uiStore.themePalette, customSeed = customSeedColor(uiStore), animStyle = uiStore.animationStyle) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ProfilesScreen(
                        state = uiState,
                        onRequest = { request -> uiRequests.trySend(request) },
                        onTogglePin = { profile -> togglePin(profile) },
                        onBack = { finish() },
                    )
                }
            }
        }
    }
}
