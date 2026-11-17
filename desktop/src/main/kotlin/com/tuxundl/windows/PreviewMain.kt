package com.tuxundl.windows

import androidx.compose.ui.ImageComposeScene
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/**
 * Renders the home page off-screen to a PNG, without opening a window.
 * Used by the preview-windows-ui workflow so the current state of the
 * UI can be checked on any machine, no Windows PC needed. This entry
 * point is a development tool and is not part of the shipped app.
 */
fun main(args: Array<String>) {
    val out = File(args.getOrElse(0) { "tuxundl-home.png" })
    val scene = ImageComposeScene(1100, 750) {
        App()
    }
    // Give the first frames a moment so fonts and layout settle.
    Thread.sleep(300)
    // ImageComposeScene renders to a Skia image; encode it as PNG
    // directly instead of going through AWT.
    val data = scene.render().encodeToData(EncodedImageFormat.PNG)
    check(data != null) { "failed to encode the preview PNG" }
    out.writeBytes(data.bytes)
    scene.close()
    println("wrote ${out.absolutePath}")
}
