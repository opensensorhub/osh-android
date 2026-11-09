package org.sensorhub.android.ui.features.settings

import android.app.Application
import android.app.LocaleManager
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.LocaleList
import androidx.lifecycle.AndroidViewModel
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigInteger
import java.net.InetAddress
import java.nio.ByteOrder
import org.sensorhub.android.ui.theme.AppThemePreference

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(application)
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        val deviceName = prefs.getString("device_name", "MyDevice") ?: "MyDevice"
        val ipAddress = getDeviceIpAddress()
        val version = getAppVersion()
        val currentLang = prefs.getString("app_language", "en") ?: "en"
        val langIndex = LANGUAGE_VALUES.indexOf(currentLang).coerceAtLeast(0)
        val currentTheme = prefs.getString(AppThemePreference.PREFERENCE_KEY, AppThemePreference.SYSTEM.name)
        val themeIndex = THEME_VALUES.indexOf(currentTheme).coerceAtLeast(0)

        _state.value = SettingsState(
            deviceName = deviceName,
            deviceIpAddress = ipAddress,
            appVersion = version,
            selectedLanguageIndex = langIndex,
            selectedThemeIndex = themeIndex,
        )
    }

    fun updateDeviceName(name: String) {
        prefs.edit().putString("device_name", name).apply()
        _state.value = _state.value.copy(deviceName = name)
    }

    fun selectLanguage(index: Int) {
        val localeTag = LANGUAGE_VALUES[index]
        prefs.edit().putString("app_language", localeTag).apply()
        _state.value = _state.value.copy(selectedLanguageIndex = index)

        val localeManager = getApplication<Application>().getSystemService(LocaleManager::class.java)
        localeManager.applicationLocales = LocaleList.forLanguageTags(localeTag)
    }

    fun selectTheme(index: Int) {
        val theme = THEME_VALUES[index]
        prefs.edit().putString(AppThemePreference.PREFERENCE_KEY, theme).apply()
        _state.value = _state.value.copy(selectedThemeIndex = index)
    }

    private fun getDeviceIpAddress(): String {
        return try {
            val wifiManager = getApplication<Application>().getSystemService(WifiManager::class.java)
            var ipAddress = wifiManager.connectionInfo.ipAddress

            if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
                ipAddress = Integer.reverseBytes(ipAddress)
            }

            val ipByteArray = BigInteger.valueOf(ipAddress.toLong()).toByteArray()
            InetAddress.getByAddress(ipByteArray).hostAddress ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun getAppVersion(): String {
        return try {
            val context = getApplication<Application>()
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: ""
        } catch (e: PackageManager.NameNotFoundException) {
            ""
        }
    }
}
