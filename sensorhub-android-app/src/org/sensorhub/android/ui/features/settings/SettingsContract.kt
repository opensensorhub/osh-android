package org.sensorhub.android.ui.features.settings

public val LANGUAGE_LABELS = listOf("English", "中文 (台灣)", "Español", "Français", "Deutsch", "Italiano", "Português")
public val LANGUAGE_VALUES = listOf("en", "zh-TW", "es", "fr", "de", "it", "pt")
public val THEME_VALUES = listOf("SYSTEM", "LIGHT", "DARK")

data class SettingsState(
    val deviceName: String = "MyDevice",
    val deviceIpAddress: String = "",
    val appVersion: String = "",
    val selectedLanguageIndex: Int = 0,
    val selectedThemeIndex: Int = 0,
)
