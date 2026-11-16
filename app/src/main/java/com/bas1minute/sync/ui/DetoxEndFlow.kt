package com.bas1minute.sync.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bas1minute.sync.detox.DetoxLogic
import com.bas1minute.sync.detox.DetoxSession
import com.bas1minute.sync.detox.StreakLogic
import kotlinx.coroutines.delay

/**
 * Ending a detox early, honestly. Three short, truthful screens, always with a visible way out — no hidden
 * buttons, no invented claims, no guilt. The friction is real (a short wait, and typing back the person's own
 * reason for starting) rather than fake, because real friction gives a craving — which research on urges puts
 * at a few minutes, not permanent — room to pass; deception just teaches people to distrust the app.
 *
 * Step 1: what ending now actually costs (time, streak) — their own numbers, not a scare line.
 * Step 2: a breathing pause with one honest, non-alarmist fact about cravings.
 * Step 3: type back the reason they gave when they started (or "no particular reason" if they didn't give one),
 *         after a short wait. Optionally review (and choose to send) a message to an accountability contact.
 */
@Composable
fun EndEarlyFlow(
    s: DetoxSession,
    now: Long,
    streak: Int,
    accountabilityName: String,
    accountabilityContact: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var step by remember { mutableIntStateOf(1) }
    when (step) {
        1 -> CostStep(s, now, streak, onContinue = { step = 2 }, onDismiss = onDismiss)
        2 -> PauseStep(onContinue = { step = 3 }, onDismiss = onDismiss)
        else -> CommitStep(s, accountabilityName, accountabilityContact, onConfirm = onConfirm, onDismiss = onDismiss)
    }
}

@Composable
private fun FlowDialog(title: String, dismissLabel: String = "Keep my promise", onDismiss: () -> Unit, content: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() } },
        // The real exit, always here, always the same size, never hidden: ending early is the person's call.
        confirmButton = { },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel, color = Blue, fontWeight = FontWeight.Bold) } },
    )
}

@Composable
private fun StepFooter(nextLabel: String, onContinue: () -> Unit, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onDismiss) { Text("Keep my promise", color = Blue, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(4.dp))
        TextButton(onClick = onContinue) { Text(nextLabel, color = Muted) }
    }
}

@Composable
private fun CostStep(s: DetoxSession, now: Long, streak: Int, onContinue: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Here's what ending now costs") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "You've kept ${DetoxLogic.formatMinutes(s.keptMinutes(now))} of your ${DetoxLogic.formatMinutes(s.plannedMinutes)} promise. " +
                        "${DetoxLogic.formatDuration(s.remainingMs(now))} left.",
                )
                if (s.why.isNotBlank()) Text("Your reason for starting: \u201c${s.why}\u201d", fontWeight = FontWeight.Medium)
                if (streak >= 1) Text(
                    if (streak == 1) "Ending now means this won't count as a focus day, and your streak won't start."
                    else "Ending now breaks your $streak-day streak.",
                    fontWeight = FontWeight.Medium,
                )
                if (s.blockedAttempts > 0) Text("You've already resisted ${s.blockedAttempts} time${if (s.blockedAttempts == 1) "" else "s"} this session.")
            }
        },
        confirmButton = { },
        dismissButton = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Keep my promise", color = Blue, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onContinue) { Text("Still want to end", color = Muted) }
            }
        },
    )
}

@Composable
private fun PauseStep(onContinue: () -> Unit, onDismiss: () -> Unit) {
    // A real 45-second pause, not a dressed-up countdown: long enough for an urge to crest and ease, per
    // research on craving duration (cravings are usually reported to peak and fade within minutes, not hours).
    var seconds by remember { mutableIntStateOf(45) }
    LaunchedEffect(Unit) { while (seconds > 0) { delay(1000); seconds-- } }
    FlowDialog(title = "Before you decide", onDismiss = onDismiss) {
        Text("The urge to check your phone right now will almost certainly feel smaller in a few minutes, even if it doesn't feel that way yet. That's how cravings work — they peak and pass.")
        Text("Try a slow breath in for 4, out for 6, a few times while you wait.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("Keep my promise", color = Blue, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = onContinue, enabled = seconds == 0) {
                Text(if (seconds > 0) "Continue (${seconds}s)" else "Continue", color = if (seconds == 0) Muted else PaperLine)
            }
        }
    }
}

@Composable
private fun CommitStep(
    s: DetoxSession,
    accountabilityName: String,
    accountabilityContact: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val expected = s.why.trim().ifBlank { "no particular reason" }
    var typed by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(accountabilityContact.isBlank()) }   // nothing to send: treat as done
    val matches = typed.trim().equals(expected, ignoreCase = true)
    FlowDialog(title = "Last step", onDismiss = onDismiss) {
        Text("To end early, type back your own reason for starting, exactly as you see it:")
        Text("\u201c$expected\u201d", fontWeight = FontWeight.Bold)
        OutlinedTextField(typed, { typed = it }, singleLine = true, placeholder = { Text(expected) }, modifier = Modifier.fillMaxWidth())
        if (accountabilityContact.isNotBlank()) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Paper).padding(12.dp)) {
                Text("Tell ${accountabilityName.ifBlank { "your contact" }} you ended early?", fontWeight = FontWeight.Medium)
                Text("Nothing is sent unless you tap Send — you'll see and can edit the message first.", style = MaterialTheme.typography.bodySmall, color = Muted)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { sent = true }) { Text("Skip", color = Muted) }
                    TextButton(onClick = {
                        val msg = "I ended my ${DetoxLogic.formatMinutes(s.plannedMinutes)} focus session early today."
                        val send = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$accountabilityContact")).putExtra("sms_body", msg)
                        runCatching { ctx.startActivity(send) }
                        sent = true
                    }) { Text("Review & send") }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("Keep my promise", color = Blue, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = onConfirm, enabled = matches && sent) { Text("End detox", color = if (matches && sent) Danger else Muted) }
        }
    }
}
