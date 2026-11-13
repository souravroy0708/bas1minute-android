package com.bas1minute.sync.goal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The one photo a life goal can carry — a parent, a child, a grandparent, whoever or whatever it's for.
 * Kept at a modest size in the app's own private storage, in its original aspect ratio (unlike the square
 * profile photo, these are personal pictures the person chose on purpose; cropping them felt wrong).
 */
object LifeGoalPhoto {
    private const val MAX_SIDE = 1024

    /** Bumps to the goal id whenever a photo is saved, so an open screen knows to re-read it. */
    val saved = MutableStateFlow(0L)

    private fun dir(context: Context) = File(context.filesDir, "goal_photos").apply { mkdirs() }
    private fun file(context: Context, goalId: Long) = File(dir(context), "$goalId.jpg")

    fun save(context: Context, goalId: Long, uri: Uri): Boolean = try {
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
        file(context, goalId).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        saved.value = goalId
        true
    } catch (e: Exception) {
        false
    }

    fun path(context: Context, goalId: Long): String? = file(context, goalId).takeIf { it.exists() }?.path

    fun load(context: Context, goalId: Long): Bitmap? = file(context, goalId).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    fun remove(context: Context, goalId: Long) { file(context, goalId).delete() }

    /** Called when the account is cleared (logout / delete account): removes every saved photo. */
    fun clearAll(context: Context) { dir(context).listFiles()?.forEach { it.delete() } }
}
