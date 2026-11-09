package org.sensorhub.android.ui.features.nodes.ondevice

import android.content.SharedPreferences

data class LocalServiceSettings(
    val sosEnabled: Boolean = true,
    val csApiEnabled: Boolean = true,
    val discoveryEnabled: Boolean = false,
    val rulesLink: String = "",
) {
    companion object {
        const val SOS_ENABLED = "sos_service"
        const val CS_API_ENABLED = "csapi_service"
        const val DISCOVERY_ENABLED = "discovery_service"
        const val RULES_LINK = "rules_link"

        fun read(prefs: SharedPreferences): LocalServiceSettings {
            val defaults = LocalServiceSettings()
            return LocalServiceSettings(
                sosEnabled = prefs.getBoolean(SOS_ENABLED, defaults.sosEnabled),
                csApiEnabled = prefs.getBoolean(CS_API_ENABLED, defaults.csApiEnabled),
                discoveryEnabled = prefs.getBoolean(DISCOVERY_ENABLED, defaults.discoveryEnabled),
                rulesLink = prefs.getString(RULES_LINK, defaults.rulesLink) ?: defaults.rulesLink,
            )
        }
    }
}