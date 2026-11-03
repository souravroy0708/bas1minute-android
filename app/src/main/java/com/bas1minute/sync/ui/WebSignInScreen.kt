package com.bas1minute.sync.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bas1minute.sync.AppController
import com.bas1minute.sync.api.Repo
import com.bas1minute.sync.api.Res

private const val CODE_LEN = 8
private const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"

/** Keeps only code characters, max 8: "k7m2-qx9p" → "K7M2QX9P". */
private fun clean(raw: String) = raw.uppercase().filter { it in ALPHABET }.take(CODE_LEN)

/**
 * Profile → Sign in on the website. The website shows a code (and a QR that opens this screen with it);
 * this phone, already signed in, approves it. Also lists signed-in browsers with Sign out.
 */
@Composable
fun WebSignInScreen(app: AppController, onBack: () -> Unit) {
    var code by remember { mutableStateOf(clean(app.webCode)) }
    var checking by remember { mutableStateOf(false) }
    var request by remember { mutableStateOf<Repo.WebRequest?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf<String?>(null) }
    var sessions by remember { mutableStateOf<List<Repo.WebSession>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val site = app.prefs.serverUrl.removePrefix("https://").removePrefix("http://")

    fun check(c: String) {
        if (c.length != CODE_LEN || checking) return
        checking = true; error = null; request = null; done = null
        app.launch {
            when (val r = app.repo.webCheck(c)) {
                is Res.Ok -> request = r.value
                is Res.Fail -> error = r.message
                else -> Unit
            }
            checking = false
        }
    }

    // Opened from the website's QR: look the code up straight away
    LaunchedEffect(Unit) { if (code.length == CODE_LEN) check(code); app.webCode = "" }
    LaunchedEffect(reload) {
        val r = app.repo.webSessions()
        sessions = if (r is Res.Ok) r.value else null
    }

    Column(Modifier.fillMaxSize().background(Paper).statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("← Profile", color = Muted, style = MaterialTheme.typography.labelLarge) }
        Text("Sign in on the website", style = MaterialTheme.typography.headlineLarge)
        Text("Open $site on a computer and choose Log in. It shows a code. Enter it here (or scan its QR with your camera) and approve. No password needed: this phone is your key.",
            style = MaterialTheme.typography.bodyMedium, color = Muted)

        AppCard(title = "Code from the website") {
            OutlinedTextField(
                value = if (code.length > 4) code.take(4) + "-" + code.drop(4) else code,
                onValueChange = { v ->
                    code = clean(v); request = null; error = null; done = null
                    if (code.length == CODE_LEN) check(code)
                },
                singleLine = true, placeholder = { Text("K7M2-QX9P") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                textStyle = MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.Center),
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
            )
            when {
                checking -> Text("Checking…", color = Muted)
                error != null -> Text(error!!, color = Danger, style = MaterialTheme.typography.bodyMedium)
                done != null -> Text(done!!, color = MintDark, style = MaterialTheme.typography.titleMedium)
            }
            request?.let { r ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFFFF3C8)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Sign in to Bas 1 Minute on ${r.browser}?", style = MaterialTheme.typography.titleMedium, color = Ink)
                    Text("Asked ${r.ago}" + (if (r.ip.isNotBlank()) " from ${r.ip}" else "") + ".", style = MaterialTheme.typography.bodySmall, color = Ink)
                    Text("Only approve if you’re signing in on your own screen right now. Never approve a code someone sent you.",
                        style = MaterialTheme.typography.bodySmall, color = Ink)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        SmallPill("Approve") {
                            app.launch {
                                when (val res = app.repo.webApprove(code, true)) {
                                    is Res.Ok -> { done = "Approved. The website is signing you in."; request = null; code = ""; reload++ }
                                    is Res.Fail -> error = res.message
                                    else -> Unit
                                }
                            }
                        }
                        TextButton(onClick = {
                            app.launch { app.repo.webApprove(code, false); done = "Denied. That browser can’t sign in."; request = null; code = "" }
                        }) { Text("Deny", color = Danger) }
                    }
                }
            }
        }

        AppCard(title = "Signed-in browsers") {
            val list = sessions
            when {
                list == null -> Text("Connect to the internet to see them.", style = MaterialTheme.typography.bodyMedium, color = Muted)
                list.isEmpty() -> Text("None. The website isn’t signed in anywhere.", style = MaterialTheme.typography.bodyMedium, color = Muted)
                else -> {
                    list.forEach { s ->
                        StatusRow(s.browser, listOf(s.ip, "active ${s.lastSeen}").filter { it.isNotBlank() }.joinToString(" · "),
                            ok = true, action = "Sign out") {
                            app.launch { app.handle(app.repo.webSignOut(s.id), "${s.browser} was signed out.") { reload++ } }
                        }
                    }
                    if (list.size > 1) TextButton(onClick = {
                        app.launch { app.handle(app.repo.webSignOut(null), "Signed out of every browser.") { reload++ } }
                    }) { Text("Sign out of every browser", color = Danger) }
                }
            }
            Text("The website shows only what this phone shares: steps, screen time and your circles.",
                style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Spacer(Modifier.height(8.dp))
    }
}
