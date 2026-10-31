package com.bas1minute.sync.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bas1minute.sync.AppController

/** First launch only: welcome, then name + 4-digit PIN. No phone number, email or password. */
@Composable
fun SetupScreen(app: AppController) {
    var step by rememberSaveable { mutableStateOf("welcome") }
    var privacy by rememberSaveable { mutableStateOf(false) }
    if (privacy) {
        androidx.activity.compose.BackHandler { privacy = false }
        AboutScreen("privacy", onBack = { privacy = false })
        return
    }
    if (step == "welcome") Welcome(onStart = { step = "name" }, onPrivacy = { privacy = true })
    else NameAndPin(title = "Make it yours", onBack = { step = "welcome" }, askName = true) { name, pin -> app.finishSetup(name, pin) }
}

@Composable
private fun Welcome(onStart: () -> Unit, onPrivacy: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Ink).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Haldi), contentAlignment = Alignment.Center) {
                Text("1", style = MaterialTheme.typography.headlineMedium, color = Ink)
            }
            Spacer(Modifier.size(12.dp))
            Column {
                Text("Bas 1 Minute", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text("बस 1 मिनट", fontFamily = Devanagari, color = MutedOnDark, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(12.dp))
        DualRing(stepsFraction = 0.75f, screenFraction = 0.55f, screenOver = false, modifier = Modifier.size(150.dp)) {
            Text("7K+4", style = MaterialTheme.typography.headlineLarge, color = Color.White)
        }
        Text("Fit hone ke liye ghante nahi, minute chahiye.", style = MaterialTheme.typography.displayLarge, color = Color.White)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ink2).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Your data, your phone.", style = MaterialTheme.typography.titleLarge, color = Haldi)
            Promise("No phone number, email or password. Just your name.")
            Promise("Everything stays on this phone.")
            Promise("Share only steps and screen time, only if you choose, to compete in circles.")
            Promise("We don’t sell your data. Ever.")
        }
        BigButton("Get started", dark = false, onClick = onStart)
        TextButton(onClick = onPrivacy, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("How we handle your data", color = MutedOnDark)
        }
    }
}

@Composable
private fun Promise(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text("✓", color = Mint, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(10.dp))
        Text(text, color = Color.White, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Name (optional) + PIN + confirm. Used for first setup and for "Forgot PIN" / "Change PIN". */
@Composable
fun NameAndPin(title: String, onBack: (() -> Unit)?, askName: Boolean, onDone: (name: String, pin: String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    val pinOk = pin.length == 4
    val match = pin == pin2
    Column(
        Modifier.fillMaxSize().background(Paper).statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (onBack != null) TextButton(onClick = onBack) { Text("← Back", color = Muted, style = MaterialTheme.typography.labelLarge) }
        Text(title, style = MaterialTheme.typography.headlineLarge)
        if (askName) {
            Text("What should we call you? It’s shown to circles you join. Nothing else is asked.",
                style = MaterialTheme.typography.bodyLarge, color = Muted)
            AppField("Your name", name, { name = it.take(40) }, null, placeholder = "Sourav")
        }
        Text("Choose a 4-digit PIN to open the app if you switch on App lock. Forgot it? You can set a new one anytime.",
            style = MaterialTheme.typography.bodyMedium, color = Muted)
        PinField("4-digit PIN", pin) { pin = it }
        PinField("Type it again", pin2) { pin2 = it }
        if (pinOk && pin2.length == 4 && !match) Text("The PINs don’t match.", color = Danger, style = MaterialTheme.typography.bodyMedium)
        BigButton("Continue", enabled = pinOk && match && (!askName || name.isNotBlank())) { onDone(name, pin) }
    }
}

@Composable
private fun PinField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(4)) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Only when App lock is on: PIN pad; "Forgot PIN?" sets a new one without questions. */
@Composable
fun LockScreen(app: AppController) {
    var entered by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var resetting by rememberSaveable { mutableStateOf(false) }
    if (resetting) {
        NameAndPin(title = "Set a new PIN", onBack = { resetting = false }, askName = false) { _, pin -> app.resetPin(pin) }
        return
    }
    Column(
        Modifier.fillMaxSize().background(Ink).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Namaste${app.prefs.firstName.let { if (it.isBlank()) "" else ", $it" }}", style = MaterialTheme.typography.headlineLarge, color = Color.White)
        Text(if (wrong) "Wrong PIN. Try again." else "Enter your PIN", color = if (wrong) Coral else MutedOnDark, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(4) { i -> Box(Modifier.size(16.dp).clip(CircleShape).background(if (i < entered.length) Haldi else Ink3)) }
        }
        Spacer(Modifier.height(8.dp))
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                row.forEach { k ->
                    Box(
                        Modifier.size(72.dp).clip(CircleShape).background(if (k.isBlank()) Color.Transparent else Ink2)
                            .clickable(enabled = k.isNotBlank()) {
                                wrong = false
                                if (k == "⌫") entered = entered.dropLast(1)
                                else if (entered.length < 4) {
                                    entered += k
                                    if (entered.length == 4) {
                                        if (app.prefs.checkPin(entered)) app.unlocked = true else { wrong = true; entered = "" }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) { Text(k, style = MaterialTheme.typography.headlineMedium, color = Color.White, textAlign = TextAlign.Center) }
                }
            }
        }
        TextButton(onClick = { resetting = true }) { Text("Forgot PIN? Set a new one", color = Haldi, style = MaterialTheme.typography.labelLarge) }
    }
}

@Composable
fun AppField(
    label: String, value: String, onChange: (String) -> Unit, error: String?,
    placeholder: String? = null, keyboard: KeyboardType = KeyboardType.Text, secret: Boolean = false, hint: String? = null,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        isError = error != null,
        supportingText = (error ?: hint)?.let { { Text(it) } },
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
    )
}
