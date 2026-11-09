package org.sensorhub.android.ui.theme

import android.app.Activity
import android.content.SharedPreferences
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.preference.PreferenceManager

enum class AppThemePreference {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        const val PREFERENCE_KEY = "app_theme"

        fun fromPreference(value: String?): AppThemePreference =
            values().firstOrNull { it.name == value } ?: SYSTEM
    }
}

private val OSHDarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline
)

private val OSHLightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = Color(0xFFFFFFFF),
    onPrimaryContainer = Color(0xFF381000),
    secondary = Color(0xFF5F5E5E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5E2E1),
    onSecondaryContainer = Color(0xFF1C1B1B),
    tertiary = Color(0xFF9C4325),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFFFFF),
    onTertiaryContainer = Color(0xFF351000),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color.White,
    onBackground = Color(0xFF201A18),
    surface = Color.White,
    onSurface = Color(0xFF201A18),
    surfaceVariant = Color.Transparent,
    onSurfaceVariant = Color.Black,
    outline = Color(0xE0E0E0E0),
)

@Immutable
data class OshStatusColors(
    val success: Color,
    val onSuccess: Color,
    val warning: Color,
    val onWarning: Color,
)

private val DefaultStatusColors = OshStatusColors(
    success = SuccessContainer,
    onSuccess = OnSuccessContainer,
    warning = WarningContainer,
    onWarning = OnWarningContainer,
)

val LocalOshStatusColors = staticCompositionLocalOf { DefaultStatusColors }

@Composable
fun OSHTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val prefs = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    var themePreference by remember {
        mutableStateOf(AppThemePreference.fromPreference(prefs.getString(AppThemePreference.PREFERENCE_KEY, null)))
    }
    val useDarkTheme = when (themePreference) {
        AppThemePreference.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        AppThemePreference.LIGHT -> false
        AppThemePreference.DARK -> true
    }
    val colorScheme = if (useDarkTheme) OSHDarkColorScheme else OSHLightColorScheme

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == AppThemePreference.PREFERENCE_KEY) {
                themePreference = AppThemePreference.fromPreference(
                    prefs.getString(AppThemePreference.PREFERENCE_KEY, null)
                )
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = AccentOrange.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = !useDarkTheme
            }
        }
    }

    CompositionLocalProvider(LocalOshStatusColors provides DefaultStatusColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = OSHTypography,
            shapes = OSHShapes,
            content = content,
        )
    }
}
