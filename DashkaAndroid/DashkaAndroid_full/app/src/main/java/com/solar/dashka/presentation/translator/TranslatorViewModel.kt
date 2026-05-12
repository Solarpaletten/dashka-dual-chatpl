package com.solar.dashka.presentation.translator

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solar.dashka.BuildConfig
import com.solar.dashka.data.api.DashkaResult
import com.solar.dashka.data.preferences.UserPreferencesRepository
import com.solar.dashka.data.share.ShareFileBuilder
import com.solar.dashka.data.speech.SpeechErrorCode
import com.solar.dashka.data.speech.SpeechRecognitionResult
import com.solar.dashka.domain.model.Direction
import com.solar.dashka.domain.model.LangCode
import com.solar.dashka.domain.model.MicState
import com.solar.dashka.domain.model.PaneState
import com.solar.dashka.domain.model.ShareMode
import com.solar.dashka.domain.model.TtsState
import com.solar.dashka.domain.model.TtsVoice
import com.solar.dashka.domain.repository.TtsRepository
import com.solar.dashka.domain.usecase.PlayTtsUseCase
import com.solar.dashka.domain.usecase.StartRecognitionUseCase
import com.solar.dashka.domain.usecase.StopRecognitionUseCase
import com.solar.dashka.domain.usecase.StopTtsUseCase
import com.solar.dashka.domain.usecase.TranslateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Sprint 2C v2 — Conversation Mode ViewModel.
 *
 * The conversation flow:
 *   - Tap mic once → continuous session begins
 *   - Speak naturally with pauses; recognizer auto-restarts invisibly
 *   - Repository emits Partial events for live UI updates (committed + currentPartial merged)
 *   - Repository emits SilenceDetected after 1500ms of real silence
 *   - SilenceDetected triggers incremental translate of FULL accumulated text
 *   - Translate runs ASYNC in parallel with STT — neither blocks the other
 *   - Output card refreshes with full new translation each time
 *   - Tap stop → MicState.Idle, Final event, final translate
 *   - Yellow translate button → reconcile translate of current input (no STT)
 *
 * Architectural responsibility split:
 *   - Repository owns speech activity detection (silence timer, restart logic)
 *   - ViewModel owns UI state and translate API orchestration
 */
@HiltViewModel
class TranslatorViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val translateUseCase: TranslateUseCase,
    private val startRecognitionUseCase: StartRecognitionUseCase,
    private val stopRecognitionUseCase: StopRecognitionUseCase,
    private val playTtsUseCase: PlayTtsUseCase,
    private val stopTtsUseCase: StopTtsUseCase,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val shareFileBuilder: ShareFileBuilder,
    private val ttsRepository: TtsRepository,
) : ViewModel() {

    private val partnerLang: LangCode = LangCode.fromCode(BuildConfig.PARTNER_LANG)
        ?: LangCode.PL

    private val _uiState = MutableStateFlow(
        PaneState(
            direction = Direction.RU_TO_PARTNER,
            // Default direction RU→PL targets Polish; female voice (Eve) for
            // contrast with the user's typical male/neutral Russian voice.
            // User can override at any time via the voice picker.
            voice = TtsVoice.EVE,
        )
    )
    val uiState: StateFlow<PaneState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<TranslatorEvent>(replay = 0)
    val events: SharedFlow<TranslatorEvent> = _events.asSharedFlow()

    /** In-flight translation — cancelling stale one prevents clobbering. */
    private var translateJob: Job? = null

    /** In-flight STT collection job — owns the cold flow lifecycle. */
    private var sttJob: Job? = null

    /** In-flight TTS playback job — cancelling stops playback. */
    private var ttsJob: Job? = null

    init {
        // Sprint 4: load persisted voice + autoplay on VM creation.
        // First-launch defaults: EVE + autoplay off.
        viewModelScope.launch {
            val prefs = userPreferencesRepository.loadInitial()
            _uiState.update {
                it.copy(
                    voice = prefs.voice,
                    autoplayEnabled = prefs.autoplayEnabled,
                )
            }
        }
    }

    fun onIntent(intent: TranslatorIntent) {
        when (intent) {
            is TranslatorIntent.InputChanged -> updateInput(intent.text)
            TranslatorIntent.Translate -> performTranslation(isUserInitiated = true)
            TranslatorIntent.ToggleDirection -> toggleDirection()
            TranslatorIntent.Clear -> clear()
            TranslatorIntent.DismissError -> _uiState.update { it.copy(errorMessage = null) }

            TranslatorIntent.MicTapped -> handleMicTap()
            is TranslatorIntent.PermissionResult -> handlePermissionResult(intent.granted)
            is TranslatorIntent.SpeechEvent -> handleSpeechEvent(intent.event)

            TranslatorIntent.PlayTtsTapped -> startTtsPlayback()
            TranslatorIntent.StopTtsTapped -> stopTtsPlayback()
            is TranslatorIntent.TtsEvent -> handleTtsEvent(intent.state)

            is TranslatorIntent.VoiceSelected -> selectVoice(intent.voice)
            is TranslatorIntent.ToggleAutoplay -> toggleAutoplay(intent.enabled)

            TranslatorIntent.CopyTranslation -> copyTranslation()

            TranslatorIntent.ShareVoiceTapped -> shareVoiceDirect()
            TranslatorIntent.ShareTextTapped -> openShareSheet()
            TranslatorIntent.DismissShareSheet -> dismissShareSheet()
            is TranslatorIntent.ShareWithMode -> shareWithMode(intent.mode)
        }
    }

    /* ─────────── Text translation ─────────── */

    private fun updateInput(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    private fun toggleDirection() {
        // Cancel any active recognition before switching language.
        if (_uiState.value.micState != MicState.Idle) {
            sttJob?.cancel()
            sttJob = null
        }
        translateJob?.cancel()
        _uiState.update {
            it.copy(
                direction = it.direction.toggled(),
                inputText = "",
                translatedText = "",
                errorMessage = null,
                micState = MicState.Idle,
            )
        }
    }

    private fun clear() {
        translateJob?.cancel()
        _uiState.update {
            it.copy(inputText = "", translatedText = "", errorMessage = null)
        }
    }

    /**
     * Run a translation. Cancels any in-flight translate (last-write-wins).
     *
     * @param isUserInitiated true when triggered by the yellow Translate button
     *                        — shows isTranslating spinner. False during the
     *                        background incremental conversation flow — silently
     *                        updates output without UI flicker.
     */
    private fun performTranslation(isUserInitiated: Boolean) {
        val current = _uiState.value
        if (current.inputText.isBlank()) return

        translateJob?.cancel()
        translateJob = viewModelScope.launch {
            if (isUserInitiated) {
                _uiState.update { it.copy(isTranslating = true, errorMessage = null) }
            }

            val (source, target) = when (current.direction) {
                Direction.RU_TO_PARTNER -> LangCode.RU to partnerLang
                Direction.PARTNER_TO_RU -> partnerLang to LangCode.RU
            }

            when (val result = translateUseCase(current.inputText, source, target)) {
                is DashkaResult.Success -> {
                    _uiState.update {
                        it.copy(
                            translatedText = result.data.translatedText,
                            isTranslating = false,
                        )
                    }
                    // Sprint 3C: if autoplay is enabled and STT is idle,
                    // automatically play the new translation. Echo guard inside.
                    maybeAutoplay()
                }
                is DashkaResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isTranslating = false,
                            // Only surface translation errors during user-initiated
                            // taps. Background incremental fails are silent — the
                            // next pause will retry, and we don't want to spam the
                            // user during a continuous conversation.
                            errorMessage = if (isUserInitiated) result.toUserMessage() else null,
                        )
                    }
                }
            }
        }
    }

    /* ─────────── Voice input — Conversation Mode ─────────── */

    private fun handleMicTap() {
        val state = _uiState.value
        when (state.micState) {
            MicState.Idle, is MicState.Error -> startRecognitionFlow()
            MicState.Listening, MicState.Processing -> {
                // Manual stop — Rule #10: end of conversation turn.
                stopRecognitionUseCase()
            }
            MicState.RequestingPermission -> Unit
        }
    }

    private fun startRecognitionFlow() {
        if (!startRecognitionUseCase.isAvailable()) {
            _uiState.update {
                it.copy(
                    micState = MicState.Error("Распознавание речи недоступно на этом устройстве"),
                )
            }
            return
        }

        val granted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            _uiState.update { it.copy(micState = MicState.RequestingPermission) }
            viewModelScope.launch {
                _events.emit(TranslatorEvent.RequestRecordAudioPermission)
            }
            return
        }

        startListeningForReal()
    }

    private fun startListeningForReal() {
        sttJob?.cancel()
        translateJob?.cancel()
        val sourceLang = when (_uiState.value.direction) {
            Direction.RU_TO_PARTNER -> LangCode.RU
            Direction.PARTNER_TO_RU -> partnerLang
        }
        // Clear previous transcript to start a fresh conversation turn.
        _uiState.update {
            it.copy(
                inputText = "",
                translatedText = "",
                micState = MicState.Listening,
                errorMessage = null,
            )
        }
        sttJob = viewModelScope.launch {
            startRecognitionUseCase(sourceLang).collect { event ->
                onIntent(TranslatorIntent.SpeechEvent(event))
            }
        }
    }

    private fun handlePermissionResult(granted: Boolean) {
        if (granted) {
            startListeningForReal()
        } else {
            _uiState.update {
                it.copy(
                    micState = MicState.Error("Нужно разрешение на запись звука"),
                )
            }
        }
    }

    private fun handleSpeechEvent(event: SpeechRecognitionResult) {
        when (event) {
            SpeechRecognitionResult.ReadyForSpeech,
            SpeechRecognitionResult.BeginningOfSpeech -> {
                // Rule #8: keep MicState stable in Listening — no flicker.
                _uiState.update { it.copy(micState = MicState.Listening) }
            }
            is SpeechRecognitionResult.Partial -> {
                // Rule #3 from Leanid: original text continuously accumulates.
                // visibleText = committed + current partial — already merged in Repository.
                _uiState.update {
                    it.copy(
                        inputText = event.text,
                        micState = MicState.Listening,
                    )
                }
                // Note: NO debounce trigger here. Translate is driven by
                // SilenceDetected from Repository's speech-activity timer.
            }
            SpeechRecognitionResult.SilenceDetected -> {
                // Rule #4 from Leanid: real silence triggers incremental translate
                // of the FULL accumulated text. No spinner, no UI flicker.
                performTranslation(isUserInitiated = false)
            }
            is SpeechRecognitionResult.Final -> {
                // Rule #10: manual stop = end of conversation turn.
                _uiState.update {
                    it.copy(
                        inputText = event.text,
                        micState = MicState.Idle,
                    )
                }
                if (event.text.isNotBlank()) {
                    performTranslation(isUserInitiated = false)
                }
            }
            is SpeechRecognitionResult.Error -> {
                // Rule #5/#6: NO_MATCH/SPEECH_TIMEOUT/CLIENT/RECOGNIZER_BUSY never reach here.
                // Only fatal errors (NETWORK, AUDIO, SERVER, etc.) get here.
                _uiState.update {
                    it.copy(
                        micState = MicState.Error(event.code.toUserMessage()),
                    )
                }
            }
        }
    }

    /* ─────────── TTS playback (Sprint 3A) ─────────── */

    private fun startTtsPlayback() {
        val state = _uiState.value
        if (state.translatedText.isBlank()) return

        // Cancel any in-flight playback before starting a new one.
        ttsJob?.cancel()

        val targetLang = when (state.direction) {
            Direction.RU_TO_PARTNER -> partnerLang
            Direction.PARTNER_TO_RU -> LangCode.RU
        }

        ttsJob = viewModelScope.launch {
            playTtsUseCase(
                text = state.translatedText,
                language = targetLang,
                voice = state.voice,
            ).collect { ttsState ->
                onIntent(TranslatorIntent.TtsEvent(ttsState))
            }
        }
    }

    private fun stopTtsPlayback() {
        ttsJob?.cancel()
        stopTtsUseCase()
        _uiState.update { it.copy(ttsState = TtsState.Idle) }
    }

    private fun handleTtsEvent(state: TtsState) {
        _uiState.update { it.copy(ttsState = state) }
        // Errors are surfaced via the snackbar on the screen — no auto-dismiss
        // here, the user dismisses by tapping or interacting.
    }

    /* ─────────── Voice picker (Sprint 3B) ─────────── */

    /**
     * User explicitly picked a voice. Persists for the session in PaneState
     * AND across app restarts via DataStore (Sprint 4).
     * If TTS is currently playing, we don't interrupt — next playback will use
     * the new voice. Stop+restart is the user's choice (matches Sprint 3A
     * Дашкин manual-only principle).
     */
    private fun selectVoice(voice: TtsVoice) {
        _uiState.update { it.copy(voice = voice) }
        viewModelScope.launch {
            userPreferencesRepository.setVoice(voice)
        }
    }

    /* ─────────── Autoplay (Sprint 3C) ─────────── */

    /**
     * User toggled autoplay. When enabled, successful translations will
     * automatically trigger TTS playback, BUT ONLY if STT is not actively
     * recording (mutual exclusion to prevent echo loop).
     *
     * Defaults to false at app start — language training is the typical
     * first-use scenario. Sprint 4: persisted via DataStore.
     */
    private fun toggleAutoplay(enabled: Boolean) {
        _uiState.update { it.copy(autoplayEnabled = enabled) }
        viewModelScope.launch {
            userPreferencesRepository.setAutoplayEnabled(enabled)
        }
    }

    /* ─────────── Share & Clipboard (Sprint 4) ─────────── */

    /**
     * Copy current translation to clipboard. No-op if empty.
     * Screen handles the actual ClipboardManager call via TranslatorEvent.
     */
    private fun copyTranslation() {
        val text = _uiState.value.translatedText
        if (text.isBlank()) return
        viewModelScope.launch {
            _events.emit(TranslatorEvent.CopyToClipboard(text))
        }
    }

    /* ─────────── Share (Sprint 4B.1 — refined UX) ─────────── */

    /**
     * Sprint 4B.1: dedicated 🔊 voice-share button — single tap, immediate.
     * No bottom sheet. Prepares MP3 (cache hit = instant; miss = ~1-2s) then
     * fires ACTION_SEND with audio/mpeg.
     *
     * Дашкин принцип: "voice deserves dedicated action".
     */
    private fun shareVoiceDirect() {
        val state = _uiState.value
        val text = state.translatedText
        if (text.isBlank()) return
        shareVoiceInternal(text, state)
    }

    /**
     * Sprint 4B.1: 📤 text-share button opens explicit bottom sheet.
     * No long-press, no hidden UX — explicit interface.
     */
    private fun openShareSheet() {
        if (_uiState.value.translatedText.isBlank()) return
        _uiState.update { it.copy(shareSheetVisible = true) }
    }

    /** User dismissed sheet without picking. */
    private fun dismissShareSheet() {
        _uiState.update { it.copy(shareSheetVisible = false) }
    }

    /**
     * User picked a mode from the text-share bottom sheet.
     *
     * Sprint 4B.1: bottom sheet now shows only 2 options (VoiceOnly moved
     * to its dedicated button):
     *   TextOnly      → just text
     *   TextAndVoice  → both files (multi-share)
     */
    private fun shareWithMode(mode: ShareMode) {
        val state = _uiState.value
        val text = state.translatedText
        if (text.isBlank()) return

        // Close sheet first.
        _uiState.update { it.copy(shareSheetVisible = false) }

        when (mode) {
            ShareMode.TextOnly -> shareTextOnly(text)
            ShareMode.TextAndVoice -> shareTextAndVoice(text, state)
        }
    }

    private fun shareTextOnly(text: String) {
        viewModelScope.launch {
            _events.emit(TranslatorEvent.ShareText(text))
        }
    }

    private fun shareVoiceInternal(text: String, state: PaneState) {
        val targetLang = currentTargetLang(state)
        viewModelScope.launch {
            _uiState.update { it.copy(isPreparingShareVoice = true) }
            try {
                ensureVoiceInCache(text, targetLang, state.voice)
                val voiceUri = shareFileBuilder.buildVoiceFile(text, targetLang, state.voice)
                if (voiceUri == null) {
                    _uiState.update { it.copy(errorMessage = "Не удалось подготовить аудио.") }
                    return@launch
                }
                _events.emit(
                    TranslatorEvent.ShareFiles(
                        uris = listOf(voiceUri),
                        mimeType = "audio/mpeg",
                    )
                )
            } finally {
                _uiState.update { it.copy(isPreparingShareVoice = false) }
            }
        }
    }

    private fun shareTextAndVoice(text: String, state: PaneState) {
        val targetLang = currentTargetLang(state)
        viewModelScope.launch {
            _uiState.update { it.copy(isPreparingShareVoice = true) }
            try {
                ensureVoiceInCache(text, targetLang, state.voice)
                val txtUri = shareFileBuilder.buildTextFile(text)
                val voiceUri = shareFileBuilder.buildVoiceFile(text, targetLang, state.voice)
                val uris = buildList {
                    add(txtUri)
                    voiceUri?.let { add(it) }
                }
                // Mixed types — use */* for safety; receivers handle selection.
                _events.emit(
                    TranslatorEvent.ShareFiles(
                        uris = uris,
                        mimeType = "*/*",
                        accompanyingText = text,
                    )
                )
            } finally {
                _uiState.update { it.copy(isPreparingShareVoice = false) }
            }
        }
    }

    /**
     * Ensure MP3 is in TTS cache. No-op if already cached. On error,
     * sets errorMessage and returns — caller should bail.
     */
    private suspend fun ensureVoiceInCache(
        text: String,
        language: LangCode,
        voice: TtsVoice,
    ) {
        when (val res = ttsRepository.prefetch(text, language, voice)) {
            is DashkaResult.Success -> Unit
            is DashkaResult.Error -> {
                _uiState.update { it.copy(errorMessage = res.toUserMessage()) }
            }
        }
    }

    private fun currentTargetLang(state: PaneState): LangCode = when (state.direction) {
        Direction.RU_TO_PARTNER -> partnerLang
        Direction.PARTNER_TO_RU -> LangCode.RU
    }

    /**
     * Conditional autoplay trigger called from performTranslation success path.
     * Echo guard: skip if STT is in any active state (Listening, Processing).
     */
    private fun maybeAutoplay() {
        val state = _uiState.value
        if (!state.autoplayEnabled) return
        if (state.micState != MicState.Idle) return  // echo guard
        if (state.translatedText.isBlank()) return
        startTtsPlayback()
    }

    override fun onCleared() {
        super.onCleared()
        sttJob?.cancel()
        translateJob?.cancel()
        ttsJob?.cancel()
        stopTtsUseCase()
    }
}

/* ─────────── Helpers ─────────── */

sealed interface TranslatorEvent {
    data object RequestRecordAudioPermission : TranslatorEvent

    /** Sprint 4: copy translation text to system clipboard. */
    data class CopyToClipboard(val text: String) : TranslatorEvent

    /** Sprint 4: launch Android Share Sheet with translation text. */
    data class ShareText(val text: String) : TranslatorEvent

    /**
     * Sprint 4B: launch Android Share Sheet with one or more file URIs.
     * - Single file → ACTION_SEND
     * - Multiple files → ACTION_SEND_MULTIPLE
     * Optional accompanying text appears as EXTRA_TEXT.
     */
    data class ShareFiles(
        val uris: List<android.net.Uri>,
        val mimeType: String,
        val accompanyingText: String? = null,
    ) : TranslatorEvent
}

private fun DashkaResult.Error.toUserMessage(): String = when (this) {
    DashkaResult.Error.Unauthorized ->
        "Неверный токен доступа. Проверьте DASHKA_API_TOKEN."
    DashkaResult.Error.NetworkError ->
        "Нет подключения к интернету."
    DashkaResult.Error.Timeout ->
        "Сервер не ответил вовремя."
    is DashkaResult.Error.Server ->
        message
    is DashkaResult.Error.Unknown ->
        throwable.message ?: "Неизвестная ошибка"
}

private fun SpeechErrorCode.toUserMessage(): String = when (this) {
    SpeechErrorCode.AUDIO ->
        "Ошибка записи звука."
    SpeechErrorCode.CLIENT ->
        "Ошибка распознавания. Попробуйте снова."
    SpeechErrorCode.INSUFFICIENT_PERMISSIONS ->
        "Нужно разрешение на запись звука."
    SpeechErrorCode.NETWORK ->
        "Распознавание требует интернета."
    SpeechErrorCode.NETWORK_TIMEOUT ->
        "Распознавание не успело за таймаут."
    SpeechErrorCode.NO_MATCH ->
        "Не удалось распознать речь."  // never shown — Repository suppresses
    SpeechErrorCode.RECOGNIZER_BUSY ->
        "Распознаватель занят. Попробуйте через секунду."
    SpeechErrorCode.SERVER ->
        "Сервер распознавания недоступен."
    SpeechErrorCode.SPEECH_TIMEOUT ->
        "Не услышал речь."  // never shown — Repository suppresses
    SpeechErrorCode.LANGUAGE_NOT_SUPPORTED ->
        "Язык не поддерживается на этом устройстве."
    SpeechErrorCode.LANGUAGE_UNAVAILABLE ->
        "Языковой пакет не установлен. Проверьте Settings → Voice."
    SpeechErrorCode.UNKNOWN ->
        "Не удалось распознать речь."
}
