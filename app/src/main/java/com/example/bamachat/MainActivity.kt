package com.example.bamachat

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.fragment.app.FragmentActivity
import com.example.bamachat.ui.screen.BamaChatApp
import com.example.bamachat.ui.theme.BamaChatTheme
import com.example.bamachat.util.AppTelemetry
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppTelemetry.logEvent("app_open")

        enableEdgeToEdge()

        setContent {
            BamaChatTheme(dynamicColor = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    BamaChatApp()
                }
            }
        }
    }
}
