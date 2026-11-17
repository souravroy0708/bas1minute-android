package com.bas1minute.sync

import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * The "hard lock" door. Android already ships a real app blocker — Digital Wellbeing's app timers and Focus
 * mode (Samsung: "Digital Wellbeing and parental controls") — which greys an app out at the OS level. Sending
 * members there gives a true block with ZERO extra permissions for Bas 1 Minute: no "Display over other apps",
 * no Accessibility, no always-on service. Apps can't set those timers programmatically, so we open the screen
 * and the member sets it themselves.
 *
 * The packages below are the ones declared in the manifest's <queries>. If neither opens (other OEMs, or a
 * build where Digital Wellbeing has no launchable screen), we fall back to the main Settings screen and the UI
 * tells the member to search for "Digital Wellbeing".
 */
object DigitalWellbeing {
    private val PACKAGES = listOf(
        "com.google.android.apps.wellbeing",   // Pixel, Motorola, Nokia, OnePlus and most Android One phones
        "com.samsung.android.forest",          // Samsung
    )

    /** Returns true if Digital Wellbeing itself opened, false if we had to fall back to Settings. */
    fun open(ctx: Context): Boolean {
        val pm = ctx.packageManager
        for (pkg in PACKAGES) {
            val launch = runCatching { pm.getLaunchIntentForPackage(pkg) }.getOrNull() ?: continue
            if (runCatching { ctx.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return true
        }
        runCatching { ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        return false
    }
}
