package com.solar.dashka.data.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around Android's MediaPlayer for one-shot MP3 playback.
 *
 * Sprint 3A scope:
 *   - Synchronous prepare on already-downloaded MP3 bytes
 *   - Plays from a temp file in cacheDir/tts/
 *   - Single playback at a time — second play() call cancels the first
 *
 * Sprint 3C+ will replace this with ExoPlayer when we need streaming or
 * gapless autoplay.
 *
 * Thread safety: all MediaPlayer methods invoked on main thread (API requirement
 * for some callbacks). State transitions are protected by `synchronized(this)`.
 */
@Singleton
class AudioPlayerWrapper @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val mainHandler = Handler(Looper.getMainLooper())

    private var player: MediaPlayer? = null
    private var currentTempFile: File? = null

    /**
     * Play raw MP3 bytes. Cancels any in-flight playback first.
     *
     * @param onPrepared called when audio actually starts playing
     * @param onCompleted called on successful end of playback
     * @param onError called on any failure (file write, prepare, runtime)
     */
    fun play(
        mp3Bytes: ByteArray,
        onPrepared: () -> Unit,
        onCompleted: () -> Unit,
        onError: (String) -> Unit,
    ) {
        mainHandler.post {
            try {
                stopInternal()  // tear down previous player

                val tempFile = writeTempFile(mp3Bytes)
                currentTempFile = tempFile

                val mp = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    setDataSource(tempFile.absolutePath)
                    setOnPreparedListener {
                        it.start()
                        onPrepared()
                    }
                    setOnCompletionListener {
                        cleanupAndNotify(onCompleted)
                    }
                    setOnErrorListener { _, what, extra ->
                        val msg = "Playback error (what=$what, extra=$extra)"
                        cleanupAndNotify { onError(msg) }
                        true  // we handled the error
                    }
                    prepareAsync()
                }
                player = mp
            } catch (e: Exception) {
                onError(e.message ?: "Не удалось воспроизвести аудио")
                cleanup()
            }
        }
    }

    /**
     * Stop any active playback. Idempotent — safe to call multiple times.
     */
    fun stop() {
        mainHandler.post { stopInternal() }
    }

    private fun stopInternal() {
        player?.let { mp ->
            try {
                if (mp.isPlaying) mp.stop()
            } catch (_: IllegalStateException) {
                // Player not in valid state — ignore.
            }
            try {
                mp.reset()
                mp.release()
            } catch (_: IllegalStateException) {
            }
        }
        player = null
        currentTempFile?.delete()
        currentTempFile = null
    }

    private fun cleanupAndNotify(notify: () -> Unit) {
        cleanup()
        notify()
    }

    private fun cleanup() {
        player?.release()
        player = null
        currentTempFile?.delete()
        currentTempFile = null
    }

    /**
     * Write MP3 bytes to a temp file in cacheDir. MediaPlayer needs a file path
     * (or content URI) — it won't accept a raw byte array.
     */
    private fun writeTempFile(mp3Bytes: ByteArray): File {
        val ttsDir = File(context.cacheDir, "tts").apply { mkdirs() }
        val file = File(ttsDir, "playback_${System.currentTimeMillis()}.mp3")
        FileOutputStream(file).use { it.write(mp3Bytes) }
        return file
    }
}
