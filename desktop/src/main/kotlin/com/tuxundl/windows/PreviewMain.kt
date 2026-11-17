package com.tuxundl.windows

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.toAwtImage
import java.io.File
import javax.imageio.ImageIO

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
    ImageIO.write(scene.render().toAwtImage(), "png", out)
    scene.close()
    println("wrote ${out.absolutePath}")
}
