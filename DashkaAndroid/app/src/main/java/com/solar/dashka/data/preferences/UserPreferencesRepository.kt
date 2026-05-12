package com.solar.dashka.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.solar.dashka.domain.model.TtsVoice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sprint 4 — persistent user preferences via DataStore.
 *
 * Two values persisted:
 *   - selectedVoice — the TtsVoice the user picked (default: EVE)
 *   - autoplayEnabled — autoplay toggle state (default: false)
 *
 * Both survive app kill/restart. Settings load synchronously on first
 * VM access via runBlocking on a single first() call — acceptable here
 * because DataStore reads are fast and only happen once at VM init.
 *
 * Future Sprint 4+ additions could persist:
 *   - Last selected direction
 *   - Last partner language (when multi-language is added)
 *   - User's preferred theme
 */
@Singleton
class UserPreferencesRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val Context.dataStore by preferencesDataStore(name = DATASTORE_NAME)

    /* ---------- Reads ---------- */

    val voiceFlow: Flow<TtsVoice> = context.dataStore.data.map { prefs ->
        prefs[KEY_VOICE]?.let { id ->
            TtsVoice.entries.firstOrNull { it.id == id }
        } ?: TtsVoice.EVE
    }

    val autoplayFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTOPLAY] ?: false
    }

    /**
     * Synchronous initial read for ViewModel construction.
     * Returns defaults on first launch before any preferences exist.
     */
    suspend fun loadInitial(): UserPreferences = UserPreferences(
        voice = voiceFlow.first(),
        autoplayEnabled = autoplayFlow.first(),
    )

    /* ---------- Writes ---------- */

    suspend fun setVoice(voice: TtsVoice) {
        context.dataStore.edit { prefs ->
            prefs[KEY_VOICE] = voice.id
        }
    }

    suspend fun setAutoplayEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTOPLAY] = enabled
        }
    }

    private companion object {
        const val DATASTORE_NAME = "dashka_user_prefs"
        val KEY_VOICE = stringPreferencesKey("selected_voice")
        val KEY_AUTOPLAY = booleanPreferencesKey("autoplay_enabled")
    }
}

/** Initial preferences snapshot — used at VM construction. */
data class UserPreferences(
    val voice: TtsVoice,
    val autoplayEnabled: Boolean,
)
