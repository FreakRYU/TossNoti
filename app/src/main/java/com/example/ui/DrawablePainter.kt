package com.example.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Render any Android Drawable as a Compose-friendly ImageBitmap.
 *
 * We render at a fixed size (96x96) which matches the icon slot in the app
 * picker. This avoids per-recompose redraws of adaptive/vector drawables.
 */
@Composable
fun rememberDrawableBitmap(drawable: Drawable, sizePx: Int = 96): ImageBitmap {
    return remember(drawable, sizePx) {
        val bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
            drawable.bitmap
        } else {
            val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: sizePx
            val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: sizePx
            val targetW = sizePx
            val targetH = (h.toFloat() / w.toFloat() * sizePx).toInt().coerceAtLeast(1)
            val bm = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bm)
            drawable.setBounds(0, 0, targetW, targetH)
            drawable.draw(canvas)
            bm
        }
        bitmap.asImageBitmap()
    }
}
