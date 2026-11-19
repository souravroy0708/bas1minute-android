package com.bas1minute.sync.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
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

    fun openGoogleAppDetails(ctx: Context) = openAppDetails(ctx, speechService(ctx)?.packageName
        ?: if (isInstalled(ctx, GOOGLE_APP)) GOOGLE_APP else SPEECH_SERVICES)

    /**
     * The app that actually turns speech into text (usually "Google"). It records the audio itself, so it needs
     * ITS OWN microphone permission and must be enabled. When it isn't, Android says "Google voice search isn't
     * available" — the most common reason voice typing fails even though Bas 1 Minute has the mic.
     */
    data class SpeechService(val packageName: String, val label: String, val enabled: Boolean, val hasMic: Boolean)

    fun speechService(ctx: Context): SpeechService? = runCatching {
        val pm = ctx.packageManager
        val default = Settings.Secure.getString(ctx.contentResolver, "voice_recognition_service")
            ?.let { ComponentName.unflattenFromString(it)?.packageName }
        val installed = pm.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0).map { it.serviceInfo.packageName }
        val pkg = default?.takeIf { it.isNotBlank() } ?: installed.firstOrNull { it == GOOGLE_APP }
            ?: installed.firstOrNull() ?: return@runCatching null
        val info = pm.getApplicationInfo(pkg, 0)
        SpeechService(
            packageName = pkg,
            label = pm.getApplicationLabel(info).toString().ifBlank { "Google" },
            enabled = info.enabled,
            hasMic = pm.checkPermission(Manifest.permission.RECORD_AUDIO, pkg) == PackageManager.PERMISSION_GRANTED,
        )
    }.getOrNull()

    /** Our mic is fine, but the speech app is off or has no mic (and on-device recognition can't step in). */
    fun speechAppBlocked(ctx: Context): Boolean {
        val s = speechService(ctx) ?: return false
        return (!s.enabled || !s.hasMic) && !onDeviceAvailable(ctx)
    }

    fun isOnline(ctx: Context): Boolean = runCatching {
        val cm = ctx.getSystemService(android.net.ConnectivityManager::class.java)
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }.getOrDefault(true)

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
    data object Setup : VoiceSheet
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
            granted && VoiceInput.speechAppBlocked(ctx) -> VoiceSheet.Help(VoiceProblem.SPEECH_APP_NO_MIC)
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
                val canListen = VoiceInput.recognizerAvailable(ctx) || VoiceInput.onDeviceAvailable(ctx)
                sheet = when {
                    canListen && !VoiceInput.hasMic(ctx) -> VoiceSheet.Explain
                    canListen && VoiceInput.speechAppBlocked(ctx) -> VoiceSheet.Help(VoiceProblem.SPEECH_APP_NO_MIC)
                    canListen -> VoiceSheet.Listening
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
        is VoiceSheet.Help -> VoiceHelpDialog(s.problem, onCheckEverything = { sheet = VoiceSheet.Setup }, onClose = { sheet = null })
        VoiceSheet.Setup -> VoiceSetupDialog(onClose = { sheet = null })
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
                    SpeechRecognizer.ERROR_AUDIO -> if (VoiceInput.speechAppBlocked(ctx)) onProblem(VoiceProblem.SPEECH_APP_NO_MIC) else onProblem(VoiceProblem.OTHER)
                    SpeechRecognizer.ERROR_CLIENT -> if (VoiceInput.speechAppBlocked(ctx)) onProblem(VoiceProblem.SPEECH_APP_NO_MIC)   // else: we stopped it ourselves
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
fun VoiceHelpDialog(problem: VoiceProblem, onCheckEverything: (() -> Unit)? = null, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val (title, intro, steps) = when (problem) {
        VoiceProblem.MIC_BLOCKED -> Triple(
            "Allow the microphone",
            "Microphone is turned off for Bas 1 Minute, so Android won’t ask again. You can switch it on in a few taps:",
            listOf("Tap Open settings below", "Tap Permissions", "Tap Microphone", "Choose “Allow only while using the app”", "Come back and tap the mic again"),
        )
        VoiceProblem.SPEECH_APP_NO_MIC -> {
            val svc = VoiceInput.speechService(ctx); val name = svc?.label ?: "Google"
            if (svc?.enabled == false) Triple(
                "The “$name” app is turned off",
                "Bas 1 Minute already has the microphone. Android’s voice typing is done by the “$name” app, and it’s switched off — that’s why Android says voice search isn’t available.",
                listOf("Tap Open $name settings below", "Tap Enable (or Turn on)", "Then Permissions › Microphone › Allow", "Come back and tap the mic again"),
            ) else Triple(
                "Allow the microphone for “$name”",
                "Bas 1 Minute already has the microphone. Android’s voice typing is done by the “$name” app, and it needs the microphone too — that’s why Android says voice search isn’t available.",
                listOf("Tap Open $name settings below", "Tap Permissions", "Tap Microphone", "Choose “Allow only while using the app”, then come back"),
            )
        }
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
                VoiceProblem.SPEECH_APP_NO_MIC -> BigButton("${t("Open")} ${VoiceInput.speechService(ctx)?.label ?: "Google"} ${t("settings")}") {
                    VoiceInput.openGoogleAppDetails(ctx); onClose()
                }
                VoiceProblem.NO_SPEECH_SERVICE -> BigButton(t("Get the Google app")) { VoiceInput.openPlay(ctx); onClose() }
                VoiceProblem.NEEDS_INTERNET_OR_PACK -> BigButton(t("Open speech settings")) { VoiceInput.openSpeechSettings(ctx); onClose() }
                VoiceProblem.OTHER -> BigButton(t("Open speech app settings")) { VoiceInput.openGoogleAppDetails(ctx); onClose() }
            }
            // Never open a dialog INSIDE this dialog: closing the parent while the child is measuring crashes Compose
            // ("LayoutNode should be attached to an owner"). The caller closes this one and opens the checklist.
            if (onCheckEverything != null) GhostButton(t("Check everything"), onClick = onCheckEverything)
            GhostButton(t("Close"), onClick = onClose)
        }
    }
}

/** For Profile › This phone: one row with the live voice status; tap it for the full setup checklist. */
@Composable
fun VoicePermissionRow() {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose { } }
    var setup by remember { mutableStateOf(false) }
    val ready = remember(tick, setup) {
        VoiceInput.hasMic(ctx) && (VoiceInput.recognizerAvailable(ctx) || VoiceInput.onDeviceAvailable(ctx)) && !VoiceInput.speechAppBlocked(ctx)
    }
    StatusRow(
        t("Voice typing (optional)"),
        if (ready) "Ready · speak instead of typing in Diary and Goals" else "Not ready yet · tap Set up to see what’s missing",
        ok = ready,
        action = if (ready) t("Check") else t("Set up"),
        onAction = { setup = true },
    )
    if (setup) VoiceSetupDialog(onClose = { setup = false; tick++ })
}

/**
 * Voice typing setup — the same checklist Kharcha Check uses. Voice needs three things, and phones differ, so
 * each one is checked live (again every time you come back from Settings) with the exact fix:
 *  1. Microphone permission for Bas 1 Minute.
 *  2. A speech service on the phone (usually the Google app or "Speech Recognition & Synthesis from Google").
 *  3. Microphone permission for THAT app, which records the audio. When it's missing Android says
 *     "Google voice search isn't available" — the most common reason voice doesn't work.
 * Plus online/offline, and a Test button that shows exactly what was heard. Nothing is saved.
 */
@Composable
fun VoiceSetupDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose { } }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    var testing by remember { mutableStateOf(false) }
    var heard by remember { mutableStateOf<String?>(null) }
    var testProblem by remember { mutableStateOf<VoiceProblem?>(null) }

    val appMic = remember(tick) { VoiceInput.hasMic(ctx) }
    val service = remember(tick) { VoiceInput.speechService(ctx) }
    val serviceOk = remember(tick) { VoiceInput.recognizerAvailable(ctx) }
    val onDevice = remember(tick) { VoiceInput.onDeviceAvailable(ctx) }
    val online = remember(tick) { VoiceInput.isOnline(ctx) }
    val name = service?.label ?: "Google"
    val allReady = appMic && (serviceOk || onDevice) && (service == null || (service.enabled && service.hasMic) || onDevice)

    Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(28.dp)).background(Paper)
                .verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(t("Voice typing setup"), style = MaterialTheme.typography.headlineMedium, color = TextMain)
            Text("Voice typing needs these. Each one is checked live — tap the button next to anything that isn’t ready.",
                style = MaterialTheme.typography.bodyMedium, color = Muted)

            VoiceCheck(
                ok = appMic, title = "Microphone for Bas 1 Minute",
                okText = "Allowed. Used only while the Listening window is open.",
                badText = "Not allowed yet. Tap Allow, then choose “While using the app”. If nothing pops up, Android has blocked it: tap Open settings › Permissions › Microphone › Allow.",
                fix = "Allow" to { askMic.launch(Manifest.permission.RECORD_AUDIO) },
                fix2 = "Open settings" to { VoiceInput.openAppDetails(ctx) },
            )
            VoiceCheck(
                ok = serviceOk || onDevice, title = "A speech service on this phone",
                okText = when {
                    onDevice && service != null -> "Ready: on-device recognition, with “$name” as backup."
                    onDevice -> "Ready: on-device recognition."
                    else -> "Ready: “$name”."
                },
                badText = "None found. Install or update “Speech Recognition & Synthesis from Google” (free), then come back.",
                fix = "Open Play Store" to { VoiceInput.openPlay(ctx, "com.google.android.tts") },
                fix2 = "Get the Google app" to { VoiceInput.openPlay(ctx) },
            )
            if (service != null) VoiceCheck(
                ok = (service.enabled && service.hasMic) || onDevice, title = "Microphone for “$name”",
                okText = if (service.enabled && service.hasMic) "Allowed. “$name” turns your speech into text."
                    else "On-device recognition is used, so “$name” isn’t needed.",
                badText = if (!service.enabled) "The “$name” app is turned off. Open its settings, tap Enable, then allow Microphone under Permissions."
                    else "Not allowed. This is why Android says “voice search isn’t available”. Open “$name” settings › Permissions › Microphone › Allow while using the app.",
                fix = "Open $name settings" to { VoiceInput.openAppDetails(ctx, service.packageName) },
            )
            VoiceCheck(
                ok = onDevice || online, title = "Offline or online", warnOnly = true,
                okText = if (onDevice) "Speech is turned into text on this phone. Works without internet." else "You’re online, so voice works now.",
                badText = "You’re offline and this phone has no offline speech pack. Connect to the internet, or download English (India) — and हिन्दी — under On-device / Offline speech recognition.",
                fix = "Open voice settings" to { VoiceInput.openSpeechSettings(ctx) },
            )

            AppCard(title = t("Test it")) {
                Text("Say something like “Today I walked to the market with my daughter”. Nothing is saved.",
                    style = MaterialTheme.typography.bodyMedium, color = Muted)
                BigButton(t("Start test"), enabled = allReady) { heard = null; testing = true }
                if (!allReady) Text("Fix the items above first.", style = MaterialTheme.typography.bodySmall, color = Muted)
                heard?.let { Text("Heard: “$it” — voice typing works ✓", style = MaterialTheme.typography.titleSmall, color = TextMain) }
            }
            GhostButton(t("Done"), onClick = onClose)
        }
    }
    if (testing) ListeningDialog(
        onText = { heard = it; testing = false },
        onProblem = { testing = false; testProblem = it },
        onClose = { testing = false },
    )
    testProblem?.let { VoiceHelpDialog(it) { testProblem = null; tick++ } }
}

@Composable
private fun VoiceCheck(
    ok: Boolean, title: String, okText: String, badText: String,
    fix: Pair<String, () -> Unit>, fix2: Pair<String, () -> Unit>? = null, warnOnly: Boolean = false,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardBg)
            .border(1.dp, if (ok) CardLine else if (warnOnly) Haldi else Danger, RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(if (ok) "✅" else if (warnOnly) "⚠️" else "❌", style = MaterialTheme.typography.titleLarge)
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextMain)
                Text(if (ok) okText else badText, style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
        }
        if (!ok) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigButton(fix.first, modifier = Modifier.weight(1f), onClick = fix.second)
            fix2?.let { GhostButton(it.first, modifier = Modifier.weight(1f), onClick = it.second) }
        }
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
