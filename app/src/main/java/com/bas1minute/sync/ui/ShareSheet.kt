package com.bas1minute.sync.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bas1minute.sync.AppController
import com.bas1minute.sync.Distance
import com.bas1minute.sync.ShareCard

/** Pick a card (steps, streak, circle rank), a size (post or story), preview it, then share the image. */
@Composable
fun ShareSheet(app: AppController, onShareImage: (Bitmap, String) -> Unit) {
    val start = app.shareKind ?: return
    val context = LocalContext.current
    val goal = app.home.stepGoal
    val bestCircle = app.circles?.circles?.minByOrNull { it.myRank }
    val options = buildList {
        add("steps" to "Steps")
        add("streak" to "21-day streak")
        if (bestCircle != null) add("rank" to "Circle rank")
    }
    var kind by remember { mutableStateOf(if (start == "rank" && bestCircle == null) "steps" else start) }
    var story by remember { mutableStateOf(false) }
    var showName by remember { mutableStateOf(true) }

    val card: ShareCard.Kind = when (kind) {
        "streak" -> {
            val steps = app.phone.last21.map { it.row?.steps }
            ShareCard.Kind.Streak(
                streak = TwentyOneStats.streak(steps, goal),
                goalDays = steps.count { (it ?: 0L) >= goal },
                days = steps.map { s -> if (s == null || s == 0L) null else s >= goal },
            )
        }
        "rank" -> bestCircle!!.let { ShareCard.Kind.Rank(it.myRank, it.size, it.name, it.myPoints) }
        else -> {
            val s = app.phone.today?.row?.steps ?: 0L
            ShareCard.Kind.Steps(s, goal, Distance.format(Distance.km(s, app.prefs.stepLengthCm)))
        }
    }
    val name = app.prefs.firstName.takeIf { showName }
    val bitmap = remember(card, story, name) { ShareCard.render(context, card, name, story) }
    val caption = when (card) {
        is ShareCard.Kind.Steps -> "${card.km} today on Bas 1 Minute 🚶"
        is ShareCard.Kind.Streak -> "🔥 ${card.streak}-day streak on Bas 1 Minute"
        is ShareCard.Kind.Rank -> "#${card.rank} in ${card.circle} this week 🏆 #Bas1Minute"
    }

    Dialog(onDismissRequest = { app.shareKind = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(28.dp)).background(Paper)
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Share your achievement", style = MaterialTheme.typography.titleLarge)
            Choice(options, kind) { kind = it }
            Choice(listOf("post" to "Post", "story" to "Story"), if (story) "story" else "post") { story = it == "story" }
            Image(
                bitmap.asImageBitmap(), contentDescription = "Preview of the image to share",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).clip(RoundedCornerShape(18.dp)),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Show my name", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = showName, onCheckedChange = { showName = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = Ink, checkedThumbColor = Haldi))
            }
            BigButton("Share image", dark = false) { onShareImage(bitmap, caption); app.shareKind = null }
            Text("Only this image is shared. Nothing else from your phone.", style = MaterialTheme.typography.bodySmall, color = Muted)
            TextButton(onClick = { app.shareKind = null }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Cancel", color = Muted)
            }
        }
    }
}

@Composable
private fun Choice(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            Text(label, style = MaterialTheme.typography.labelMedium, color = if (on) Color.White else Ink,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Ink else Color.White)
                    .clickable { onSelect(value) }.padding(horizontal = 14.dp, vertical = 8.dp))
        }
    }
}
