package com.solar.dashka.presentation.translator.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.solar.dashka.domain.model.ShareMode

/**
 * Sprint 4B.4 — final micro-polish: tight, narrow, light popover.
 *
 * Дашкин direction: "popup должен ощущаться как continuation кнопки 📤,
 * а не появляться отдельным блоком". Telegram / macOS / Notion context-menu
 * feel, not Android settings panel.
 *
 * Implementation note: Sprint 4B.3 used Material 3 DropdownMenuItem which
 * has a fixed minimum row height (~48dp) and ~12dp vertical padding. To
 * achieve tighter macOS-style density we replace DropdownMenuItem with a
 * custom Row + clickable inside the DropdownMenu container. This gives full
 * control over height, padding, and icon size while keeping all the
 * DropdownMenu-provided affordances (anchoring, fade+scale animation,
 * tap-outside dismissal).
 *
 * Tuned vs Material 3 defaults:
 *   - Width:   180dp  (vs ~240dp default)
 *   - Row pad: 8dp v / 12dp h  (vs 12dp v / 16dp h)
 *   - Icon:    18dp   (vs 20dp)
 *   - Offset:  y = -4dp  — popup hangs right under the icon, no gap
 */
@Composable
fun SharePopoverMenu(
    isPreparingVoice: Boolean,
    onPickMode: (ShareMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = "Поделиться текстом",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            // Tight offset: popup feels like a continuation of the icon.
            offset = DpOffset(x = 0.dp, y = (-4).dp),
            modifier = Modifier.requiredWidth(POPOVER_WIDTH),
        ) {
            CompactRow(
                onClick = {
                    expanded = false
                    onPickMode(ShareMode.TextOnly)
                },
                enabled = true,
            ) {
                Icon(
                    imageVector = Icons.Default.Article,
                    contentDescription = null,
                    modifier = Modifier.size(ICON_SIZE),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Текст",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            CompactRow(
                onClick = {
                    expanded = false
                    onPickMode(ShareMode.TextAndVoice)
                },
                enabled = !isPreparingVoice,
            ) {
                if (isPreparingVoice) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(ICON_SIZE),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Inventory2,
                        contentDescription = null,
                        modifier = Modifier.size(ICON_SIZE),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = "Текст + голос",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/**
 * Compact row inside the popover — replaces DropdownMenuItem to bypass its
 * fixed minimum height. macOS context-menu density.
 */
@Composable
private fun CompactRow(
    onClick: () -> Unit,
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.4f
    CompositionLocalProvider(
        LocalContentColor provides MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}

private val POPOVER_WIDTH = 180.dp
private val ICON_SIZE = 18.dp


