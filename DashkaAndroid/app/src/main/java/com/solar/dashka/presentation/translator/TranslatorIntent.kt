package com.solar.dashka.presentation.translator

import com.solar.dashka.data.speech.SpeechRecognitionResult
import com.solar.dashka.domain.model.ShareMode
import com.solar.dashka.domain.model.TtsState
import com.solar.dashka.domain.model.TtsVoice

/**
 * Sprint 3B intents — extended Sprint 3A with voice picker.
 */
sealed interface TranslatorIntent {
    /* ------ Sprint 1 (text translation) ------ */
    data class InputChanged(val text: String) : TranslatorIntent
    data object Translate : TranslatorIntent
    data object ToggleDirection : TranslatorIntent
    data object Clear : TranslatorIntent
    data object DismissError : TranslatorIntent

    /* ------ Sprint 2A/2C (voice input) ------ */
    data object MicTapped : TranslatorIntent
    data class PermissionResult(val granted: Boolean) : TranslatorIntent
    data class SpeechEvent(val event: SpeechRecognitionResult) : TranslatorIntent

    /* ------ Sprint 3A (TTS playback) ------ */
    data object PlayTtsTapped : TranslatorIntent
    data object StopTtsTapped : TranslatorIntent
    data class TtsEvent(val state: TtsState) : TranslatorIntent

    /* ------ Sprint 3B (voice picker) ------ */

    /** User picked a voice from the dropdown. Persists for the session. */
    data class VoiceSelected(val voice: TtsVoice) : TranslatorIntent

    /* ------ Sprint 3C (autoplay toggle) ------ */

    /** User flipped the Autoplay switch. */
    data class ToggleAutoplay(val enabled: Boolean) : TranslatorIntent

    /* ------ Sprint 4 (share & clipboard) ------ */

    /** User tapped copy — translation goes to clipboard. */
    data object CopyTranslation : TranslatorIntent

    /**
     * Sprint 4C.5: User tapped copy on the input/source pane — original
     * (untranslated) text goes to clipboard.
     *
     * Communication primitive: users routinely need to forward their own
     * original phrase, compare original vs translation side-by-side, or
     * paste it elsewhere unchanged. NOT a Pro feature — core to daily use.
     */
    data object CopyOriginal : TranslatorIntent

    /**
     * Sprint 4C.6: User tapped paste — clipboard text goes into input.
     *
     * Smart behavior:
     *   - Empty input → replace (text becomes the clipboard content)
     *   - Has input → append with single-space separator (compound message)
     *   - Whitespace normalized (no double spaces, no leading/trailing)
     *   - No newlines — translator flow requires inline text
     *
     * The clipboard text is read in the Composable layer (where
     * LocalClipboardManager lives) and passed in here as payload.
     */
    data class PasteIntoInput(val clipboardText: String) : TranslatorIntent

    /* ------ Sprint 4B.3 (refined: popover replaces bottom sheet) ------ */

    /**
     * Dedicated 🔊 voice-share button — single tap, immediate.
     * Prepares MP3 if needed → opens system Share Sheet with audio.
     */
    data object ShareVoiceTapped : TranslatorIntent

    /**
     * Sprint 4B.3: triggered when user picks a mode from the SharePopoverMenu.
     * Popover visibility is now local Composable state (no ViewModel needed
     * for visibility management — Telegram/macOS-style context menu pattern).
     */
    data class ShareWithMode(val mode: ShareMode) : TranslatorIntent
}
