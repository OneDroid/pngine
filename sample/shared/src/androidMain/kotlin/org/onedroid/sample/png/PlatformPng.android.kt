package org.onedroid.sample.png

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

actual fun platformPngBaseline(pixels: IntArray, width: Int, height: Int): ByteArray? {
    val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    return try {
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
    } finally {
        bitmap.recycle()
    }
}

actual val platformPngName: String = "Bitmap.compress(PNG)"
