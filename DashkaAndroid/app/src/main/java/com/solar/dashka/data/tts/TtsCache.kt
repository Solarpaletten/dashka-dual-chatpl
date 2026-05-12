package com.solar.dashka.data.tts

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Simple in-memory LRU cache for TTS MP3 bytes.
 *
 * Sprint 3A: 5 entries, in-memory only. Web Dashka v2.1 mirrors this pattern
 * with a 50-entry cache; we start small and grow when usage data warrants it.
 *
 * Key strategy: text + language + voice. Same translated phrase replayed by
 * the same voice should hit cache; voice change is a cache miss.
 *
 * Memory profile: typical TTS clip is 30-60KB MP3, so 5 entries ≈ 150-300KB.
 * Acceptable.
 *
 * Thread safety: synchronized on the LinkedHashMap. Cache reads/writes happen
 * only from the IO dispatcher in TtsRepositoryImpl, but we lock anyway for
 * defensive correctness.
 */
@Singleton
class TtsCache @Inject constructor() {

    private val cache: LinkedHashMap<CacheKey, ByteArray> =
        object : LinkedHashMap<CacheKey, ByteArray>(MAX_ENTRIES, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: Map.Entry<CacheKey, ByteArray>,
            ): Boolean = size > MAX_ENTRIES
        }

    @Synchronized
    fun get(text: String, language: String, voice: String): ByteArray? =
        cache[CacheKey(text, language, voice)]

    @Synchronized
    fun put(text: String, language: String, voice: String, mp3Bytes: ByteArray) {
        cache[CacheKey(text, language, voice)] = mp3Bytes
    }

    @Synchronized
    fun clear() {
        cache.clear()
    }

    @Synchronized
    fun size(): Int = cache.size

    private data class CacheKey(
        val text: String,
        val language: String,
        val voice: String,
    )

    private companion object {
        const val MAX_ENTRIES = 5
    }
}
