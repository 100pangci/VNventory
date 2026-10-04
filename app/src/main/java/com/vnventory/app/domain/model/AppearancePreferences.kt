package com.vnventory.app.domain.model

import com.vnventory.app.domain.text.MessageKey
import com.vnventory.app.domain.text.message

enum class ThemeMode(private val labelKey: MessageKey) {
    SYSTEM(MessageKey.THEME_SYSTEM),
    LIGHT(MessageKey.THEME_LIGHT),
    DARK(MessageKey.THEME_DARK);

    val label get() = message(labelKey)

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }
}

data class AppearancePreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
)
