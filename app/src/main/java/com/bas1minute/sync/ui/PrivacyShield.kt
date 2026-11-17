package com.bas1minute.sync.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import com.bas1minute.sync.Prefs

/*
 * Privacy shield for the most personal screens (Goals, Life goals, Diary).
 *
 * While one is open, Android's FLAG_SECURE is set on the window: the screen can't be screenshotted or screen-
 * recorded, can't be mirrored to a TV, and Recents shows a blank card instead of your diary. Members can turn
 * it off in Profile > Privacy (e.g. to screenshot a goal on purpose); it's ON by default.
 *
 * Counted, not a simple on/off: screens cross-fade, so the next private screen appears before the previous one
 * is disposed — a plain clear-on-dispose would drop the shield while a private screen is still showing.
 */
object PrivacyShield {
    private var holders = 0

    fun acquire(activity: Activity) {
        holders++
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    fun release(activity: Activity) {
        holders = (holders - 1).coerceAtLeast(0)
        if (holders == 0) activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Call at the top of a private screen. Does nothing if the member turned the shield off. */
@Composable
fun PrivateScreen() {
    val ctx = LocalContext.current
    DisposableEffect(Unit) {
        val activity = ctx.findActivity()
        val on = activity != null && Prefs(ctx).privateScreens
        if (on) PrivacyShield.acquire(activity!!)
        onDispose { if (on) PrivacyShield.release(activity!!) }
    }
}
