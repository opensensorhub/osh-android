package org.sensorhub.android

import org.sensorhub.android.ui.SensorHubViewModel
import org.sensorhub.android.ui.features.client.OshClientViewModel

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.sensorhub.android.ui.navigation.Navbar
import org.sensorhub.android.ui.navigation.OSHNavHost
import org.sensorhub.android.ui.navigation.Screen
import org.sensorhub.android.ui.theme.OSHTheme

@Composable
fun OSHApp(hubViewModel: SensorHubViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val navController = rememberNavController()
    val clientViewModel: OshClientViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

    val bottomNavRoutes = setOf(
        Screen.Dashboard.route,
        Screen.Sensors.route,
        Screen.Map.route,
        Screen.Node.route,
    )
    val backStackEntry by navController.currentBackStackEntryAsState()
    val showBottomNav = backStackEntry?.destination?.route in bottomNavRoutes

    Scaffold(
        bottomBar = {
            if (showBottomNav) Navbar(navController = navController)
        }
    ) { padding ->
        OSHNavHost(
            navController = navController,
            hubViewModel = hubViewModel,
            clientViewModel = clientViewModel,
            modifier = Modifier.padding(padding)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun MainScreenPreview() {
    OSHTheme {
        OSHApp()
    }
}
