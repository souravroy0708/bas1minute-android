package com.bas1minute.sync.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.bas1minute.sync.AppController
import com.bas1minute.sync.api.Res
import kotlinx.coroutines.launch

/** Welcome → Log in / Create account. Uses the same mobile-number accounts as the website. */
@Composable
fun AuthScreen(app: AppController, onOpenUrl: (String) -> Unit) {
    var mode by rememberSaveable { mutableStateOf("welcome") }
    when (mode) {
        "welcome" -> Welcome(onLogin = { mode = "login" }, onSignup = { mode = "signup" })
        else -> AuthForm(app, signup = mode == "signup", onSwitch = { mode = if (mode == "signup") "login" else "signup" },
            onBack = { mode = "welcome" }, onOpenUrl = onOpenUrl)
    }
}

@Composable
private fun Welcome(onLogin: () -> Unit, onSignup: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
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
        Spacer(Modifier.weight(1f))
        DualRing(stepsFraction = 0.75f, screenFraction = 0.55f, screenOver = false, modifier = Modifier.size(160.dp)) {
            Text("7K+4", style = MaterialTheme.typography.headlineLarge, color = Color.White)
        }
        Spacer(Modifier.height(28.dp))
        Text("Fit hone ke liye ghante nahi, minute chahiye.", style = MaterialTheme.typography.displayLarge, color = Color.White)
        Spacer(Modifier.height(14.dp))
        Text(
            "7,000 steps and four one-minute bursts a day, a minute of calm, and your family cheering you on.",
            style = MaterialTheme.typography.bodyLarge, color = MutedOnDark,
        )
        Spacer(Modifier.weight(1f))
        BigButton("Create a free account", dark = false, onClick = onSignup)
        Spacer(Modifier.height(10.dp))
        GhostButton("I have an account", onDark = true, onClick = onLogin)
    }
}

@Composable
private fun AuthForm(app: AppController, signup: Boolean, onSwitch: () -> Unit, onBack: () -> Unit, onOpenUrl: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var mobile by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var consent by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var fields by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showServer by remember { mutableStateOf(false) }
    var server by remember { mutableStateOf(app.prefs.serverUrl) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Back", color = Muted, style = MaterialTheme.typography.labelLarge) }
        Text(if (signup) "Create your account" else "Welcome back", style = MaterialTheme.typography.headlineLarge)
        Text(
            if (signup) "Use your mobile number. It’s the same account as the website." else "Log in with your mobile number and password.",
            style = MaterialTheme.typography.bodyLarge, color = Muted,
        )
        if (error != null) {
            Text(error!!, color = Danger, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFFFF1EF)).padding(14.dp))
        }
        if (signup) AppField("Your name", name, { name = it }, fields["name"], placeholder = "Rohit Sharma")
        AppField("Mobile number", mobile, { mobile = it }, fields["mobile"], placeholder = "98765 43210", keyboard = KeyboardType.Phone)
        AppField(
            if (signup) "Choose a password" else "Password", password, { password = it }, fields["password"],
            keyboard = KeyboardType.Password, secret = true,
            hint = if (signup) "At least 8 characters. Avoid common words or only numbers." else null,
        )
        if (signup) {
            Row(verticalAlignment = Alignment.Top) {
                Checkbox(checked = consent, onCheckedChange = { consent = it }, colors = CheckboxDefaults.colors(checkedColor = Ink))
                Column(Modifier.padding(top = 12.dp)) {
                    Text("Store my steps, screen time and activity so I can see my dashboard.", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { onOpenUrl("${app.prefs.serverUrl}/privacy/") }) { Text("Read the privacy notice", color = Blue) }
                    fields["consent"]?.let { Text(it, color = Danger, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        BigButton(
            when { busy -> "Please wait…"; signup -> "Create account"; else -> "Log in" },
            enabled = !busy && mobile.isNotBlank() && password.isNotBlank() && (!signup || name.isNotBlank()),
        ) {
            busy = true; error = null; fields = emptyMap()
            scope.launch {
                val res = if (signup) app.repo.signup(name, mobile, password, consent) else app.repo.login(mobile, password)
                busy = false
                when (res) {
                    is Res.Ok -> app.loggedInNow()
                    is Res.Fail -> { error = res.message; fields = res.fields }
                    is Res.Queued -> {}
                }
            }
        }
        TextButton(onClick = onSwitch, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(if (signup) "Already have an account? Log in" else "New here? Create an account", color = Ink, style = MaterialTheme.typography.labelLarge)
        }
        if (!signup) {
            Text("Forgot your password? Email the Bas 1 Minute team from your account’s email and we’ll reset it.",
                style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        TextButton(onClick = { showServer = !showServer }) {
            Text(if (showServer) "Hide website address" else "Testing? Change website address", color = Muted, style = MaterialTheme.typography.labelMedium)
        }
        if (showServer) {
            AppField("Website address", server, { server = it }, null, keyboard = KeyboardType.Uri)
            GhostButton("Use this address") { app.prefs.serverUrl = server; showServer = false }
        }
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
