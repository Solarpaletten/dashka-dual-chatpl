package com.solar.dashka.domain.repository

import com.solar.dashka.data.speech.SpeechRecognitionResult
import com.solar.dashka.domain.model.LangCode
import kotlinx.coroutines.flow.Flow

/**
 * Domain abstraction over Android's SpeechRecognizer.
 *
 * Sprint 2A only uses startListening() + stopListening() + the events Flow.
 * The repository owns the SpeechRecognizer lifecycle internally.
 */
interface SpeechRecognitionRepository {

    /**
     * `true` if the device has a recognition service installed (most do; some
     * emulators and AOSP-only ROMs do not).
     */
    fun isAvailable(): Boolean

    /**
     * Stream of recognition events. Cold flow — collecting starts the recognizer,
     * stopping cancels and destroys it. Re-collect for each session.
     *
     * @param language the spoken locale (e.g. RU.speechLocale = "ru-RU")
     */
    fun startListening(language: LangCode): Flow<SpeechRecognitionResult>

    /**
     * Politely ends recording — recognizer will emit a Final result if any speech
     * was captured, or transition to Idle.
     */
    fun stopListening()

    /**
     * Hard cancel — drop everything, no Final emission. Used when ViewModel
     * cleared (configuration change, screen leave).
     */
    fun cancel()
}
