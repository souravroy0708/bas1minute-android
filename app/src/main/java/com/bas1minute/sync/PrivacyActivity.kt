package com.bas1minute.sync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.bas1minute.sync.ui.AboutScreen
import com.bas1minute.sync.ui.Bas1MinuteTheme

/** Opened from the app and from Health Connect's permission screen ("privacy policy"). Fully native. */
class PrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Bas1MinuteTheme { AboutScreen("privacy", onBack = { finish() }) } }
    }
}
