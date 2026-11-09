package org.sensorhub.android.ui.navigation


import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import org.sensorhub.android.R
import org.sensorhub.android.ui.SensorHubViewModel
import org.sensorhub.android.ui.components.EdgeSwipeContainer
import org.sensorhub.android.ui.features.client.visualizations.FullscreenMap
import org.sensorhub.android.ui.features.client.OshClientScreen
import org.sensorhub.android.ui.features.client.OshClientViewModel
import org.sensorhub.android.ui.features.client.NodeSystemsScreen
import org.sensorhub.android.ui.features.client.SystemDetailRoute
import org.sensorhub.android.ui.features.dashboard.DashboardRoute
import org.sensorhub.android.ui.features.settings.help.FaqItem
import org.sensorhub.android.ui.features.settings.help.HelpFaqScreen
import org.sensorhub.android.ui.features.maps.MapScreen
import org.sensorhub.android.ui.features.nodes.NodeScreen
import org.sensorhub.android.ui.features.nodes.ondevice.OnDeviceNodeScreen
import org.sensorhub.android.ui.features.sensors.SensorsScreen
import org.sensorhub.android.ui.features.nodes.servers.ServerFormScreen
import org.sensorhub.android.ui.features.nodes.servers.ServerProfilesScreen
import org.sensorhub.android.ui.features.settings.SettingsScreen

@Composable
fun OSHNavHost(
    navController: NavHostController,
    hubViewModel: SensorHubViewModel,
    clientViewModel: OshClientViewModel,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
        modifier = modifier
    ) {

        // Main Screens
        composable(Screen.Settings.route) {
            val context = LocalContext.current
            SettingsScreen(
                onNavigateToHelpFaq = { navController.navigate(Screen.HelpFaq.route) },
                onNavigateToRepo = { context.startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/botts-innovative-research/osh-android".toUri())) },
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Screen.Dashboard.route) {
            DashboardRoute(
                viewModel = hubViewModel,
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } }
            )
        }
        composable(Screen.Sensors.route) {
            SensorsScreen(
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } }
            )
        }
        composable(Screen.Map.route) {
            MapScreen(
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } },
                tracks = clientViewModel.tracks,
            )
        }
        composable(Screen.Node.route) {
            NodeScreen(
                onNavigateToVisualizations = { navController.navigate(Screen.Client.route) { launchSingleTop = true } },
                onNavigateToNodes = { navController.navigate(Screen.ServerProfiles.route) { launchSingleTop = true } },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } },
            )
        }


        composable(Screen.HelpFaq.route) {
            val context = LocalContext.current
            val questions = context.resources.getStringArray(R.array.faq_questions)
            val answers = context.resources.getStringArray(R.array.faq_answers)
            val categories = context.resources.getStringArray(R.array.faq_categories)

            val faqItems = buildFaqItems(questions, answers, categories)

            EdgeSwipeContainer(
                onEdgeSwipeLeftToRight = { navController.popBackStack() }
            ) {
                HelpFaqScreen(
                    items = faqItems,
                    onBackClick = { navController.popBackStack() }
                )
            }
        }

        composable(Screen.Client.route) {
            EdgeSwipeContainer(
                onEdgeSwipeLeftToRight = { navController.popBackStack() }
            ) {
                OshClientScreen(
                    onNavigateToNodeManager = { navController.navigate(Screen.ServerProfiles.route) { launchSingleTop = true } },
                    onNavigateToSystemsScreen = { profileId ->
                        navController.navigate(Screen.NodeSystems.createRoute(profileId))
                    },
                    viewModel = clientViewModel,
                    onBackClick = { navController.popBackStack() },
                )
            }
        }
        composable(
            route = Screen.NodeSystems.route,
            arguments = listOf(navArgument("profileId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getString("profileId").orEmpty()
            EdgeSwipeContainer(
                onEdgeSwipeLeftToRight = { navController.popBackStack() }
            ) {
                NodeSystemsScreen(
                    profileId = profileId,
                    onOpenSystem = { selectedProfileId, systemId ->
                        val destinationProfileId = selectedProfileId.ifBlank { profileId }
                        navController.navigate(
                            Screen.SystemDetail.createRoute(
                                destinationProfileId,
                                systemId
                            )
                        )
                    },
                    viewModel = clientViewModel,
                    onBackClick = { navController.popBackStack() },
                )
            }
        }
        composable(
            route = Screen.SystemDetail.route,
            arguments = listOf(
                navArgument("profileId") { type = NavType.StringType },
                navArgument("systemId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->

            EdgeSwipeContainer(
                onEdgeSwipeLeftToRight = { navController.popBackStack() }
            ) {
                SystemDetailRoute(
                    profileId = backStackEntry.arguments?.getString("profileId").orEmpty(),
                    systemId = backStackEntry.arguments?.getString("systemId").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onOpenFullscreenMap = { streamId ->
                        navController.navigate(Screen.FullscreenMap.createRoute(
                            backStackEntry.arguments?.getString("profileId").orEmpty(),
                            backStackEntry.arguments?.getString("systemId").orEmpty(),
                            streamId,
                        ))
                    },
                    viewModel = clientViewModel,
                )
            }
        }
        composable(
            route = Screen.FullscreenMap.route,
            arguments = listOf(
                navArgument("profileId") { type = NavType.StringType },
                navArgument("systemId") { type = NavType.StringType },
                navArgument("streamId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            EdgeSwipeContainer(
                onEdgeSwipeLeftToRight = { navController.popBackStack() }
            ) {
                FullscreenMap(
                    onBackClick = { navController.popBackStack() },
                    profileId = backStackEntry.arguments?.getString("profileId").orEmpty(),
                    systemId = backStackEntry.arguments?.getString("systemId").orEmpty(),
                    streamId = backStackEntry.arguments?.getString("streamId").orEmpty(),
                    viewModel = clientViewModel,
                )
            }
        }

        composable(Screen.ServerProfiles.route) {
            EdgeSwipeContainer(
                onEdgeSwipeLeftToRight = { navController.popBackStack() }
            ) {
                ServerProfilesScreen(
                    onBackClick = { navController.popBackStack() },
                    onOpenThisDevice = { navController.navigate(Screen.OnDeviceNode.route) },
                    navController = navController
                )
            }
        }
        composable(Screen.OnDeviceNode.route) {
            EdgeSwipeContainer(
                onEdgeSwipeLeftToRight = { navController.popBackStack() }
            ) {
                OnDeviceNodeScreen(onBackClick = { navController.popBackStack() })
            }
        }
        composable(
            route = Screen.ServerForm.route,
            arguments = listOf(navArgument("profileId") { type = NavType.StringType })
        ) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getString("profileId")
            EdgeSwipeContainer(
                onEdgeSwipeLeftToRight = { navController.popBackStack() }
            ) {
                ServerFormScreen(
                    onBackClick = { navController.popBackStack() },
                    profileId = profileId
                )
            }
        }
    }
}


private fun buildFaqItems(
    questions: Array<String>,
    answers: Array<String>,
    categories: Array<String>
): List<FaqItem> {
    val items = mutableListOf<FaqItem>()
    var currentCategory = ""
    for (i in questions.indices) {
        val category = categories[i]
        if (category != currentCategory) {
            items.add(FaqItem.Header(category))
            currentCategory = category
        }
        items.add(FaqItem.Entry(questions[i], answers[i]))
    }
    return items
}
