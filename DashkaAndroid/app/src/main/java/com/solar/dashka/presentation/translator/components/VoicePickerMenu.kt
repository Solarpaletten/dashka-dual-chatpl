package com.solar.dashka.presentation.translator.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.solar.dashka.domain.model.TtsVoice

/**
 * Voice picker — anchored TextButton + DropdownMenu.
 *
 * Sprint 3B: 5 hardcoded voices. Persistence is in-memory (ViewModel state).
 * Selection survives across translations within the session, but resets on
 * app kill/restart. Sprint 3D+ may add DataStore persistence.
 *
 * Layout: shown in TopAppBar actions slot. Compact form factor (~80dp wide).
 */
@Composable
fun VoicePickerMenu(
    selectedVoice: TtsVoice,
    onVoiceSelected: (TtsVoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    TextButton(
        onClick = { expanded = true },
        modifier = modifier,
    ) {
        Text(
            text = "${selectedVoice.icon} ${selectedVoice.displayName}",
            style = MaterialTheme.typography.labelLarge,
        )
        Icon(
            imageVector = Icons.Default.ArrowDropDown,
            contentDescription = "Сменить голос",
        )
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        TtsVoice.entries.forEach { voice ->
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${voice.icon} ${voice.displayName}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = voice.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                },
                onClick = {
                    expanded = false
                    onVoiceSelected(voice)
                },
                trailingIcon = if (voice == selectedVoice) {
                    {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Выбран",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else null,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

/* --- Voice metadata extensions --- */

/** Emoji icon — 👨 male, 👩 female. */
private val TtsVoice.icon: String
    get() = if (isFemale) "👩" else "👨"

/** Russian short description for dropdown subtitle. */
private val TtsVoice.description: String
    get() = when (this) {
        TtsVoice.LEO -> "спокойный"
        TtsVoice.REX -> "глубокий"
        TtsVoice.SAL -> "нейтральный"
        TtsVoice.EVE -> "мягкий"
        TtsVoice.ARA -> "выразительный"
    }
