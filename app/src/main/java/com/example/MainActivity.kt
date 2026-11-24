package com.example

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.data.OrientationMode
import com.example.ui.IdeEffect
import com.example.ui.IdeViewModel
import com.example.ui.ZenncodeApp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: IdeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        is IdeEffect.OpenExternalBrowser -> openInChromeOrDefault(effect.url, effect.preferChrome)
                        is IdeEffect.SetOrientation -> applyOrientation(effect.mode)
                    }
                }
            }
        }

        setContent {
            ZenncodeApp(viewModel = viewModel)
        }
    }

    private fun applyOrientation(mode: OrientationMode) {
        requestedOrientation = when (mode) {
            OrientationMode.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            OrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            OrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    private fun openInChromeOrDefault(url: String, preferChrome: Boolean) {
        val uri = Uri.parse(url)
        if (preferChrome) {
            val chromeIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.android.chrome")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (chromeIntent.resolveActivity(packageManager) != null) {
                startActivity(chromeIntent)
                return
            }
        }
        val fallback = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { startActivity(fallback) }
            .onFailure { viewModel.showToast("No external browser available; opening in Built-in Browser") }
    }
}
