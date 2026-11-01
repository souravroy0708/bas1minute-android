package com.bas1minute.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Receives admin campaigns as data messages, personalises them on the phone, shows a notification. */
class PushService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Prefs(this).pushToken = token
        Push.upload(applicationContext)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val d = message.data
        if (d["type"] != "campaign") return
        val ctx = applicationContext
        if (!Prefs(ctx).pushEnabled) return
        if (d["category"] == "taunt" && !Prefs(ctx).allowTaunts) return
        val v = Personalizer.values(ctx)
        if (!Personalizer.matches(d["audience"] ?: "all", v)) return
        val title = Personalizer.fill(d["title"] ?: return, v) ?: return
        val body = Personalizer.fill(d["body"] ?: "", v) ?: return
        Push.show(ctx, title, body, d["id"]?.toIntOrNull() ?: 0)
    }
}

object Push {
    private const val CHANNEL = "motivation"

    /** Fetch this phone's Firebase token and send it (or "" when switched off). Safe without Firebase config. */
    fun upload(context: Context) {
        val prefs = Prefs(context)
        CoroutineScope(Dispatchers.IO).launch {
            if (!prefs.pushEnabled) { com.bas1minute.sync.api.Repo(context).registerPush(""); return@launch }
            val token = prefs.pushToken
            if (token.isNotBlank()) com.bas1minute.sync.api.Repo(context).registerPush(token)
        }
    }

    /** Ask Firebase for the token (first run). Does nothing if the app was built without google-services.json. */
    fun refreshToken(context: Context) {
        try {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { t ->
                Prefs(context).pushToken = t
                upload(context)
            }
        } catch (e: Exception) {
            // Firebase not configured in this build: pushes are off, everything else works.
        }
    }

    fun show(context: Context, title: String, body: String, id: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Motivation", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Nudges and health facts based on your own steps and screen time"
            })
        }
        val open = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(1000 + id, n)
    }
}
