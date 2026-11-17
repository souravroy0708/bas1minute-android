package com.bas1minute.sync.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.bas1minute.sync.VoiceText

/*
 * Speak instead of typing, in Diary and Goals.
 *
 * Uses Android's own speech screen (RecognizerIntent.ACTION_RECOGNIZE_SPEECH) — the same "Speak now" sheet
 * Google apps use. The microphone belongs to the system's speech app, not to us, so Bas 1 Minute needs NO
 * RECORD_AUDIO permission and never touches audio. Offline recognition is preferred where the phone has the
 * language pack (most Pixels/Samsungs for English and Hindi), so speech often never leaves the phone.
 *
 * Whatever is heard is ADDED to what's already in the box (never replaces it) and lands in a normal text field,
 * so the member can fix any word it got wrong before saving. If the phone has no speech service, the mic simply
 * doesn't show and the field works exactly as before.
 */

/** Whether this phone has a speech screen to open. Cached per process: it doesn't change while the app runs. */
object VoiceInput {
    private var cached: Boolean? = null
    fun isAvailable(ctx: Context): Boolean = cached ?: run {
        val ok = runCatching {
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(ctx.packageManager) != null
        }.getOrDefault(false)
        cached = ok; ok
    }

    /** "hi-IN" when the app is in Hindi, otherwise Indian English — which also copes with most Hinglish. */
    fun languageTag(): String = if (Lang.current == "hi") "hi-IN" else "en-IN"

    fun intent(prompt: String): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag())
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag())
        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
}

/** A mic button that opens the speech screen and hands back what was heard. Renders nothing if unavailable. */
@Composable
fun MicButton(prompt: String, onHeard: (String) -> Unit) {
    val ctx = LocalContext.current
    val available = remember { VoiceInput.isAvailable(ctx) }
    if (!available) return
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!heard.isNullOrBlank()) onHeard(heard)
    }
    IconButton(
        onClick = { runCatching { launcher.launch(VoiceInput.intent(prompt)) } },
        modifier = Modifier.semantics { contentDescription = t("Speak instead of typing") },
    ) {
        Icon(Icons.Filled.Mic, contentDescription = null, tint = Ink)
    }
}

/**
 * Drop-in replacement for OutlinedTextField in Diary and Goals: same field, plus a mic at the end.
 * [onValueChange] is the caller's own (it still applies the caller's length limit), so spoken text obeys the
 * same rules as typed text.
 */
@Composable
fun VoiceTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = false,
    label: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = singleLine,
        // if/else (not ?.let) so the lambdas get the expected @Composable type
        placeholder = if (placeholder != null) { { Text(placeholder) } } else null,
        label = if (label != null) { { Text(label) } } else null,
        trailingIcon = {
            MicButton(prompt = placeholder ?: t("Speak now")) { heard -> onValueChange(VoiceText.append(value, heard)) }
        },
    )
}
