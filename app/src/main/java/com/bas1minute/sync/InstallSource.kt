package com.bas1minute.sync

import android.content.Context
import android.os.Build

/**
 * Android 13+ blocks "restricted settings" (including Usage access) for apps installed outside an
 * app store, until the user taps App info → ⋮ → Allow restricted settings. Play Store installs aren't affected.
 */
object InstallSource {
    private val STORES = setOf("com.android.vending", "com.sec.android.app.samsungapps", "com.amazon.venezia")

    fun restrictedSettingsLikely(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val installer = try {
            context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        } catch (e: Exception) {
            null
        }
        return installer !in STORES
    }
}
