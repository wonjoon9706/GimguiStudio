package com.example.pleosaudioguide

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.pleosaudioguide.ui.GuideApp
import com.example.pleosaudioguide.ui.theme.PleosAudioGuideTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PleosAudioGuideTheme { GuideApp() } }
    }
}
