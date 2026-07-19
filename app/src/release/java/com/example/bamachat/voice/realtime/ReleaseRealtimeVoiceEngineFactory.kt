package com.example.bamachat.voice.realtime

import android.content.Context
import com.example.bamachat.BuildConfig
import com.example.bamachat.voice.AppVoiceDiagnostics
import com.example.bamachat.voice.RealtimeVoiceEngine
import com.example.bamachat.voice.RealtimeVoiceEngineFactory
import com.example.bamachat.voice.VoiceDiagnostics
import com.google.firebase.auth.FirebaseAuth
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReleaseRealtimeVoiceEngineFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val diagnostics: VoiceDiagnostics
) : RealtimeVoiceEngineFactory {
    override fun create(): RealtimeVoiceEngine {
        val credentialProvider = FirebaseRealtimeSessionCredentialProvider.create(
            firebaseAuth = FirebaseAuth.getInstance(),
            sessionUrl = BuildConfig.BAMA_VOICE_REALTIME_SESSION_URL,
            sessionEndUrl = BuildConfig.BAMA_VOICE_REALTIME_SESSION_END_URL
        )
        return OpenAiRealtimeVoiceEngine(
            credentialProvider = credentialProvider,
            peerConnectionFactory = RealtimePeerConnectionFactory {
                AndroidRealtimePeerConnectionController(context, diagnostics = diagnostics)
            },
            diagnostics = diagnostics
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ReleaseRealtimeVoiceModule {
    @Binds
    @Singleton
    abstract fun bindRealtimeVoiceEngineFactory(
        implementation: ReleaseRealtimeVoiceEngineFactory
    ): RealtimeVoiceEngineFactory
}

@Module
@InstallIn(SingletonComponent::class)
object ReleaseVoiceDiagnosticsModule {
    @Provides
    @Singleton
    fun provideVoiceDiagnostics(): VoiceDiagnostics = AppVoiceDiagnostics
}
