package com.solar.dashka.data.share

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.solar.dashka.data.tts.TtsCache
import com.solar.dashka.domain.model.LangCode
import com.solar.dashka.domain.model.TtsVoice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sprint 4B.2 — builds shareable MP3 files for the share sheet.
 *
 * Design decision (Леанидин refinement):
 *   - Text is NEVER shared as a file — always as Intent.EXTRA_TEXT.
 *     Receiver apps (Telegram, WhatsApp, Email) paste it directly into
 *     the message field. No attachment-to-be-opened.
 *   - Only MP3 files are built here.
 *   - "Text + Voice" mode = ONE MP3 attachment + EXTRA_TEXT caption.
 *     Mirrors how Telegram handles voice notes with captions.
 *
 * File location: {cacheDir}/share/
 * File naming:   dashka_translation_2026-05-08_17-30-15.mp3
 *
 * URI exposure: via FileProvider (declared in AndroidManifest.xml).
 * Authority: ${applicationId}.fileprovider  →  com.solar.dashka.fileprovider
 *
 * Lifecycle: files are short-lived. Written immediately before share,
 * Android cleans up cacheDir/ under storage pressure.
 *
 * IMPORTANT: this builder REUSES the TTS cache. If the user already played
 * back this translation (cache hit), the MP3 is in TtsCache as bytes — we
 * just write them to a file. No second backend call.
 */
@Singleton
class ShareFileBuilder @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val ttsCache: TtsCache,
) {

    private val timestampFormat = SimpleDateFormat(
        "yyyy-MM-dd_HH-mm-ss",
        Locale.US,
    )

    /**
     * Build an MP3 file from cached TTS bytes. Returns null if the audio is
     * not in cache — caller must prefetch first.
     */
    suspend fun buildVoiceFile(
        text: String,
        language: LangCode,
        voice: TtsVoice,
    ): Uri? = withContext(Dispatchers.IO) {
        val mp3Bytes = ttsCache.get(text, language.code, voice.id)
            ?: return@withContext null

        val shareDir = ensureShareDir()
        val filename = "dashka_translation_${timestamp()}.mp3"
        val file = File(shareDir, filename)
        FileOutputStream(file).use { it.write(mp3Bytes) }
        getUri(file)
    }

    private fun ensureShareDir(): File =
        File(context.cacheDir, "share").apply { mkdirs() }

    private fun getUri(file: File): Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )

    private fun timestamp(): String = timestampFormat.format(Date())
}
