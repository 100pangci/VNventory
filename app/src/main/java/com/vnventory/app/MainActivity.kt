package com.vnventory.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import com.vnventory.app.ui.text.LocalTitleDisplayMode
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vnventory.app.di.AppViewModelProvider
import com.vnventory.app.ui.VNventoryRoot
import com.vnventory.app.ui.theme.VNventoryTheme
import com.vnventory.app.ui.theme.ThemeViewModel

class MainActivity : ComponentActivity() {
    private val themeViewModel: ThemeViewModel by viewModels { AppViewModelProvider.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appearance by themeViewModel.appearance.collectAsStateWithLifecycle()
            val titleDisplayMode by themeViewModel.titleDisplayMode.collectAsStateWithLifecycle()
            appearance?.let { preferences ->
                val dark = preferences.themeMode.isDark(isSystemInDarkTheme())
                SideEffect {
                    // 系统处于浅色、应用强制深色时，状态栏图标也必须同步变亮。
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
                        navigationBarStyle = SystemBarStyle.auto(0xE6FFFFFF.toInt(), 0x801B1B1B.toInt()) { dark },
                    )
                }
                VNventoryTheme(preferences) {
                    CompositionLocalProvider(LocalTitleDisplayMode provides titleDisplayMode) {
                        VNventoryRoot()
                    }
                }
            }
        }
    }
}
