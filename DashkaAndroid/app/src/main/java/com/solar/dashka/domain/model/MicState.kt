package com.solar.dashka.domain.model

/**
 * Microphone state — drives the mic button's visual state and the recognition flow.
 *
 * Transitions:
 *   Idle → RequestingPermission → Idle (denied) | Listening (granted)
 *   Idle → Listening → Processing → Idle
 *   Any → Error → Idle
 *
 * Sprint 2A: STT only. TTS playback states will live separately in Sprint 3.
 */
sealed interface MicState {
    /** Idle — mic button visible, tap-ready. */
    data object Idle : MicState

    /** RECORD_AUDIO permission dialog pending. */
    data object RequestingPermission : MicState

    /** Actively listening — partial transcripts streaming in. */
    data object Listening : MicState

    /** User stopped or end-of-speech detected; finalizing transcript. */
    data object Processing : MicState

    /** Recognition or permission error — message shown via snackbar, then Idle. */
    data class Error(val message: String) : MicState
}
