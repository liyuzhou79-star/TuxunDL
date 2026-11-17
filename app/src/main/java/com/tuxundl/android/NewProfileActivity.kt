package com.tuxundl.android

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Surface
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.tuxundl.android.common.constants.Intents
import com.tuxundl.android.common.util.intent
import com.tuxundl.android.common.util.setUUID
import com.tuxundl.android.design.NewProfileDesign
import com.tuxundl.android.design.R
import com.tuxundl.android.design.model.DarkMode
import com.tuxundl.android.design.model.ProfileProvider
import com.tuxundl.android.service.model.Profile
import com.tuxundl.android.ui.NewProfileScreen
import com.tuxundl.android.ui.NewProfileUiState
import com.tuxundl.android.ui.theme.TuxunDLTheme
import com.tuxundl.android.ui.theme.customSeedColor
import com.tuxundl.android.util.withProfile
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.QRResult.QRError
import io.github.g00fy2.quickie.QRResult.QRMissingPermission
import io.github.g00fy2.quickie.QRResult.QRSuccess
import io.github.g00fy2.quickie.QRResult.QRUserCanceled
import io.github.g00fy2.quickie.ScanQRCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.util.*

class NewProfileActivity : BaseActivity<NewProfileDesign>() {
    private val self: NewProfileActivity
        get() = this

    private val uiState = NewProfileUiState()
    private val uiRequests = Channel<NewProfileDesign.Request>(Channel.UNLIMITED)

    private val scanLauncher = registerForActivityResult(ScanQRCode(), ::scanResultHandler)

    private val albumLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            decodeQrFromImage(uri)
        }
    }

    override suspend fun main() {
        uiState.providers = queryProfileProviders()

        while (isActive) {
            select<Unit> {
                events.onReceive {

                }
                uiRequests.onReceive {
                    when (it) {
                        is NewProfileDesign.Request.Create -> {
                            withProfile {
                                val name = getString(R.string.new_profile)

                                val uuid: UUID? = when (val p = it.provider) {
                                    is ProfileProvider.File ->
                                        create(Profile.Type.File, name)

                                    is ProfileProvider.Url ->
                                        create(Profile.Type.Url, name)

                                    is ProfileProvider.QR -> {
                                        null
                                    }

                                    is ProfileProvider.External -> {
                                        val data = p.get()

                                        if (data != null) {
                                            val (uri, initialName) = data

                                            create(
                                                Profile.Type.External,
                                                initialName ?: name,
                                                uri.toString()
                                            )
                                        } else {
                                            null
                                        }
                                    }
                                }

                                if (uuid != null)
                                    launchProperties(uuid)
                            }
                        }

                        is NewProfileDesign.Request.OpenDetail -> {
                            launchAppDetailed(it.provider)
                        }

                        is NewProfileDesign.Request.LaunchScanner -> {
                            scanLauncher.launch(null)
                        }

                        NewProfileDesign.Request.ScanFromAlbum -> {
                            albumLauncher.launch("image/*")
                        }
                    }
                }
            }
        }
    }

    private fun launchAppDetailed(provider: ProfileProvider.External) {
        val data = Uri.fromParts(
            "package",
            provider.intent.component?.packageName ?: return,
            null
        )

        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(data))
    }

    private suspend fun launchProperties(uuid: UUID) {
        val r = startActivityForResult(
            ActivityResultContracts.StartActivityForResult(),
            PropertiesActivity::class.intent.setUUID(uuid)
        )

        if (r.resultCode == Activity.RESULT_OK)
            finish()
    }

    private suspend fun ProfileProvider.External.get(): Pair<Uri, String?>? {
        val result = startActivityForResult(
            ActivityResultContracts.StartActivityForResult(),
            intent
        )

        if (result.resultCode != RESULT_OK)
            return null

        val uri = result.data?.data
        val name = result.data?.getStringExtra(Intents.EXTRA_NAME)

        if (uri != null) {
            return uri to name
        }

        return null
    }

    private suspend fun queryProfileProviders(): List<ProfileProvider> {
        return withContext(Dispatchers.IO) {
            val providers = packageManager.queryIntentActivities(
                Intent(Intents.ACTION_PROVIDE_URL),
                0
            ).map {
                val activity = it.activityInfo

                val name = activity.applicationInfo.loadLabel(packageManager)
                val summary = activity.loadLabel(packageManager)
                val icon = activity.loadIcon(packageManager)
                val intent = Intent(Intents.ACTION_PROVIDE_URL)
                    .setComponent(
                        ComponentName(
                            activity.packageName,
                            activity.name
                        )
                    )

                ProfileProvider.External(name.toString(), summary.toString(), icon, intent)
            }

            listOf(
                ProfileProvider.File(self),
                ProfileProvider.Url(self),
                ProfileProvider.QR(self)
            ) + providers
        }
    }

    private fun scanResultHandler(result: QRResult) {
        lifecycleScope.launch {
            when (result) {
                is QRSuccess -> {
                    val url = result.content.rawValue
                        ?: result.content.rawBytes?.let { String(it) }.orEmpty()

                    createProfileByQrCode(url)
                }

                QRUserCanceled -> {}
                QRMissingPermission -> uiState.snackbarHostState.showSnackbar(
                    message = getString(R.string.import_from_qr_no_permission),
                    duration = SnackbarDuration.Long,
                )
                is QRError -> uiState.snackbarHostState.showSnackbar(
                    message = getString(R.string.import_from_qr_exception),
                    duration = SnackbarDuration.Long,
                )
            }
        }
    }

    private suspend fun createProfileByQrCode(url: String) {
        withProfile {
            launchProperties(
                create(
                    type = Profile.Type.Url,
                    name = getString(R.string.new_profile),
                    url,
                )
            )
        }
    }

    /**
     * Decode a QR code from an album image with the same ML Kit barcode
     * scanner the camera flow (quickie) uses, then follow the exact same
     * success path as a camera scan.
     */
    private fun decodeQrFromImage(uri: Uri) {
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching { loadDownscaledBitmap(uri) }.getOrNull()
            }

            if (bitmap == null) {
                uiState.snackbarHostState.showSnackbar(
                    message = getString(com.tuxundl.android.R.string.import_from_album_failed),
                    duration = SnackbarDuration.Long,
                )
                return@launch
            }

            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()

            BarcodeScanning.getClient(options)
                .process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { barcodes ->
                    val value = barcodes.firstOrNull()?.rawValue

                    lifecycleScope.launch {
                        if (value != null) {
                            createProfileByQrCode(value)
                        } else {
                            uiState.snackbarHostState.showSnackbar(
                                message = getString(com.tuxundl.android.R.string.import_from_album_failed),
                                duration = SnackbarDuration.Long,
                            )
                        }
                    }
                }
                .addOnFailureListener {
                    lifecycleScope.launch {
                        uiState.snackbarHostState.showSnackbar(
                            message = getString(com.tuxundl.android.R.string.import_from_album_failed),
                            duration = SnackbarDuration.Long,
                        )
                    }
                }
        }
    }

    /** Load the picked image, downscaled so ML Kit stays fast on huge photos. */
    private fun loadDownscaledBitmap(uri: Uri): Bitmap {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri))
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(contentResolver, uri)
        }

        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= 2048) return bitmap

        val scale = 2048f / maxSide

        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
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
                    NewProfileScreen(
                        state = uiState,
                        onRequest = { request -> uiRequests.trySend(request) },
                        onBack = { finish() },
                    )
                }
            }
        }
    }
}
