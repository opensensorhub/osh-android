package org.sensorhub.android.ui.features.sensors

import android.app.Application
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.AndroidViewModel
import androidx.preference.PreferenceManager
import org.sensorhub.android.ui.features.sensors.data.SensorRegistry
import org.sensorhub.android.ui.features.sensors.data.SensorUiEntry

class SensorsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(application)

    private val _toggleStates = mutableStateMapOf<String, Boolean>()
    val toggleStates: Map<String, Boolean> = _toggleStates

    private val _stringStates = mutableStateMapOf<String, String>()
    val stringStates: Map<String, String> = _stringStates

    init {
        SensorRegistry.entries.forEach { sensor ->
            _toggleStates[sensor.prefKey] = sensor.isEnabled(prefs)
        }

        CHOICE_DIALOGS.forEach { spec ->
            _stringStates[spec.prefKey] = spec.setting.read(prefs)
        }

        SensorRegistry.bluetoothAddressPreferenceKeys.forEach { key ->
            _stringStates[key] = prefs.getString(key, "") ?: ""
        }
    }

    fun toggleSensor(sensor: SensorUiEntry, enabled: Boolean) {
        val prefKey = sensor.prefKey
        prefs.edit().putBoolean(prefKey, enabled).apply()
        _toggleStates[prefKey] = enabled
    }

    fun choiceValue(setting: org.sensorhub.android.ui.features.sensors.data.SensorChoice): String =
        _stringStates[setting.key] ?: setting.defaultValue

    fun setStringPref(prefKey: String, value: String) {
        prefs.edit().putString(prefKey, value).apply()
        _stringStates[prefKey] = value
    }
}
