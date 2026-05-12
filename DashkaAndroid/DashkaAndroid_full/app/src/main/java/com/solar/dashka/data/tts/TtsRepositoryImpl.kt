package com.solar.dashka.data.tts

import com.solar.dashka.data.api.DashkaApi
import com.solar.dashka.data.api.DashkaResult
import com.solar.dashka.data.api.dto.TtsRequest
import com.solar.dashka.domain.model.LangCode
import com.solar.dashka.domain.model.TtsState
import com.solar.dashka.domain.model.TtsVoice
import com.solar.dashka.domain.repository.TtsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TTS orchestration: fetch MP3 from backend → cache → play.
 *
 * Sprint 3A flow:
 *   1. Check cache by (text, language, voice) — hit fast-path skips network
 *   2. On miss: POST /api/tts → ResponseBody.bytes() → cache.put
 *   3. Hand off to AudioPlayerWrapper → emit Playing → Idle (or Error)
 *
 * Network errors mapped via DashkaResult sealed class for consistency with
 * the translate flow.
 */
@Singleton
class TtsRepositoryImpl @Inject constructor(
    private val api: DashkaApi,
    private val cache: TtsCache,
    private val player: AudioPlayerWrapper,
) : TtsRepository {

    override fun play(
        text: String,
        language: LangCode,
        voice: TtsVoice,
    ): Flow<TtsState> = callbackFlow {
        if (text.isBlank()) {
            trySend(TtsState.Error("Нет текста для озвучки"))
            close()
            return@callbackFlow
        }

        trySend(TtsState.Loading)

        val mp3Bytes: ByteArray = try {
            // 1. Try cache first.
            cache.get(text, language.code, voice.id)
                ?: run {
                    // 2. Cache miss — fetch from backend on IO dispatcher.
                    val bytes = withContext(Dispatchers.IO) {
                        val response = api.tts(
                            TtsRequest(
                                text = text,
                                language = language.code,
                                voice = voice.id,
                            )
                        )
                        response.bytes()
                    }
                    cache.put(text, language.code, voice.id, bytes)
                    bytes
                }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: HttpException) {
            val msg = when (e.code()) {
                401 -> "Неверный токен доступа."
                429 -> "Слишком много запросов. Подождите."
                in 500..599 -> "Сервер озвучки недоступен."
                else -> "Ошибка сервера (код ${e.code()})."
            }
            trySend(TtsState.Error(msg))
            close()
            return@callbackFlow
        } catch (e: IOException) {
            trySend(TtsState.Error("Нет подключения к интернету."))
            close()
            return@callbackFlow
        } catch (e: Exception) {
            trySend(TtsState.Error(e.message ?: "Не удалось озвучить."))
            close()
            return@callbackFlow
        }

        // 3. Hand off to player.
        player.play(
            mp3Bytes = mp3Bytes,
            onPrepared = { trySend(TtsState.Playing) },
            onCompleted = {
                trySend(TtsState.Idle)
                close()
            },
            onError = { msg ->
                trySend(TtsState.Error(msg))
                close()
            },
        )

        // When collector stops or close() is called, tear down playback.
        awaitClose {
            player.stop()
        }
    }.flowOn(Dispatchers.Main.immediate)

    override fun stop() {
        player.stop()
    }

    override suspend fun prefetch(
        text: String,
        language: LangCode,
        voice: TtsVoice,
    ): DashkaResult<Unit> {
        if (text.isBlank()) return DashkaResult.Error.Unknown(
            IllegalArgumentException("blank text")
        )
        // Already cached? No-op.
        cache.get(text, language.code, voice.id)?.let {
            return DashkaResult.Success(Unit)
        }
        return try {
            val bytes = withContext(Dispatchers.IO) {
                api.tts(
                    TtsRequest(text, language.code, voice.id)
                ).bytes()
            }
            cache.put(text, language.code, voice.id, bytes)
            DashkaResult.Success(Unit)
        } catch (e: HttpException) {
            DashkaResult.Error.Server(
                code = e.code(),
                message = e.message ?: "tts prefetch failed",
            )
        } catch (e: IOException) {
            DashkaResult.Error.NetworkError
        } catch (e: Exception) {
            DashkaResult.Error.Unknown(e)
        }
    }
}
