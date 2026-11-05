package com.bas1minute.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * If the app ever closes unexpectedly, the next launch offers to email the report. Nothing is sent
 * automatically; the person sees the text in their mail app first. Google Play's Android vitals also
 * collects crashes once the app is on the Play Store.
 */
object CrashLog {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val appCtx = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                File(appCtx.filesDir, FILE).writeText(buildString {
                    appendLine("Bas 1 Minute ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    appendLine("${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                    appendLine("Thread: ${thread.name}")
                    appendLine()
                    append(sw.toString().take(12_000))
                })
            } catch (x: Exception) { }
            previous?.uncaughtException(thread, e)
        }
    }

    fun pending(context: Context): String? = File(context.filesDir, FILE).takeIf { it.exists() }?.readText()

    fun clear(context: Context) { File(context.filesDir, FILE).delete() }

    fun email(context: Context, report: String) {
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(BuildConfig.SUPPORT_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, "Bas 1 Minute crash report ${BuildConfig.VERSION_NAME}")
            putExtra(Intent.EXTRA_TEXT, "What were you doing when it closed?\n\n\n---\n$report")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try { context.startActivity(i) } catch (e: Exception) { }
    }
}
