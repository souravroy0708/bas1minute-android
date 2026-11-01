package com.bas1minute.sync.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bas1minute.sync.api.BoardRow

/** Top three members who share, as a podium (2nd · 1st · 3rd). Steps rise in when shown. */
@Composable
fun Podium(board: List<BoardRow>) {
    val top = board.filter { it.shares }.sortedBy { it.rank }.take(3)
    if (top.size < 2) return
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val order = listOfNotNull(top.getOrNull(1), top.getOrNull(0), top.getOrNull(2))
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Leaders: " + top.joinToString { "${it.rank}. ${it.name}, ${it.points} points" } },
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom,
    ) {
        order.forEach { row ->
            val target: Dp = when (row.rank) { 1 -> 110.dp; 2 -> 80.dp; else -> 60.dp }
            val h by animateDpAsState(if (shown) target else 8.dp, tween(700), label = "podium")
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(null, row.name, if (row.rank == 1) 56.dp else 44.dp)
                Text(row.name + if (row.isMe) " (you)" else "", style = MaterialTheme.typography.labelLarge, maxLines = 1,
                    modifier = Modifier.padding(top = 4.dp))
                Text("${row.points} pts", style = MaterialTheme.typography.bodySmall, color = Muted)
                Box(
                    Modifier.fillMaxWidth().height(h).padding(top = 6.dp)
                        .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                        .background(when (row.rank) { 1 -> Haldi; 2 -> Color(0xFFC9CEFF); else -> Color(0xFFFFD8C8) }),
                    contentAlignment = Alignment.TopCenter,
                ) { Text("${row.rank}", style = MaterialTheme.typography.headlineMedium, color = Ink, modifier = Modifier.padding(top = 8.dp)) }
            }
        }
    }
}
