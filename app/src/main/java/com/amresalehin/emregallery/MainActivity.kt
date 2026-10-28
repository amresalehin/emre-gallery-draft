package com.amresalehin.emregallery

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amresalehin.emregallery.data.PreferencesRepository
import com.amresalehin.emregallery.ui.GalleryApp
import com.amresalehin.emregallery.ui.theme.EmreGalleryTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestGalleryRefreshRate()
        enableEdgeToEdge()
        setContent {
            val preferences = remember { PreferencesRepository(applicationContext) }
            val theme by preferences.theme.collectAsStateWithLifecycle(initialValue = "system")
            EmreGalleryTheme(theme) {
                GalleryApp()
            }
        }
    }
}

