package org.sensorhub.android.ui.features.nodes.ondevice

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.NetworkInterface

class OnDeviceViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(application)
    private val _state = MutableStateFlow(readState())
    val state: StateFlow<OnDeviceNodeState> = _state.asStateFlow()

    fun setSosEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(LocalServiceSettings.SOS_ENABLED, enabled).apply()
        _state.value = _state.value.copy(services = _state.value.services.copy(sosEnabled = enabled))
    }

    fun setCsApiEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(LocalServiceSettings.CS_API_ENABLED, enabled).apply()
        _state.value = _state.value.copy(services = _state.value.services.copy(csApiEnabled = enabled))
    }

    fun setDiscoveryEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(LocalServiceSettings.DISCOVERY_ENABLED, enabled).apply()
        _state.value = _state.value.copy(services = _state.value.services.copy(discoveryEnabled = enabled))
    }

    fun setDiscoveryRulesLink(value: String) {
        prefs.edit().putString(LocalServiceSettings.RULES_LINK, value).apply()
        _state.value = _state.value.copy(services = _state.value.services.copy(rulesLink = value))
    }

    fun updateDeviceName(name: String) {
        prefs.edit().putString(DEVICE_NAME, name).apply()
        _state.value = _state.value.copy(deviceName = name)
    }

    private fun readState() = OnDeviceNodeState(
        deviceName = prefs.getString(DEVICE_NAME, DEFAULT_DEVICE_NAME) ?: DEFAULT_DEVICE_NAME,
        ipAddress = getDeviceIpAddress(),
        services = LocalServiceSettings.read(prefs),
    )

    private fun getDeviceIpAddress(): String = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList().asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress.orEmpty()
    }.getOrDefault("")

    private companion object {
        const val DEVICE_NAME = "device_name"
        const val DEFAULT_DEVICE_NAME = "This Device"
    }
}

data class OnDeviceNodeState(
    val deviceName: String,
    val ipAddress: String,
    val services: LocalServiceSettings,
)
