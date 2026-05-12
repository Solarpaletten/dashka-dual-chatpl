package com.solar.dashka.data.speech

/**
 * Stream events from SpeechRecognizer. The repository exposes a Flow<SpeechRecognitionResult>
 * and the ViewModel collects it and updates UI state.
 *
 * Lifecycle (Conversation Mode):
 *   ReadyForSpeech → BeginningOfSpeech → Partial(*) → SilenceDetected → ...
 *   ... eventually Final on user stop, or Error on fatal failure.
 *   NO_MATCH/SPEECH_TIMEOUT/CLIENT/RECOGNIZER_BUSY are silently restarted by Repository
 *   and never emitted as Error.
 */
sealed interface SpeechRecognitionResult {

    /** Recognizer is initialized and listening — UI shows the active state. */
    data object ReadyForSpeech : SpeechRecognitionResult

    /** User started speaking — first audio detected. */
    data object BeginningOfSpeech : SpeechRecognitionResult

    /** Streaming visible text (committed + current partial) — updates input field live. */
    data class Partial(val text: String) : SpeechRecognitionResult

    /**
     * Real silence detected — no speech activity for the configured threshold.
     * ViewModel triggers an incremental translate of the committed accumulated text.
     * Emitted only once per silence period (debounced internally).
     */
    data object SilenceDetected : SpeechRecognitionResult

    /** Final transcript on user-initiated stop — auto-translate kicks in here. */
    data class Final(val text: String, val confidence: Float) : SpeechRecognitionResult

    /** FATAL recognition error only — see SpeechErrorCode for kind. */
    data class Error(val code: SpeechErrorCode, val rawCode: Int) : SpeechRecognitionResult
}

/**
 * Mapped from android.speech.SpeechRecognizer.ERROR_* constants.
 * See: https://developer.android.com/reference/android/speech/SpeechRecognizer
 */
enum class SpeechErrorCode {
    AUDIO,                  // ERROR_AUDIO — recording failed
    CLIENT,                 // ERROR_CLIENT — usually means recognizer service crash
    INSUFFICIENT_PERMISSIONS,
    NETWORK,                // ERROR_NETWORK
    NETWORK_TIMEOUT,        // ERROR_NETWORK_TIMEOUT
    NO_MATCH,               // ERROR_NO_MATCH — couldn't recognize anything
    RECOGNIZER_BUSY,        // ERROR_RECOGNIZER_BUSY
    SERVER,                 // ERROR_SERVER
    SPEECH_TIMEOUT,         // ERROR_SPEECH_TIMEOUT — user didn't speak
    LANGUAGE_NOT_SUPPORTED, // ERROR_LANGUAGE_NOT_SUPPORTED — RU/PL pack missing
    LANGUAGE_UNAVAILABLE,   // ERROR_LANGUAGE_UNAVAILABLE
    UNKNOWN,
}
