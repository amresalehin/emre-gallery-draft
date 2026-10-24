package com.amresalehin.emregallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amresalehin.emregallery.data.PreferencesRepository
import com.amresalehin.emregallery.ui.GalleryApp
import com.amresalehin.emregallery.ui.theme.EmreGalleryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GalleryApplication.disableTouchPointTaps(this)
        enableEdgeToEdge()
        setContent {
            val preferences = remember { PreferencesRepository(applicationContext) }
            val theme by preferences.theme.collectAsStateWithLifecycle(initialValue = "system")
            EmreGalleryTheme(theme) {
                GalleryApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        GalleryApplication.disableTouchPointTaps(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            GalleryApplication.disableTouchPointTaps(this)
        }
    }
}
