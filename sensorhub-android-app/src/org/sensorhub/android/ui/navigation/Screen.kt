package org.sensorhub.android.ui.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard_screen")
    object Settings : Screen("settings_screen")
    object Map : Screen("map_screen")
    object FullscreenMap : Screen("fullscreen_map_screen/{profileId}/{systemId}/{streamId}") {
        fun createRoute(profileId: String, systemId: String, streamId: String): String =
            "fullscreen_map_screen/${Uri.encode(profileId)}/${Uri.encode(systemId)}/${Uri.encode(streamId)}"
    }
    object Client : Screen("client_screen")
    object NodeSystems : Screen("node_systems_screen/{profileId}") {
        fun createRoute(profileId: String): String =
            "node_systems_screen/${Uri.encode(profileId)}"
    }
    object Node : Screen("node_screen")
    object SystemDetail : Screen("system_detail_screen/{profileId}/{systemId}") {
        fun createRoute(profileId: String, systemId: String): String {
            return "system_detail_screen/${Uri.encode(profileId)}/${Uri.encode(systemId)}"
        }
    }
    object Sensors : Screen("sensors_screen")
    object ServerProfiles : Screen("server_profiles_screen")
    object OnDeviceNode : Screen("on_device_node_screen")
    object HelpFaq : Screen("help_faq_screen")
    object ServerForm : Screen("server_form_screen/{profileId}") {
        fun createRoute(profileId: String? = null): String {
            return "server_form_screen/${profileId ?: "new"}"
        }
    }
}
