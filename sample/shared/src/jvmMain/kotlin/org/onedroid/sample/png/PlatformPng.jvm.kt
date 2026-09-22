package org.onedroid.sample.png

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

actual fun platformPngBaseline(pixels: IntArray, width: Int, height: Int): ByteArray? {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    image.setRGB(0, 0, width, height, pixels, 0, width)
    return ByteArrayOutputStream().use { out ->
        ImageIO.write(image, "png", out)
        out.toByteArray()
    }
}

actual val platformPngName: String = "ImageIO PNG"
