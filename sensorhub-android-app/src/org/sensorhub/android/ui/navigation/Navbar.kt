package org.sensorhub.android.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.sensorhub.android.ui.theme.OSHTheme
import org.sensorhub.android.ui.theme.Elevation
import org.sensorhub.android.ui.theme.OverlayLight
import org.sensorhub.android.R

@Composable
fun Navbar(
    navController: NavController
){
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route.let {
        it
    }
    val navigationItems = listOf(
        NavigationItem(
            title = "Home",
            icon = Icons.Default.Home,
            route = Screen.Dashboard.route
        ),
        NavigationItem(
            title = "Node",
            iconRes = R.drawable.ic_node,
            route = Screen.Node.route
        ),
        NavigationItem(
            title = "Sensors",
            icon = Icons.Default.Sensors,
            route = Screen.Sensors.route
        ),
        NavigationItem(
            title = "Map",
            icon = Icons.Default.Map,
            route = Screen.Map.route
        )
    )

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.primary,
        tonalElevation = Elevation.none,
    ) {
        navigationItems.forEach { item ->
        NavigationBarItem(
            selected = currentRoute == item.route,
            onClick = {
                navController.navigate(item.route) {
                    popUpTo(navController.graph.findStartDestination().id)
                    launchSingleTop = true
                }
            },
            icon = {
                item.icon?.let {
                    Icon(imageVector = it, contentDescription = item.title)
                } ?: item.iconRes?.let {
                    Icon(painter = painterResource(it), contentDescription = item.title)
                }
            },
            label = {
                Text(
                    item.title,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                unselectedIconColor = MaterialTheme.colorScheme.onPrimary,
                indicatorColor = OverlayLight,
            )

        )}
    }
}

data class NavigationItem(
    val title: String,
    val icon: ImageVector? = null,
    val iconRes: Int? = null,
    val route: String
)

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun NavbarPreview() {
    OSHTheme {
        Navbar(navController = rememberNavController())
    }
}
