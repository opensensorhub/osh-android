package org.sensorhub.android.ui.features.client

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.sensorhub.android.R
import org.sensorhub.android.ui.features.client.data.RemoteNodeState
import org.sensorhub.android.ui.components.OSHCard
import org.sensorhub.android.ui.components.OSHClickableStatusRowWithIcon
import org.sensorhub.android.ui.components.OSHTopAppBarWithBack
import org.sensorhub.android.ui.theme.OSHTheme
import org.sensorhub.android.ui.theme.Dimensions
import org.sensorhub.android.ui.theme.Spacing

@Composable
fun OshClientScreen(
    onNavigateToNodeManager: () -> Unit,
    onNavigateToSystemsScreen: (profileId: String) -> Unit,
    viewModel: OshClientViewModel = viewModel(),
    onBackClick: () -> Unit
) {
    val nodes by viewModel.nodes.collectAsStateWithLifecycle()
    OshClientContent(
        nodes = nodes,
        onNavigateToNodeManager = onNavigateToNodeManager,
        onRefreshProfiles = viewModel::refreshProfiles,
        onNavigateToSystemsScreen = onNavigateToSystemsScreen,
        onBackClick = onBackClick,
    )
}

@Composable
private fun OshClientContent(
    nodes: List<RemoteNodeState>,
    onNavigateToNodeManager: () -> Unit,
    onRefreshProfiles: () -> Unit,
    onNavigateToSystemsScreen: (profileId: String) -> Unit,
    onBackClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            OSHTopAppBarWithBack(
                title = stringResource(R.string.visualizations_title),
                onBackClick = onBackClick,
                actions = {
                    IconButton(onClick = onRefreshProfiles) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.content_desc_refresh_profiles)
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (nodes.isEmpty()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Default.Sensors,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.client_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = Spacing.md),
                )
                OutlinedButton(
                    onClick = onNavigateToNodeManager,
                    modifier = Modifier.padding(top = Spacing.md),
                ) {
                    Text(stringResource(R.string.node_manager_title))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = Spacing.sm),
            ) {
                item(key = "remote-nodes-heading") {
                    Text(
                        text = stringResource(R.string.remote_nodes),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = Dimensions.screenHorizontal, vertical = Spacing.sm),
                    )
                }
                nodes.forEach { node ->
                    item(key = "server:${node.profileId}") {
                        ClientNodeCard(
                            node = node,
                            onClick = { onNavigateToSystemsScreen(node.profileId) },
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun OshClientScreenPreview() {
    val nodes = listOf(
        RemoteNodeState(
            profileId = "campus",
            name = "Campus Node",
            endpointUrl = "https://osh.example.org",
        ),
        RemoteNodeState(
            profileId = "field",
            name = "Field Node",
            endpointUrl = "https://field.osh.example.org",
            loading = true,
        ),
        RemoteNodeState(
            profileId = "lab",
            name = "Lab Node",
            endpointUrl = "https://lab.osh.example.org",
            error = "Unable to connect",
        ),
    )

    OSHTheme {
        OshClientContent(
            nodes = nodes,
            onNavigateToNodeManager = {},
            onNavigateToSystemsScreen = {},
            onRefreshProfiles = {},
            onBackClick = {},
        )
    }
}

@Composable
private fun ClientNodeCard(
    node: RemoteNodeState,
    onClick: () -> Unit,
) {
    OSHCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = Dimensions.screenHorizontal,
                vertical = Dimensions.cardOuterVertical,
            ),
    ) {
        Column {
            OSHClickableStatusRowWithIcon(
                title = node.name,
                imageVector = Icons.Filled.Cloud,
                contentDescription = stringResource(R.string.content_desc_server),
                subtitle = node.endpointUrl,
                summary = when {
                    node.loading -> stringResource(R.string.client_discovering_systems)
                    node.error != null -> node.error
                    node.systems.isEmpty() -> stringResource(R.string.client_tap_to_discover)
                    else -> stringResource(R.string.client_systems_discovered, node.systems.size)
                },
                status = when {
                    node.loading -> "starting"
                    node.error != null -> "error"
                    node.systems.isNotEmpty() -> "started"
                    else -> "unknown"
                },
                onClick = if (node.loading) null else onClick,
            )
            if (node.loading) {
                CircularProgressIndicator(
                    Modifier.padding(start = 52.dp, bottom = 16.dp)
                )
            }
        }
    }
}
