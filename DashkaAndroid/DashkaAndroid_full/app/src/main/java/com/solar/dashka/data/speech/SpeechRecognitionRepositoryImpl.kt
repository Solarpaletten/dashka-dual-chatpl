package com.solar.dashka.data.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.solar.dashka.domain.model.LangCode
import com.solar.dashka.domain.repository.SpeechRecognitionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sprint 2C v4 — Conversation Mode with hardened session lifetime.
 *
 * KEY INVARIANT: a recording session ends EXACTLY ONCE per turn, and only on:
 *   1. User taps the stop button (yellow → mic toggle) — userStopRequested = true
 *   2. Genuinely fatal device error (NETWORK, AUDIO, INSUFFICIENT_PERMISSIONS,
 *      LANGUAGE_NOT_SUPPORTED, LANGUAGE_UNAVAILABLE)
 *   3. 50+ consecutive silent-restartable errors without ANY healthy event
 *      (basically: device's recognizer service is broken)
 *
 * Silence is NEVER an end-of-session signal. The recognizer keeps restarting
 * invisibly for as long as the user wants. This supports use cases like:
 *   - 50-minute YouTube news streams with synchronous translation
 *   - Live interpreter mode for meetings
 *   - Long monologues with natural pauses
 *
 * Rules (per Leanid's Conversation Mode spec, refined):
 *   1. Mic turns on once per user tap.
 *   2. Session lives long across multiple recognizer restarts.
 *   3. Original transcript continuously accumulates (committed + partial split).
 *   4. SilenceDetected event emitted on real silence — drives incremental translate.
 *      DOES NOT END THE SESSION. Mic stays in Listening.
 *   5. Silence is NEVER an error.
 *   6. NO_MATCH / SPEECH_TIMEOUT / CLIENT / RECOGNIZER_BUSY / SERVER / UNKNOWN
 *      NEVER reach the UI as Error during normal operation.
 *   7. Recognition restart is invisible — UI stays in Listening state.
 *   8. UI session does not flash or reset between recognizer instances.
 *   9. Yellow translate button = final reconcile (handled by ViewModel).
 *  10. Manual stop = end of conversation turn.
 */
@Singleton
class SpeechRecognitionRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SpeechRecognitionRepository {

    private val mainHandler = Handler(Looper.getMainLooper())

    private var recognizer: SpeechRecognizer? = null

    @Volatile
    private var userStopRequested: Boolean = false

    /** Finalized utterances — appended on each onResults. Survives recognizer restarts. */
    private var committedText: String = ""

    /** Live partial from current utterance — replaced on each onPartialResults. */
    private var currentPartial: String = ""

    /** True after the silence timer has fired and emitted SilenceDetected.
     *  Reset to false on next speech activity. Prevents repeated emits. */
    private var silenceFlagged: Boolean = false

    /**
     * Counter for consecutive silent-restartable errors (CLIENT, SERVER, BUSY,
     * UNKNOWN). Reset on any healthy event (Partial, Result, NO_MATCH, TIMEOUT).
     * If exceeds MAX_CONSECUTIVE_ERRORS, the device is genuinely broken and
     * we surface the error.
     *
     * Tuned high (50) for long sessions (e.g. 50-minute YouTube news with
     * synchronous translate). One healthy event resets the counter, so we
     * only close on truly persistent failure.
     */
    private var consecutiveSilentErrors: Int = 0

    private var sessionIntent: Intent? = null
    private var sessionListener: RecognitionListener? = null

    override fun isAvailable(): Boolean =
        SpeechRecognizer.isRecognitionAvailable(context)

    override fun startListening(language: LangCode): Flow<SpeechRecognitionResult> =
        callbackFlow {
            // Reset session state.
            userStopRequested = false
            committedText = ""
            currentPartial = ""
            silenceFlagged = false
            consecutiveSilentErrors = 0
            activeScope = this

            sessionIntent = buildRecognitionIntent(language.speechLocale)
            sessionListener = createSessionListener(this)

            mainHandler.post {
                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    trySend(
                        SpeechRecognitionResult.Error(
                            code = SpeechErrorCode.UNKNOWN,
                            rawCode = -1,
                        )
                    )
                    close()
                    return@post
                }
                startNewRecognizer()
            }

            awaitClose {
                mainHandler.post {
                    cancelSilenceTimer()
                    recognizer?.destroy()
                    recognizer = null
                    sessionIntent = null
                    sessionListener = null
                    activeScope = null
                }
            }
        }.flowOn(Dispatchers.Main.immediate)

    override fun stopListening() {
        userStopRequested = true
        mainHandler.post {
            cancelSilenceTimer()
            recognizer?.stopListening()
        }
    }

    override fun cancel() {
        userStopRequested = true
        mainHandler.post {
            cancelSilenceTimer()
            recognizer?.cancel()
            recognizer?.destroy()
            recognizer = null
        }
    }

    /* ─────────── Session listener factory ─────────── */

    private fun createSessionListener(
        scope: ProducerScope<SpeechRecognitionResult>,
    ): RecognitionListener = object : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {
            // Only emit on the very first ready event — restarts are invisible.
            if (committedText.isEmpty() && currentPartial.isEmpty()) {
                scope.trySend(SpeechRecognitionResult.ReadyForSpeech)
            }
        }

        override fun onBeginningOfSpeech() {
            // Real speech activity → reset silence timer.
            onSpeechActivity()
            scope.trySend(SpeechRecognitionResult.BeginningOfSpeech)
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Skip — too chatty for activity tracking.
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            // Rule #7: don't surface — VAD pause is not a state change.
        }

        override fun onError(error: Int) {
            val mapped = mapErrorCode(error)
            if (userStopRequested) {
                emitFinalAndClose(scope)
                return
            }
            // Rule #5/#6 from Leanid: silence is never an error. UI must NEVER
            // see "Не удалось распознать речь" during a normal conversation.
            //
            // Strategy: be greedy about silent-restart. Only NETWORK/AUDIO/
            // permission/language errors are genuinely fatal. Everything else
            // — including UNKNOWN codes from newer Android versions — gets a
            // silent restart, with a consecutive-error counter to prevent
            // infinite loops on a truly broken device.
            when (mapped) {
                SpeechErrorCode.NO_MATCH,
                SpeechErrorCode.SPEECH_TIMEOUT -> {
                    consecutiveSilentErrors = 0  // these are healthy
                    scheduleRestart(RESTART_DELAY_SHORT_MS)
                }
                SpeechErrorCode.CLIENT,
                SpeechErrorCode.RECOGNIZER_BUSY,
                SpeechErrorCode.SERVER,
                SpeechErrorCode.UNKNOWN -> {
                    // Service-side glitches — silent restart with counter.
                    consecutiveSilentErrors++
                    if (consecutiveSilentErrors >= MAX_CONSECUTIVE_ERRORS) {
                        // Device is genuinely broken — surface and stop.
                        scope.trySend(
                            SpeechRecognitionResult.Error(
                                code = mapped,
                                rawCode = error,
                            )
                        )
                        scope.close()
                    } else {
                        val delay = when (mapped) {
                            SpeechErrorCode.RECOGNIZER_BUSY -> RESTART_DELAY_BUSY_MS
                            SpeechErrorCode.CLIENT -> RESTART_DELAY_CLIENT_MS
                            else -> RESTART_DELAY_GLITCH_MS
                        }
                        scheduleRestart(delay)
                    }
                }
                else -> {
                    // Genuinely fatal: NETWORK, AUDIO, INSUFFICIENT_PERMISSIONS,
                    // LANGUAGE_NOT_SUPPORTED, LANGUAGE_UNAVAILABLE.
                    scope.trySend(
                        SpeechRecognitionResult.Error(
                            code = mapped,
                            rawCode = error,
                        )
                    )
                    scope.close()
                }
            }
        }

        override fun onResults(results: Bundle?) {
            consecutiveSilentErrors = 0  // healthy result resets counter
            val matches = results?.getStringArrayList(
                SpeechRecognizer.RESULTS_RECOGNITION
            )
            val text = matches?.firstOrNull().orEmpty().trim()

            // Rule #2 from Leanid: commit the partial as text becomes final.
            if (text.isNotEmpty()) {
                committedText = appendText(committedText, text)
                currentPartial = ""
                onSpeechActivity()
            }

            if (userStopRequested) {
                emitFinalAndClose(scope)
            } else {
                emitVisibleText(scope)
                // Rule #7: silent auto-restart for the next utterance.
                scheduleRestart(0L)
            }
        }

        override fun onPartialResults(partial: Bundle?) {
            val matches = partial?.getStringArrayList(
                SpeechRecognizer.RESULTS_RECOGNITION
            )
            val text = matches?.firstOrNull().orEmpty().trim()
            if (text.isNotEmpty()) {
                consecutiveSilentErrors = 0  // healthy partial resets counter
                // Rule #2 from Leanid: keep partial separate from committed.
                currentPartial = text
                onSpeechActivity()
                emitVisibleText(scope)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    /* ─────────── Speech activity / silence timer ─────────── */

    private val silenceRunnable = Runnable {
        if (!userStopRequested && !silenceFlagged) {
            silenceFlagged = true
            sessionListenerSafeEmit(SpeechRecognitionResult.SilenceDetected)
        }
    }

    /**
     * Called on every real speech activity event:
     *   - onBeginningOfSpeech
     *   - onPartialResults (text grew)
     *   - onResults (utterance finalized)
     *
     * Resets the silence timer. If timer fires without new activity, emits
     * SilenceDetected ONCE. Subsequent activity unflags and re-arms.
     */
    private fun onSpeechActivity() {
        silenceFlagged = false
        mainHandler.removeCallbacks(silenceRunnable)
        mainHandler.postDelayed(silenceRunnable, SILENCE_DETECT_MS)
    }

    private fun cancelSilenceTimer() {
        mainHandler.removeCallbacks(silenceRunnable)
    }

    /**
     * Helper: emit through the listener's captured scope. The listener already
     * has the scope reference, but the silenceRunnable doesn't — this looks it
     * up via the active session listener, which won't be null while a session
     * is alive.
     *
     * In practice we use a stored reference set in startListening callbackFlow.
     */
    private var activeScope: ProducerScope<SpeechRecognitionResult>? = null

    private fun sessionListenerSafeEmit(event: SpeechRecognitionResult) {
        activeScope?.trySend(event)
    }

    /* ─────────── Recognizer lifecycle ─────────── */

    private fun emitVisibleText(scope: ProducerScope<SpeechRecognitionResult>) {
        val visible = appendText(committedText, currentPartial)
        if (visible.isNotEmpty()) {
            scope.trySend(SpeechRecognitionResult.Partial(visible))
        }
    }

    private fun emitFinalAndClose(scope: ProducerScope<SpeechRecognitionResult>) {
        // On manual stop, fold any uncommitted partial into committed.
        val finalText = appendText(committedText, currentPartial).trim()
        committedText = finalText
        currentPartial = ""
        scope.trySend(
            SpeechRecognitionResult.Final(
                text = finalText,
                confidence = -1f,
            )
        )
        scope.close()
    }

    private fun scheduleRestart(delayMs: Long) {
        mainHandler.postDelayed({
            if (!userStopRequested) {
                startNewRecognizer()
            }
        }, delayMs)
    }

    private fun startNewRecognizer() {
        val intent = sessionIntent ?: return
        val listener = sessionListener ?: return
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(listener)
            startListening(intent)
        }
    }

    private fun buildRecognitionIntent(speechLocale: String): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechLocale)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                30_000L,
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                3_000L,
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                2_000L,
            )
        }

    /**
     * Append two text fragments with a single space, deduplicating overlapping
     * suffix/prefix. Handles the common case where Google echoes the previous
     * partial as final.
     */
    private fun appendText(existing: String, fresh: String): String {
        if (fresh.isBlank()) return existing
        if (existing.isBlank()) return fresh
        // Exact-suffix duplicate (Google sometimes does this).
        if (existing.endsWith(fresh)) return existing
        // If fresh starts with the tail of existing, merge.
        // Edge case: existing = "Привет как дела", fresh = "как дела сегодня" → "Привет как дела сегодня"
        val maxOverlap = minOf(existing.length, fresh.length)
        for (i in maxOverlap downTo 1) {
            if (existing.endsWith(fresh.substring(0, i))) {
                return existing + fresh.substring(i)
            }
        }
        return "$existing $fresh"
    }

    private fun mapErrorCode(error: Int): SpeechErrorCode = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> SpeechErrorCode.AUDIO
        SpeechRecognizer.ERROR_CLIENT -> SpeechErrorCode.CLIENT
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            SpeechErrorCode.INSUFFICIENT_PERMISSIONS
        SpeechRecognizer.ERROR_NETWORK -> SpeechErrorCode.NETWORK
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> SpeechErrorCode.NETWORK_TIMEOUT
        SpeechRecognizer.ERROR_NO_MATCH -> SpeechErrorCode.NO_MATCH
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SpeechErrorCode.RECOGNIZER_BUSY
        SpeechRecognizer.ERROR_SERVER -> SpeechErrorCode.SERVER
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechErrorCode.SPEECH_TIMEOUT
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ->
            SpeechErrorCode.LANGUAGE_NOT_SUPPORTED
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
            SpeechErrorCode.LANGUAGE_UNAVAILABLE
        else -> SpeechErrorCode.UNKNOWN
    }

    private companion object {
        /** Real silence duration before emitting SilenceDetected. */
        const val SILENCE_DETECT_MS = 1_500L

        /** Restart delay after NO_MATCH / SPEECH_TIMEOUT (pure silence). */
        const val RESTART_DELAY_SHORT_MS = 200L

        /** Restart delay after CLIENT error (service hiccup). */
        const val RESTART_DELAY_CLIENT_MS = 500L

        /** Restart delay after generic glitch (SERVER, UNKNOWN). */
        const val RESTART_DELAY_GLITCH_MS = 600L

        /** Restart delay when recognizer service is busy. */
        const val RESTART_DELAY_BUSY_MS = 800L

        /** Max consecutive silent-restartable errors before surfacing.
         *  Tuned high for long YouTube-style sessions — only persistent
         *  device failure should ever hit this. */
        const val MAX_CONSECUTIVE_ERRORS = 50
    }
}
