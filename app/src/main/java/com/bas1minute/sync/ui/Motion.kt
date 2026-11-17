package com.bas1minute.sync.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role

/*
 * The one piece of "feel" shared by every tappable thing: it sinks slightly under the finger (spring back, no
 * bounce), the ripple stays inside its rounded shape, and the phone gives the lightest possible tick.
 * Premium apps feel premium mostly because every touch answers like this, consistently.
 *
 * Put it FIRST in the chain, before background/padding, so the whole tile scales:
 *   Modifier.tactileClickable(RoundedCornerShape(20.dp)) { … }.background(CardBg).padding(16.dp)
 */
fun Modifier.tactileClickable(
    shape: Shape = RectangleShape,
    onClickLabel: String? = null,
    role: Role? = Role.Button,
    enabled: Boolean = true,
    haptic: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.965f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 700f),
        label = "press",
    )
    val haptics = LocalHapticFeedback.current
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(shape)
        .clickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
        ) {
            if (haptic) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}
