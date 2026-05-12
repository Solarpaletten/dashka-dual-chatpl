package com.solar.dashka.presentation.translator.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.solar.dashka.domain.model.MicState

/**
 * Mic button — visualizes the four MicState variants and dispatches MicTapped.
 *
 * Visual mapping:
 *   Idle / Error / RequestingPermission → outlined gray mic icon
 *   Listening                           → red filled circle, stop icon, pulse animation
 *   Processing                          → red filled circle, stop icon, no pulse
 */
@Composable
fun MicButton(
    state: MicState,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = state is MicState.Listening || state is MicState.Processing

    val containerColor = if (active) {
        Color(0xFFD32F2F)  // Red — matches web stop button
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val contentColor = if (active) Color.White else MaterialTheme.colorScheme.onSurface

    // Pulse animation only during active listening (not Processing).
    val pulseScale: Float = if (state is MicState.Listening) {
        val transition = rememberInfiniteTransition(label = "mic-pulse")
        val scale by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 700),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "mic-pulse-scale",
        )
        scale
    } else {
        1f
    }

    val targetScale by animateFloatAsState(
        targetValue = pulseScale,
        animationSpec = tween(150),
        label = "mic-scale-target",
    )

    val description = when (state) {
        MicState.Idle -> "Включить микрофон"
        MicState.Listening -> "Остановить запись"
        MicState.Processing -> "Обработка…"
        MicState.RequestingPermission -> "Запрос разрешения"
        is MicState.Error -> "Ошибка микрофона"
    }

    Surface(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .scale(targetScale)
            .semantics { contentDescription = description },
        color = containerColor,
        shape = CircleShape,
        onClick = onTap,
    ) {
        Box(
            modifier = Modifier.background(containerColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (active) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = null,
                tint = contentColor,
            )
        }
    }
}
