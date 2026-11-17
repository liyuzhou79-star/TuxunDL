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
                                try {
                                    queryAll().forEach { p ->
                                        if (p.imported && p.type != Profile.Type.File)
                                            update(p.uuid)
                                    }
                                }
                                finally {
                                    uiState.allUpdating = false
                                }
                            }
                        is ProfilesDesign.Request.Update ->
                            withProfile { update(it.profile.uuid) }
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
        }
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
        launch {
            var name: String? = null
            withProfile {
                name = queryByUUID(uuid)?.name
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
                        onBack = { finish() },
                    )
                }
            }
        }
    }
}
