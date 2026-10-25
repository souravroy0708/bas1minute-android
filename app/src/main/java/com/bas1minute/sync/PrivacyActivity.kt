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
                    Block("Screen time", "The total minutes apps were on screen each day. We add it up on the phone. App names and what you did in apps never leave your phone.")
                    Block("What we send", "One line per day: the date, total steps and total screen minutes. Sent to your Bas 1 Minute account only, over a secure connection, using your sync key.")
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
