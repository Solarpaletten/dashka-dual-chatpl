package com.solar.dashka.domain.model

/**
 * TTS playback state — drives the play button's visual state.
 *
 * Sprint 3A: minimal viable. Sprint 3C will add Autoplay; Sprint 3D will add
 * cross-feature interruption (e.g. user starts speaking while TTS is playing).
 */
sealed interface TtsState {
    /** Idle — play button visible, tap-ready. */
    data object Idle : TtsState

    /** Fetching MP3 from backend (or pulling from cache). */
    data object Loading : TtsState

    /** Audio is playing — button shows stop icon. */
    data object Playing : TtsState

    /** Error — message shown via snackbar, then Idle. */
    data class Error(val message: String) : TtsState
}
