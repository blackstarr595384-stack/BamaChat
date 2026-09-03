package com.example.bamachat.voice.debug

import com.example.bamachat.voice.RealtimeVoiceEngine
import com.example.bamachat.voice.RealtimeVoiceEngineFactory
import com.example.bamachat.voice.NoOpVoiceDiagnostics
import com.example.bamachat.voice.VoiceDiagnostics
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeRealtimeVoiceEngineFactory @Inject constructor(
    private val repository: DebugVoiceScenarioRepository
) : RealtimeVoiceEngineFactory {
    override fun create(): RealtimeVoiceEngine = FakeRealtimeVoiceEngine(repository)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DebugRealtimeVoiceModule {
    @Binds
    @Singleton
    abstract fun bindRealtimeVoiceEngineFactory(
        implementation: FakeRealtimeVoiceEngineFactory
    ): RealtimeVoiceEngineFactory
}

@Module
@InstallIn(SingletonComponent::class)
object DebugVoiceDiagnosticsModule {
    @Provides
    @Singleton
    fun provideVoiceDiagnostics(): VoiceDiagnostics = NoOpVoiceDiagnostics
}
