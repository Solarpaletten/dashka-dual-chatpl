package com.solar.dashka.presentation.translator

import android.Manifest
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solar.dashka.BuildConfig
import com.solar.dashka.domain.model.Direction
import com.solar.dashka.domain.model.LangCode
import com.solar.dashka.domain.model.MicState
import com.solar.dashka.domain.model.PaneState
import com.solar.dashka.domain.model.TtsState
import com.solar.dashka.domain.usecase.TranslateUseCase
import com.solar.dashka.presentation.history.HistoryBottomSheet
import com.solar.dashka.presentation.translator.components.MicButton
import com.solar.dashka.presentation.translator.components.PlayTtsButton
import com.solar.dashka.presentation.translator.components.SharePopoverMenu
import com.solar.dashka.presentation.translator.components.VoicePickerMenu

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslatorScreen(
    viewModel: TranslatorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Sprint 4C: History bottom sheet visibility — local Composable state
    // (no ViewModel needed for visibility, same pattern as SharePopoverMenu).
    var historyVisible by remember { mutableStateOf(false) }

    /* ---- Sprint 4C.6: clipboard-aware paste ---- */
    // We track only WHETHER clipboard has text (not the text itself), so the
    // 📥 button can be shown/hidden adaptively. Reading the description is
    // privacy-friendly — it does NOT trigger the Android 14+ toast that
    // appears on actual getText() calls. We refresh on lifecycle ON_RESUME
    // (typical flow: user copies in another app, switches back to Dashka).
    val context = LocalContext.current
    val clipboardManager = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    var clipboardHasText by remember { mutableStateOf(false) }

    fun refreshClipboardState() {
        val desc = clipboardManager.primaryClipDescription
        clipboardHasText = desc != null && (
            desc.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) ||
            desc.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)
        )
    }

    // Initial check.
    LaunchedEffect(Unit) { refreshClipboardState() }

    // Refresh when user returns to the app (most common paste workflow).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshClipboardState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Also listen for system clipboard changes while we're foregrounded.
    DisposableEffect(clipboardManager) {
        val listener = ClipboardManager.OnPrimaryClipChangedListener {
            refreshClipboardState()
        }
        clipboardManager.addPrimaryClipChangedListener(listener)
        onDispose { clipboardManager.removePrimaryClipChangedListener(listener) }
    }

    /* ---- Permission launcher ---- */
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onIntent(TranslatorIntent.PermissionResult(granted))
    }

    /* ---- One-shot events from ViewModel ---- */
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                TranslatorEvent.RequestRecordAudioPermission -> {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
                is TranslatorEvent.CopyToClipboard -> {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                            as ClipboardManager
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText("Dashka translation", event.text)
                    )
                    snackbarHostState.showSnackbar("Скопировано")
                }
                TranslatorEvent.PasteSuccess -> {
                    snackbarHostState.showSnackbar("Вставлено")
                }
                is TranslatorEvent.ShareText -> {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, event.text)
                        type = "text/plain"
                    }
                    context.startActivity(
                        Intent.createChooser(sendIntent, "Поделиться переводом")
                    )
                }
                is TranslatorEvent.ShareFiles -> {
                    // Sprint 4B.2: single attachment + optional text caption.
                    // Multi-file (ACTION_SEND_MULTIPLE) was removed — text is
                    // always passed via EXTRA_TEXT, never as a .txt attachment.
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = event.mimeType
                        putExtra(Intent.EXTRA_STREAM, event.uris.first())
                        event.accompanyingText?.let { putExtra(Intent.EXTRA_TEXT, it) }
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(
                        Intent.createChooser(sendIntent, "Поделиться")
                    )
                }
            }
        }
    }

    /* ---- Snackbar for errors (translation + mic) ---- */
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.onIntent(TranslatorIntent.DismissError)
        }
    }
    LaunchedEffect(state.micState) {
        if (state.micState is MicState.Error) {
            snackbarHostState.showSnackbar((state.micState as MicState.Error).message)
        }
    }
    LaunchedEffect(state.ttsState) {
        if (state.ttsState is TtsState.Error) {
            snackbarHostState.showSnackbar((state.ttsState as TtsState.Error).message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "🇵🇱 Dashka",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Polski ↔ Русский · v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                actions = {
                    // Sprint 4C.2: clean icon-only History button. Per
                    // Дашкин direction — clock icon is universal pattern
                    // (Telegram/WhatsApp/iOS), users recognize it as
                    // history/recent. The label experiment in v0.4.1 was
                    // reverted: it pushed Title to overflow and broke the
                    // airy minimal premium feel.
                    IconButton(
                        onClick = { historyVisible = true },
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "История",
                        )
                    }
                    // Autoplay toggle — compact label + Switch
                    Text(
                        text = "Авто",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Switch(
                        checked = state.autoplayEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.onIntent(TranslatorIntent.ToggleAutoplay(enabled))
                        },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .scale(0.75f),
                    )
                    VoicePickerMenu(
                        selectedVoice = state.voice,
                        onVoiceSelected = { voice ->
                            viewModel.onIntent(TranslatorIntent.VoiceSelected(voice))
                        },
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DirectionSwitchRow(
                direction = state.direction,
                onToggle = { viewModel.onIntent(TranslatorIntent.ToggleDirection) },
            )

            InputCard(
                state = state,
                onTextChange = { viewModel.onIntent(TranslatorIntent.InputChanged(it)) },
                onClear = { viewModel.onIntent(TranslatorIntent.Clear) },
                onCopyOriginal = { viewModel.onIntent(TranslatorIntent.CopyOriginal) },
                onPaste = {
                    // Sprint 4C.6: read clipboard text now (this is the only
                    // place that calls getText() — guarded by the visibility
                    // check, so no toast surprise for users with empty clip).
                    val text = clipboardManager.primaryClip
                        ?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)
                        ?.coerceToText(context)
                        ?.toString()
                    if (!text.isNullOrBlank()) {
                        viewModel.onIntent(TranslatorIntent.PasteIntoInput(text))
                    }
                },
                clipboardHasText = clipboardHasText,
            )

            // Translate button + Mic button row.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { viewModel.onIntent(TranslatorIntent.Translate) },
                    enabled = state.inputText.isNotBlank() && !state.isTranslating,
                    modifier = Modifier.weight(1f),
                ) {
                    if (state.isTranslating) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.height(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Text("Перевод…", modifier = Modifier.padding(start = 8.dp))
                    } else {
                        Text("→ ${state.targetLangLabel()}")
                    }
                }

                MicButton(
                    state = state.micState,
                    onTap = { viewModel.onIntent(TranslatorIntent.MicTapped) },
                )
            }

            OutputCard(
                state = state,
                onPlayTts = { viewModel.onIntent(TranslatorIntent.PlayTtsTapped) },
                onStopTts = { viewModel.onIntent(TranslatorIntent.StopTtsTapped) },
                onCopy = { viewModel.onIntent(TranslatorIntent.CopyTranslation) },
                onShareVoice = { viewModel.onIntent(TranslatorIntent.ShareVoiceTapped) },
                onShareTextMode = { mode ->
                    viewModel.onIntent(TranslatorIntent.ShareWithMode(mode))
                },
            )
        }
    }

    // Sprint 4C: History Light bottom sheet — rendered as a sibling of the
    // Scaffold so it overlays the translator without being clipped by the
    // top app bar. Visibility is local Composable state.
    if (historyVisible) {
        HistoryBottomSheet(
            onDismiss = { historyVisible = false },
            onReloadEntry = { entry -> viewModel.reloadFromHistory(entry) },
        )
    }
}

@Composable
private fun DirectionSwitchRow(
    direction: Direction,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (fromFlag, toFlag, label) = when (direction) {
        Direction.RU_TO_PARTNER -> Triple("🇷🇺", "🇵🇱", "RU → PL")
        Direction.PARTNER_TO_RU -> Triple("🇵🇱", "🇷🇺", "PL → RU")
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(fromFlag, style = MaterialTheme.typography.headlineMedium)
            Text("→", style = MaterialTheme.typography.titleMedium)
            Text(toFlag, style = MaterialTheme.typography.headlineMedium)

            Spacer(Modifier.weight(1f))

            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )

            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = "Поменять направление",
                )
            }
        }
    }
}

@Composable
private fun InputCard(
    state: PaneState,
    onTextChange: (String) -> Unit,
    onClear: () -> Unit,
    onCopyOriginal: () -> Unit,
    onPaste: () -> Unit,
    clipboardHasText: Boolean,
    modifier: Modifier = Modifier,
) {
    val (flag, label) = state.sourceLangFlagAndLabel()
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "$flag $label",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.weight(1f))
                // Sprint 4C.6: Paste from clipboard. Adaptive — only visible
                // when clipboard actually contains text. Дашкин direction:
                // "interface появляется тогда когда нужен". Icon shows up
                // after user copies in another app and switches back to
                // Dashka; tapping inserts (with smart append/replace logic
                // in ViewModel.pasteIntoInput).
                if (clipboardHasText) {
                    IconButton(
                        onClick = onPaste,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Вставить из буфера",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                }
                if (state.inputText.isNotEmpty()) {
                    // Sprint 4C.5: Copy original input text. Communication
                    // primitive — users routinely forward their own phrase,
                    // compare original vs translation, paste elsewhere.
                    // Icon-only, 32dp (smaller than translation pane copy
                    // since this is a secondary action), same ContentCopy
                    // glyph for visual consistency with the translation copy.
                    IconButton(
                        onClick = onCopyOriginal,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Скопировать оригинал",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                    TextButton(onClick = onClear) {
                        Text("Очистить ✕")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.inputText,
                onValueChange = onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                placeholder = {
                    Text(
                        when {
                            state.micState == MicState.Listening ->
                                "Слушаю…"
                            state.direction == Direction.RU_TO_PARTNER ->
                                "Введите текст по-русски или нажмите 🎤"
                            else ->
                                "Wprowadź tekst po polsku lub naciśnij 🎤"
                        }
                    )
                },
                supportingText = {
                    Text(
                        "${state.inputText.length} / ${TranslateUseCase.MAX_TEXT_LENGTH}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                isError = state.inputText.length > TranslateUseCase.MAX_TEXT_LENGTH,
                readOnly = state.micState is MicState.Listening,
            )
        }
    }
}

@Composable
private fun OutputCard(
    state: PaneState,
    onPlayTts: () -> Unit,
    onStopTts: () -> Unit,
    onCopy: () -> Unit,
    onShareVoice: () -> Unit,
    onShareTextMode: (com.solar.dashka.domain.model.ShareMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val (flag, label) = state.targetLangFlagAndLabel()
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "$flag $label",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                if (state.translatedText.isNotBlank()) {
                    // Sprint 4B.1: explicit 4-button row — discoverable UX.
                    // 📋 Copy → 🔊 Voice Share → 📤 Text Share Menu → 🔈 Play
                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Скопировать",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                    IconButton(
                        onClick = onShareVoice,
                        enabled = !state.isPreparingShareVoice,
                        modifier = Modifier.size(40.dp),
                    ) {
                        if (state.isPreparingShareVoice) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Отправить голос",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                        }
                    }
                    SharePopoverMenu(
                        isPreparingVoice = state.isPreparingShareVoice,
                        onPickMode = onShareTextMode,
                    )
                    PlayTtsButton(
                        state = state.ttsState,
                        onPlay = onPlayTts,
                        onStop = onStopTts,
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Box(modifier = Modifier.heightIn(min = 80.dp)) {
                if (state.translatedText.isBlank()) {
                    Text(
                        "…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    )
                } else {
                    Text(
                        state.translatedText,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

/* --- Helpers ------------------------------------------------------------- */

private fun PaneState.sourceLangFlagAndLabel(): Pair<String, String> = when (direction) {
    Direction.RU_TO_PARTNER -> LangCode.RU.flag to LangCode.RU.displayName
    Direction.PARTNER_TO_RU -> LangCode.PL.flag to LangCode.PL.displayName
}

private fun PaneState.targetLangFlagAndLabel(): Pair<String, String> = when (direction) {
    Direction.RU_TO_PARTNER -> LangCode.PL.flag to LangCode.PL.displayName
    Direction.PARTNER_TO_RU -> LangCode.RU.flag to LangCode.RU.displayName
}

private fun PaneState.targetLangLabel(): String = when (direction) {
    Direction.RU_TO_PARTNER -> LangCode.PL.displayName
    Direction.PARTNER_TO_RU -> LangCode.RU.displayName
}
