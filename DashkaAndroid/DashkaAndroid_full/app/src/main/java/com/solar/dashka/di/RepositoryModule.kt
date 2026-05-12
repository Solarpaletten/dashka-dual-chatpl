package com.solar.dashka.di

import com.solar.dashka.data.repository.TranslationRepositoryImpl
import com.solar.dashka.data.speech.SpeechRecognitionRepositoryImpl
import com.solar.dashka.data.tts.TtsRepositoryImpl
import com.solar.dashka.domain.repository.SpeechRecognitionRepository
import com.solar.dashka.domain.repository.TranslationRepository
import com.solar.dashka.domain.repository.TtsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTranslationRepository(
        impl: TranslationRepositoryImpl,
    ): TranslationRepository

    @Binds
    @Singleton
    abstract fun bindSpeechRecognitionRepository(
        impl: SpeechRecognitionRepositoryImpl,
    ): SpeechRecognitionRepository

    @Binds
    @Singleton
    abstract fun bindTtsRepository(
        impl: TtsRepositoryImpl,
    ): TtsRepository
}
