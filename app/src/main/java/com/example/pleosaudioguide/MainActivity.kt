package com.example.pleosaudioguide

import android.os.Bundle
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.pleosaudioguide.ui.GuideApp
import com.example.pleosaudioguide.ui.theme.PleosAudioGuideTheme

class MainActivity : ComponentActivity() {
    private var requestedSpeedPermission = false

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("requestedSpeedPermission", requestedSpeedPermission)
        super.onSaveInstanceState(outState)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val speedPermission = "android.car.permission.CAR_SPEED"
        val request = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
        requestedSpeedPermission = savedInstanceState?.getBoolean("requestedSpeedPermission") ?: false
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            val history = ListeningHistoryStore.get(this)
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    history.state.collect { state ->
                        if (state.permissionPromptAllowed && !requestedSpeedPermission &&
                            ContextCompat.checkSelfPermission(this@MainActivity, speedPermission) != PackageManager.PERMISSION_GRANTED) {
                            requestedSpeedPermission = true
                            request.launch(speedPermission)
                        }
                    }
                }
            }
        }
        setContent { PleosAudioGuideTheme { GuideApp() } }
    }
}
