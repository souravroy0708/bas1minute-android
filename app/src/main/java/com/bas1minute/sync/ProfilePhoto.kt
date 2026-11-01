package com.bas1minute.sync

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.File

/** Profile photo, kept only in the app's private storage on this phone (never uploaded). */
object ProfilePhoto {
    private const val MAX = 512

    private fun file(context: Context) = File(context.filesDir, "profile.jpg")

    /** Copy the picked image, cropped to a square and scaled down. False if it can't be read. */
    fun save(context: Context, uri: Uri): Boolean = try {
        val src: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
                val s = maxOf(info.size.width, info.size.height)
                if (s > MAX * 2) d.setTargetSampleSize(s / (MAX * 2))
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) } ?: error("unreadable")
        }
        val side = minOf(src.width, src.height)
        val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val out = Bitmap.createScaledBitmap(square, MAX, MAX, true)
        file(context).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        true
    } catch (e: Exception) {
        false
    }

    fun load(context: Context): Bitmap? = file(context).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    fun remove(context: Context) { file(context).delete() }
}
