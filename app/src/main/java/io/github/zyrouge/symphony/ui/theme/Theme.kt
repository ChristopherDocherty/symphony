package io.github.zyrouge.symphony.ui.theme

import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.ThemeMode
import io.github.zyrouge.symphony.ui.helpers.ViewContext

enum class ColorSchemeMode {
    LIGHT,
    DARK,
    BLACK
}

@Composable
fun SymphonyTheme(
    context: ViewContext,
    content: @Composable () -> Unit,
) {
    val settings by context.symphony.settingsState.collectAsState()
    val themeMode = settings.themeMode
    val useMaterialYou = settings.materialYou
    val primaryColorName = settings.primaryColor
    val fontName = settings.fontFamily
    val fontScale = settings.fontScale.let { if (it <= 0f) 1f else it }
    val contentScale = settings.contentScale.let { if (it <= 0f) 1f else it }

    val colorSchemeMode = themeMode.toColorSchemeMode(isSystemInDarkTheme())
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && useMaterialYou) {
        val currentContext = LocalContext.current
        when (colorSchemeMode) {
            ColorSchemeMode.LIGHT -> dynamicLightColorScheme(currentContext)
            ColorSchemeMode.DARK -> dynamicDarkColorScheme(currentContext)
            ColorSchemeMode.BLACK -> ThemeColorSchemes.toBlackColorScheme(
                dynamicDarkColorScheme(currentContext)
            )
        }
    } else {
        val primaryColor = ThemeColors.resolvePrimaryColor(primaryColorName)
        when (colorSchemeMode) {
            ColorSchemeMode.LIGHT -> ThemeColorSchemes.createLightColorScheme(primaryColor)
            ColorSchemeMode.DARK -> ThemeColorSchemes.createDarkColorScheme(primaryColor)
            ColorSchemeMode.BLACK -> ThemeColorSchemes.createBlackColorScheme(primaryColor)
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        val activity = view.context as Activity
        SideEffect {
            WindowCompat.getInsetsController(activity.window, view)
                .isAppearanceLightStatusBars = colorSchemeMode == ColorSchemeMode.LIGHT
        }
    }

    val textDirection = when (LocalLayoutDirection.current) {
        LayoutDirection.Rtl -> TextDirection.Rtl
        else -> TextDirection.Ltr
    }
    val typography = SymphonyTypography.toTypography(
        SymphonyTypography.resolveFont(fontName),
        textDirection,
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = {
            CompositionLocalProvider(
                LocalDensity provides Density(
                    LocalDensity.current.density * contentScale,
                    LocalDensity.current.fontScale * fontScale,
                )
            ) {
                content()
            }
        }
    )
}

fun ThemeMode.toColorSchemeMode(symphony: Symphony): ColorSchemeMode {
    val isSystemInDarkTheme = symphony.applicationContext.resources.configuration.uiMode.let {
        (it and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
    return toColorSchemeMode(isSystemInDarkTheme)
}

fun ThemeMode.toColorSchemeMode(isSystemInDarkTheme: Boolean) = when (this) {
    ThemeMode.THEME_SYSTEM -> if (isSystemInDarkTheme) ColorSchemeMode.DARK else ColorSchemeMode.LIGHT
    ThemeMode.THEME_SYSTEM_BLACK -> if (isSystemInDarkTheme) ColorSchemeMode.BLACK else ColorSchemeMode.LIGHT
    ThemeMode.THEME_LIGHT -> ColorSchemeMode.LIGHT
    ThemeMode.THEME_DARK -> ColorSchemeMode.DARK
    ThemeMode.THEME_BLACK -> ColorSchemeMode.BLACK
    else -> ColorSchemeMode.LIGHT
}

fun ColorSchemeMode.isLight() = this == ColorSchemeMode.LIGHT
