package com.solar.dashka.presentation.translator.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.solar.dashka.domain.model.TtsState

/**
 * Play/Stop button for TTS output.
 *
 * Visual mapping:
 *   Idle / Error               → outlined speaker icon
 *   Loading                    → circular progress
 *   Playing                    → orange filled stop icon (matches mic active style)
 *
 * Tap dispatches:
 *   - Idle / Error → PlayTtsTapped (ViewModel kicks off fetch+play flow)
 *   - Playing      → StopTtsTapped (ViewModel cancels playback)
 *   - Loading      → ignored (let the in-flight request complete)
 */
@Composable
fun PlayTtsButton(
    state: TtsState,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isPlaying = state is TtsState.Playing
    val isLoading = state is TtsState.Loading

    val containerColor = when {
        isPlaying -> Color(0xFFD97706)  // brand orange — matches translate button
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val contentColor = when {
        isPlaying -> Color.White
        else -> MaterialTheme.colorScheme.onSurface
    }

    val description = when (state) {
        TtsState.Idle -> "Озвучить перевод"
        TtsState.Loading -> "Загрузка озвучки…"
        TtsState.Playing -> "Остановить воспроизведение"
        is TtsState.Error -> "Ошибка озвучки"
    }

    Surface(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .semantics { contentDescription = description },
        color = containerColor,
        shape = CircleShape,
        enabled = !isLoading,
        onClick = { if (isPlaying) onStop() else onPlay() },
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                isLoading -> CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                isPlaying -> Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = null,
                    tint = contentColor,
                )
                else -> Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    tint = contentColor,
                )
            }
        }
    }
}
