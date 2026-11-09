package org.sensorhub.android.ui.features.nodes.servers

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import org.sensorhub.android.R
import org.sensorhub.android.ui.features.nodes.servers.data.ServerProfileItem
import org.sensorhub.android.ui.components.OSHActionCard
import org.sensorhub.android.ui.components.OSHAddFAB
import org.sensorhub.android.ui.components.OSHAlertDialog
import org.sensorhub.android.ui.components.OSHEditActionCard
import org.sensorhub.android.ui.components.OSHTopAppBarWithBack
import org.sensorhub.android.ui.navigation.Screen
import org.sensorhub.android.ui.theme.OSHTheme

@Composable
fun ServerProfilesScreen(
    onBackClick: () -> Unit,
    onOpenThisDevice: () -> Unit,
    navController: NavController = rememberNavController(),
    viewModel: ServerProfilesViewModel = viewModel()
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    var profileToDelete by remember { mutableStateOf<ServerProfileItem?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    profileToDelete?.let { profile ->
        OSHAlertDialog(
            onDismissRequest = { profileToDelete = null },
            onConfirmation = {
                viewModel.delete(profile.id)
                profileToDelete = null
            },
            dialogTitle = stringResource(R.string.title_delete_server),
            dialogText = stringResource(R.string.msg_delete_server, profile.serverName),
            icon = Icons.Filled.Info
        )
    }

    Scaffold(
        topBar = {
            OSHTopAppBarWithBack(
                title = stringResource(R.string.title_server_profiles),
                onBackClick = onBackClick
            )
        },
        floatingActionButton = {
            OSHAddFAB(
                onClick = { navController.navigate(Screen.ServerForm.createRoute()) }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(top = 16.dp, bottom = 88.dp)
        ) {
            item(key = "this-device") {
                OSHEditActionCard(
                    title = stringResource(R.string.this_device),
                    subtitle = stringResource(R.string.this_device_summary),
                    onClick = onOpenThisDevice,
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.content_desc_open_this_device),
                )
            }
            item(key = "remote-nodes-heading") {
                Text(
                    text = stringResource(R.string.remote_nodes),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            if (profiles.isEmpty()) {
                item(key = "empty-remote-nodes") {
                    Text(
                        text = stringResource(R.string.empty_server_profiles),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            } else {
                items(profiles, key = { it.id }) { item ->
                    OSHActionCard(
                        title = item.serverName,
                        subtitle = item.endpointUrl,
                        checked = item.enabled,
                        onCheckedChange = { enabled -> viewModel.setEnabled(item.id, enabled) },
                        onClick = { navController.navigate(Screen.ServerForm.createRoute(item.id)) },
                        imageVector = Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.content_desc_edit_server, item.serverName),
                        onLongClick = { profileToDelete = item },
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun ServerProfilesScreenPreview() {
    OSHTheme {
        ServerProfilesScreen(
            onBackClick = {},
            onOpenThisDevice = {},
        )
    }
}
