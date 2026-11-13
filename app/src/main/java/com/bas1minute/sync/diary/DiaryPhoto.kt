package com.bas1minute.sync.diary

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow

/** The one photo a diary entry can carry, kept in the app's own private storage, original aspect ratio. */
object DiaryPhoto {
    private const val MAX_SIDE = 1024
    val saved = MutableStateFlow(0L)

    private fun dir(context: Context) = File(context.filesDir, "diary_photos").apply { mkdirs() }
    private fun file(context: Context, entryId: Long) = File(dir(context), "$entryId.jpg")

    fun save(context: Context, entryId: Long, uri: Uri): Boolean = try {
        val src: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
                val s = maxOf(info.size.width, info.size.height)
                if (s > MAX_SIDE * 2) d.setTargetSampleSize(s / (MAX_SIDE * 2))
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) } ?: error("unreadable")
        }
        val scale = MAX_SIDE.toFloat() / maxOf(src.width, src.height)
        val out = if (scale < 1f) Bitmap.createScaledBitmap(src, (src.width * scale).toInt().coerceAtLeast(1), (src.height * scale).toInt().coerceAtLeast(1), true) else src
        file(context, entryId).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        saved.value = entryId
        true
    } catch (e: Exception) {
        false
    }

    fun path(context: Context, entryId: Long): String? = file(context, entryId).takeIf { it.exists() }?.path
    fun remove(context: Context, entryId: Long) { file(context, entryId).delete() }
    fun clearAll(context: Context) { dir(context).listFiles()?.forEach { it.delete() } }
}
