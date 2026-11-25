package com.bas1minute.sync

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Every public link the app shows, in one place. */
object Links {
    const val WEBSITE = "https://bas1minute.pythonanywhere.com"
    /** Copy of the public site on GitHub Pages: stays up even if the main server is down. */
    const val WEBSITE_BACKUP = "https://souravroy0708.github.io/bas1minute-android/"
    const val PRIVACY = "$WEBSITE/privacy/"
    const val PLAY = "https://play.google.com/store/apps/details?id=com.bas1minute.sync"
    const val APK = "https://github.com/souravroy0708/bas1minute-android/releases/latest"
    const val EMAIL = "tosourav6@gmail.com"

    fun open(ctx: Context, url: String) {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** Google Play app if it's there, else the Play website. */
    fun rate(ctx: Context) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { ctx.startActivity(market) }.isFailure) open(ctx, PLAY)
    }

    fun email(ctx: Context, to: String = EMAIL, subject: String = "Bas 1 Minute") {
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$to")).putExtra(Intent.EXTRA_SUBJECT, subject)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(i) }
    }

    fun shareApp(ctx: Context) {
        val text = "I’m using Bas 1 Minute to walk more and scroll less — free, private, made in Hyderabad. " +
            "Join my circle: $PLAY"
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        runCatching { ctx.startActivity(Intent.createChooser(send, "Share Bas 1 Minute").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
