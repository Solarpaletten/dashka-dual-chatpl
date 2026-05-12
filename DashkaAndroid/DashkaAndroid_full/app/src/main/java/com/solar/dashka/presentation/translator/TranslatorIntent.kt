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

    /* ------ Sprint 4B.1 (refined share UX — explicit voice/text split) ------ */

    /**
     * User tapped the dedicated 🔊 voice-share button. Single tap, immediate
     * action: prepare MP3 if needed → open system Share Sheet with audio.
     */
    data object ShareVoiceTapped : TranslatorIntent

    /**
     * User tapped the 📤 text-share button. Opens explicit bottom sheet
     * with two options: TextOnly | TextAndVoice.
     */
    data object ShareTextTapped : TranslatorIntent

    /** User picked an explicit share mode from the bottom sheet. */
    data class ShareWithMode(val mode: ShareMode) : TranslatorIntent

    /** User dismissed the bottom sheet without picking. */
    data object DismissShareSheet : TranslatorIntent
}
