package com.bas1minute.sync

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bas1minute.sync.ui.Bas1MinuteTheme
import com.bas1minute.sync.ui.Ink
import com.bas1minute.sync.ui.Muted
import com.bas1minute.sync.ui.Paper

/** Shown from the app and from Health Connect's permission screen ("privacy policy"). */
class PrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val site = Prefs(this).serverUrl
        setContent {
            Bas1MinuteTheme {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(Paper)
                        .safeDrawingPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("What this app reads and sends", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Ink)
                    Block("Steps", "Your daily step total, read from Health Connect or counted by Google Play services on this phone.")
                    Block("Screen time and social media", "Total minutes on screen and in social media apps each day, plus when you last used the phone at night and first used it in the morning. Worked out on the phone. App names and what you did in apps never leave your phone.")
                    Block("Sleep, water and weight", "Read from Health Connect only if you allow each one (a watch, scale or other app saves them there), or typed by you. If you don’t allow sleep, the app estimates it from your longest overnight break from the phone. Weight is off unless you switch it on.")
                    Block("What you add yourself", "Bursts, calm minutes, water, sleep, weight, your mood check-in, goals and circles. Mood, sleep, water, weight and social media time are only ever visible to you, never to your circles.")
                    Block("What we send", "Daily totals (steps, screen minutes, bursts, calm minutes) and your mood, to your own Bas 1 Minute account only, over a secure connection. Your circles see steps, bursts and calm; screen time only if you switch it on for that circle.")
                    Block("Offline", "Everything is saved on this phone first and sent when you’re online. Logging out removes your account data from this phone.")
                    Block("What we never read", "Location, contacts, messages, photos, or any other health data.")
                    Block("Your control", "Turn off any access in Android Settings or Health Connect at any time. Delete your account and all data from the website’s Profile page.")
                    Button(
                        onClick = { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$site/privacy/"))) },
                        colors = ButtonDefaults.buttonColors(containerColor = Ink),
                        shape = RoundedCornerShape(50),
                    ) { Text("Read the full privacy notice", fontWeight = FontWeight.Bold, color = Color.White) }
                    TextButton(onClick = { finish() }) { Text("Close", color = Muted) }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun Block(title: String, body: String) {
    Column(
        Modifier
            .background(Color.White, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink)
        Text(body, color = Muted, fontSize = 15.sp)
    }
}
