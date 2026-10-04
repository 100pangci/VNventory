package com.vnventory.app.ui.theme

import android.os.Build
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.vnventory.app.domain.model.AppearancePreferences

private val LightColorScheme = lightColorScheme(
    primary = PlumPrimary,
    onPrimary = PlumOnPrimary,
    primaryContainer = PlumPrimaryContainer,
    onPrimaryContainer = PlumOnPrimaryContainer,
    secondary = RoseSecondary,
    onSecondary = RoseOnSecondary,
    secondaryContainer = RoseSecondaryContainer,
    onSecondaryContainer = RoseOnSecondaryContainer,
    tertiary = AmberTertiary,
    onTertiary = AmberOnTertiary,
    tertiaryContainer = AmberTertiaryContainer,
    onTertiaryContainer = AmberOnTertiaryContainer,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
)

private val DarkColorScheme = darkColorScheme(
    primary = PlumDarkPrimary,
    onPrimary = PlumDarkOnPrimary,
    primaryContainer = PlumDarkPrimaryContainer,
    onPrimaryContainer = PlumDarkOnPrimaryContainer,
    secondary = RoseDarkSecondary,
    onSecondary = RoseDarkOnSecondary,
    secondaryContainer = RoseDarkSecondaryContainer,
    onSecondaryContainer = RoseDarkOnSecondaryContainer,
    tertiary = AmberDarkTertiary,
    onTertiary = AmberDarkOnTertiary,
    tertiaryContainer = AmberDarkTertiaryContainer,
    onTertiaryContainer = AmberDarkOnTertiaryContainer,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
)

/**
 * 应用主题。
 *
 * @param darkTheme 是否深色（默认跟随系统）
 * @param dynamicColor 是否使用系统动态取色。默认关闭以保持品牌视觉，
 *   设置中可启用跟随壁纸配色（Android 12+），旧系统自动回退固定配色。
 */
@Composable
fun VNventoryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = appearanceColorScheme(LocalContext.current, darkTheme, dynamicColor)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VNventoryTypography,
        shapes = ShelfShapes,
        content = content,
    )
}

internal fun appearanceColorScheme(context: Context, darkTheme: Boolean, dynamicColor: Boolean, sdkInt: Int = Build.VERSION.SDK_INT): ColorScheme = when {
    dynamicColor && sdkInt >= Build.VERSION_CODES.S && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    darkTheme -> DarkColorScheme
    else -> LightColorScheme
}

/** 应用入口使用持久化外观设置，预览仍可直接指定 darkTheme。 */
@Composable
fun VNventoryTheme(appearance: AppearancePreferences, content: @Composable () -> Unit) {
    VNventoryTheme(
        darkTheme = appearance.themeMode.isDark(isSystemInDarkTheme()),
        dynamicColor = appearance.dynamicColor,
        content = content,
    )
}
