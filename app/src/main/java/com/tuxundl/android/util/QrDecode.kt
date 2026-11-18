package com.tuxundl.android.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decode the first QR code found in an image picked from the album,
 * using the same ML Kit barcode scanner as the camera flow (quickie
 * bundles it). Returns the QR payload, or null when the image cannot
 * be read or holds no QR code. Extracted from NewProfileActivity in
 * 1.4.0 so the onboarding import shares the exact same code path.
 */
suspend fun decodeQrFromImage(context: Context, uri: Uri): String? =
    withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = loadDownscaledBitmap(context, uri) ?: return@runCatching null

            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()

            val barcodes = Tasks.await(
                BarcodeScanning.getClient(options)
                    .process(InputImage.fromBitmap(bitmap, 0)),
            )

            barcodes.firstOrNull()?.rawValue
        }.getOrNull()
    }

/** Load the picked image, downscaled so ML Kit stays fast on huge photos. */
private fun loadDownscaledBitmap(context: Context, uri: Uri): Bitmap? {
    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(
            ImageDecoder.createSource(context.contentResolver, uri),
        )
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
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
