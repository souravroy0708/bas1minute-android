package com.bas1minute.sync.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.bas1minute.sync.VoiceText

/*
 * Speak instead of typing, in Diary and Goals.
 *
 * v2.1 opened the system "Speak now" screen (RecognizerIntent). On many phones that screen belongs to the Google
 * app, and if the Google app's own microphone permission is off — or the app is disabled — it just says
 * "Google voice search isn't available" with no way forward. So now we listen inside our own app with Android's
 * SpeechRecognizer, which lets us explain exactly what's wrong and send the member to the right switch.
 *
 * Privacy
 *  - One permission: Microphone, asked only the first time the mic is tapped, after a plain explanation. Never at
 *    install, never in onboarding. Members who never tap the mic are never asked.
 *  - The mic is on only while the "Listening" window is open. Audio isn't saved, and never sent to Bas 1 Minute.
 *  - On Android 12+ phones that have an offline speech pack, recognition runs fully on the phone
 *    (createOnDeviceSpeechRecognizer). Otherwise the phone's own speech service (usually Google) converts it.
 *  - Only the text comes back, and it lands in an editable box — nothing is saved until the member taps Save.
 *
 * If the phone has no speech service at all, we fall back to the system "Speak now" screen; if that's missing too,
 * the help sheet explains how to install or switch on voice typing.
 */
object VoiceInput {
    private const val GOOGLE_APP = "com.google.android.googlequicksearchbox"
    private const val SPEECH_SERVICES = "com.google.android.tts"

    fun hasMic(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun recognizerAvailable(ctx: Context) = runCatching { SpeechRecognizer.isRecognitionAvailable(ctx) }.getOrDefault(false)

    fun onDeviceAvailable(ctx: Context) = Build.VERSION.SDK_INT >= 31 &&
        runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx) }.getOrDefault(false)

    fun systemSheetAvailable(ctx: Context) = runCatching {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(ctx.packageManager) != null
    }.getOrDefault(false)

    /** Show the mic at all? Yes on almost every phone; the help sheet covers the rest. */
    fun anyWay(ctx: Context) = recognizerAvailable(ctx) || systemSheetAvailable(ctx)

    /** "hi-IN" when the app is in Hindi, otherwise Indian English — which also copes with most Hinglish. */
    fun languageTag(): String = if (Lang.current == "hi") "hi-IN" else "en-IN"

    fun listenIntent(withLanguage: Boolean = true): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        .apply { if (withLanguage) putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag()) }

    fun systemSheetIntent(prompt: String): Intent = listenIntent().putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)

    fun isInstalled(ctx: Context, pkg: String) = runCatching { ctx.packageManager.getApplicationInfo(pkg, 0); true }.getOrDefault(false)

    fun openAppDetails(ctx: Context, pkg: String = ctx.packageName) {
        runCatching {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun openGoogleAppDetails(ctx: Context) = openAppDetails(ctx, if (isInstalled(ctx, GOOGLE_APP)) GOOGLE_APP else SPEECH_SERVICES)

    fun openPlay(ctx: Context, pkg: String = GOOGLE_APP) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(market) } catch (e: ActivityNotFoundException) {
            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    /** Voice-input / language settings, where offline speech packs are downloaded. Falls back to main Settings. */
    fun openSpeechSettings(ctx: Context) {
        val tries = listOf(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS), Intent(Settings.ACTION_LOCALE_SETTINGS), Intent(Settings.ACTION_SETTINGS))
        for (i in tries) if (runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
    }
}

/** Why voice typing can't run right now — each one has its own fix in [VoiceHelpDialog]. */
enum class VoiceProblem { MIC_BLOCKED, SPEECH_APP_NO_MIC, NO_SPEECH_SERVICE, NEEDS_INTERNET_OR_PACK, OTHER }

private sealed interface VoiceSheet {
    data object Explain : VoiceSheet
    data object Listening : VoiceSheet
    data class Help(val problem: VoiceProblem) : VoiceSheet
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** A mic button: explains → asks once → listens in-app, or shows exactly how to fix what's blocking it. */
@Composable
fun MicButton(prompt: String, onHeard: (String) -> Unit) {
    val ctx = LocalContext.current
    val show = remember { VoiceInput.anyWay(ctx) }
    var sheet by remember { mutableStateOf<VoiceSheet?>(null) }

    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        sheet = when {
            granted -> VoiceSheet.Listening
            // "Don't ask again" (or denied twice): only Settings can change it now, so show the steps
            ctx.activity()?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) == false ->
                VoiceSheet.Help(VoiceProblem.MIC_BLOCKED)
            else -> null
        }
    }
    val systemSheet = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let(onHeard)
        }
    }

    if (show) {
        IconButton(
            onClick = {
                sheet = when {
                    VoiceInput.recognizerAvailable(ctx) && VoiceInput.hasMic(ctx) -> VoiceSheet.Listening
                    VoiceInput.recognizerAvailable(ctx) -> VoiceSheet.Explain
                    else -> {
                        try { systemSheet.launch(VoiceInput.systemSheetIntent(prompt)); null }
                        catch (e: ActivityNotFoundException) { VoiceSheet.Help(VoiceProblem.NO_SPEECH_SERVICE) }
                    }
                }
            },
            modifier = Modifier.semantics { contentDescription = t("Speak instead of typing") },
        ) { Icon(Icons.Filled.Mic, contentDescription = null, tint = Ink) }
    }

    when (val s = sheet) {
        VoiceSheet.Explain -> MicExplainDialog(
            onAllow = { askMic.launch(Manifest.permission.RECORD_AUDIO) },
            onCancel = { sheet = null },
        )
        VoiceSheet.Listening -> ListeningDialog(
            onText = { onHeard(it); sheet = null },
            onProblem = { sheet = VoiceSheet.Help(it) },
            onClose = { sheet = null },
        )
        is VoiceSheet.Help -> VoiceHelpDialog(s.problem, onClose = { sheet = null })
        null -> {}
    }
}

@Composable
private fun SheetCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(CardBg).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
private fun MicExplainDialog(onAllow: () -> Unit, onCancel: () -> Unit) {
    Dialog(onDismissRequest = onCancel) {
        SheetCard {
            Box(Modifier.size(56.dp).clip(CircleShape).background(Haldi), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Mic, contentDescription = null, tint = Ink)
            }
            Text(t("Speak instead of typing"), style = MaterialTheme.typography.titleLarge)
            Text("Allow the microphone once, and you can talk your diary and goals instead of typing them.",
                style = MaterialTheme.typography.bodyMedium)
            Text("• The mic is on only while the Listening window is open.\n" +
                "• Your voice is turned into text by your phone’s speech service — on the phone itself where it can.\n" +
                "• Audio is never saved and never sent to Bas 1 Minute. You can edit the text before saving.",
                style = MaterialTheme.typography.bodySmall, color = Muted)
            BigButton(t("Allow microphone"), onClick = onAllow)
            GhostButton(t("Not now"), onClick = onCancel)
        }
    }
}

@Composable
private fun ListeningDialog(onText: (String) -> Unit, onProblem: (VoiceProblem) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var partial by remember { mutableStateOf("") }
    var level by remember { mutableFloatStateOf(0f) }
    var note by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }           // bump to listen again
    var onDevice by remember { mutableStateOf(VoiceInput.onDeviceAvailable(ctx)) }
    var withLanguage by remember { mutableStateOf(true) }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    DisposableEffect(attempt, onDevice, withLanguage) {
        note = null; partial = ""
        val r = if (onDevice && Build.VERSION.SDK_INT >= 31) SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
                else SpeechRecognizer.createSpeechRecognizer(ctx)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) { level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
            override fun onBufferReceived(buffer: ByteArray?) {}   // audio is never kept
            override fun onEndOfSpeech() { level = 0f }
            override fun onPartialResults(b: Bundle?) {
                b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { partial = it }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onResults(b: Bundle?) {
                val text = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() } ?: partial
                if (text.isNotBlank()) onText(text) else note = "Didn’t catch that. Tap Try again and speak a little closer."
            }
            override fun onError(error: Int) {
                level = 0f
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        if (partial.isNotBlank()) onText(partial) else note = "Didn’t catch that. Tap Try again and speak a little closer."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> note = "Voice typing is busy in another app. Tap Try again."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        // Our mic permission is on (we checked), so it's the SPEECH APP's microphone that's off
                        if (VoiceInput.hasMic(ctx)) onProblem(VoiceProblem.SPEECH_APP_NO_MIC) else onProblem(VoiceProblem.MIC_BLOCKED)
                    12, 13 -> // ERROR_LANGUAGE_NOT_SUPPORTED / ERROR_LANGUAGE_UNAVAILABLE (API 31)
                        when {
                            onDevice -> onDevice = false                 // no offline pack: use the normal service
                            withLanguage -> withLanguage = false         // try the phone's own default language
                            else -> onProblem(VoiceProblem.NEEDS_INTERNET_OR_PACK)
                        }
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
                        if (onDevice) onDevice = false else onProblem(VoiceProblem.NEEDS_INTERNET_OR_PACK)
                    SpeechRecognizer.ERROR_CLIENT -> {}                  // we stopped it ourselves
                    else -> if (onDevice) onDevice = false else onProblem(VoiceProblem.OTHER)
                }
            }
        })
        runCatching { r.startListening(VoiceInput.listenIntent(withLanguage)) }.onFailure { onProblem(VoiceProblem.OTHER) }
        onDispose { runCatching { r.cancel(); r.destroy() } }
    }

    Dialog(onDismissRequest = onClose) {
        SheetCard {
            val pulse by animateFloatAsState(1f + level * 0.35f, label = "mic-level")
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.size(76.dp).scale(pulse).clip(CircleShape).background(Haldi.copy(alpha = 0.35f)))
                Box(Modifier.size(60.dp).clip(CircleShape).background(Haldi), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Mic, contentDescription = null, tint = Ink)
                }
            }
            Text(if (note == null) t("Listening…") else t("Try again"), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.CenterHorizontally))
            Text(note ?: partial.ifBlank { t("Speak now") }, style = MaterialTheme.typography.bodyLarge,
                color = if (partial.isBlank() || note != null) Muted else TextMain, modifier = Modifier.heightIn(min = 48.dp))
            if (note != null) BigButton(t("Try again")) { attempt++ }
            else BigButton(t("Done")) { runCatching { recognizer?.stopListening() } }
            GhostButton(t("Cancel"), onClick = onClose)
        }
    }
}

/** Step-by-step fix for each problem, with a button straight to the right screen. */
@Composable
fun VoiceHelpDialog(problem: VoiceProblem, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val (title, intro, steps) = when (problem) {
        VoiceProblem.MIC_BLOCKED -> Triple(
            "Allow the microphone",
            "Microphone is turned off for Bas 1 Minute, so Android won’t ask again. You can switch it on in a few taps:",
            listOf("Tap Open settings below", "Tap Permissions", "Tap Microphone", "Choose “Allow only while using the app”", "Come back and tap the mic again"),
        )
        VoiceProblem.SPEECH_APP_NO_MIC -> Triple(
            "Your phone’s speech app needs the mic",
            "Voice typing is done by your phone’s speech app (usually the Google app), and its own microphone permission is off.",
            listOf("Tap Open speech app settings below", "Tap Permissions", "Tap Microphone and choose “Allow only while using the app”",
                "If the app shows Disabled, tap Enable", "Come back and tap the mic again"),
        )
        VoiceProblem.NO_SPEECH_SERVICE -> Triple(
            "Turn on voice typing",
            "This phone doesn’t have a voice-typing service switched on (the “Google voice search isn’t available” message).",
            listOf("Tap Get the Google app below and install or update it (it includes voice typing)",
                "If it’s already installed: Settings › Apps › Google › Enable", "Then give it Microphone permission", "Come back and tap the mic again"),
        )
        VoiceProblem.NEEDS_INTERNET_OR_PACK -> Triple(
            "Connect, or download offline speech",
            "Voice typing needs the internet on this phone, unless you download the offline speech pack once.",
            listOf("Tap Open speech settings below", "Find “On-device speech recognition” or “Offline speech recognition”",
                "Add English (India) — and हिन्दी if you write in Hindi", "Or just connect to Wi-Fi or mobile data and try again"),
        )
        VoiceProblem.OTHER -> Triple(
            "Voice typing didn’t start",
            "Something on the phone stopped voice typing. These usually fix it:",
            listOf("Close and reopen Bas 1 Minute", "Update the Google app from Play Store", "Check the Google app has Microphone permission",
                "You can also use the mic on your keyboard"),
        )
    }
    Dialog(onDismissRequest = onClose) {
        SheetCard {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(intro, style = MaterialTheme.typography.bodyMedium)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                steps.forEachIndexed { i, s ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(24.dp).clip(CircleShape).background(Ink), contentAlignment = Alignment.Center) {
                            Text("${i + 1}", color = Haldi, style = MaterialTheme.typography.labelMedium)
                        }
                        Text(s, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp, top = 2.dp))
                    }
                }
            }
            when (problem) {
                VoiceProblem.MIC_BLOCKED -> BigButton(t("Open settings")) { VoiceInput.openAppDetails(ctx); onClose() }
                VoiceProblem.SPEECH_APP_NO_MIC -> BigButton(t("Open speech app settings")) { VoiceInput.openGoogleAppDetails(ctx); onClose() }
                VoiceProblem.NO_SPEECH_SERVICE -> BigButton(t("Get the Google app")) { VoiceInput.openPlay(ctx); onClose() }
                VoiceProblem.NEEDS_INTERNET_OR_PACK -> BigButton(t("Open speech settings")) { VoiceInput.openSpeechSettings(ctx); onClose() }
                VoiceProblem.OTHER -> BigButton(t("Open speech app settings")) { VoiceInput.openGoogleAppDetails(ctx); onClose() }
            }
            GhostButton(t("Close"), onClick = onClose)
        }
    }
}

/** For Profile › This phone: microphone status with the same guided fix as the mic button. Optional. */
@Composable
fun VoicePermissionRow() {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(VoiceInput.hasMic(ctx)) }
    var help by remember { mutableStateOf<VoiceProblem?>(null) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok && ctx.activity()?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) == false) help = VoiceProblem.MIC_BLOCKED
    }
    val available = remember { VoiceInput.anyWay(ctx) }
    StatusRow(
        t("Voice typing (optional)"),
        when { !available -> "Not set up on this phone"; granted -> "Microphone allowed while you speak"; else -> "Microphone off · talk instead of typing" },
        ok = granted && available,
        action = when { !available -> t("Fix"); granted -> null; else -> t("Allow") },
        onAction = { if (!available) help = VoiceProblem.NO_SPEECH_SERVICE else ask.launch(Manifest.permission.RECORD_AUDIO) },
    )
    help?.let { VoiceHelpDialog(it) { help = null } }
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
