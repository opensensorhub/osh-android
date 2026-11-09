package org.sensorhub.android.ui.features.client

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.sensorhub.android.R
import org.sensorhub.android.ui.components.OSHCard
import org.sensorhub.android.ui.components.OSHClickableStatusRowWithIcon
import org.sensorhub.android.ui.components.OSHTopAppBarWithBack
import org.sensorhub.android.ui.components.StatusDot
import org.sensorhub.android.ui.features.client.data.RemoteNodeState
import org.sensorhub.android.ui.features.client.data.RemoteSystem
import org.sensorhub.android.ui.theme.Dimensions
import org.sensorhub.android.ui.theme.Spacing

@Composable
fun NodeSystemsScreen(
    profileId: String,
    onOpenSystem: (profileId: String, systemId: String) -> Unit,
    onBackClick: () -> Unit,
    viewModel: OshClientViewModel,
) {
    val nodes by viewModel.nodes.collectAsStateWithLifecycle()
    val node = nodes.firstOrNull { it.profileId == profileId }

    LaunchedEffect(profileId) {
        if (node != null && node.systems.isEmpty() && !node.loading && node.error == null) {
            viewModel.discover(profileId)
        }
    }

    Scaffold(
        topBar = {
            OSHTopAppBarWithBack(
                title = stringResource(R.string.client_systems_title),
                onBackClick = onBackClick,
                actions = {
                    IconButton(
                        onClick = { viewModel.discover(profileId) },
                        enabled = node != null && !node.loading,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.content_desc_refresh_profiles),
                        )
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when (node) {
            null -> MissingNodeContent(Modifier.padding(padding))
            else -> NodeSystemsContent(
                node = node,
                onDiscover = { viewModel.discover(profileId) },
                onOpenSystem = { systemId -> onOpenSystem(profileId, systemId) },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun MissingNodeContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.msg_profile_not_found),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun NodeSystemsContent(
    node: RemoteNodeState,
    onDiscover: () -> Unit,
    onOpenSystem: (systemId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = Spacing.sm),
    ) {
        item(key = "node-status") {
            SelectedNodeHeader(node = node)
        }

        when {
            node.loading -> item(key = "loading") {
                LoadingContent()
            }

            node.error != null -> item(key = "error") {
                ErrorContent(message = node.error, onRetry = onDiscover)
            }

            node.systems.isEmpty() -> item(key = "no-systems") {
                EmptySystemsContent()
            }

            else -> items(
                items = node.systems,
                key = { it.id },
            ) { system ->
                SystemCard(
                    system = system,
                    onClick = { onOpenSystem(system.id) },
                )
            }
        }
    }
}

@Composable
private fun SelectedNodeHeader(node: RemoteNodeState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = Dimensions.screenHorizontal,
                vertical = Spacing.md,
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Cloud,
                contentDescription = stringResource(R.string.content_desc_server),
            )
            Spacer(Modifier.width(Spacing.md))
            Column {
                Text(text = node.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = node.endpointUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            modifier = Modifier.padding(top = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(
                when {
                    node.loading -> "starting"
                    node.error != null -> "error"
                    else -> "started"
                }
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = when {
                    node.loading -> stringResource(R.string.client_discovering_systems)
                    node.error != null -> node.error
                    node.systems.isEmpty() -> stringResource(R.string.client_no_systems)
                    else -> stringResource(R.string.client_systems_discovered, node.systems.size)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (node.error != null) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.client_discovering_systems),
            modifier = Modifier.padding(top = Spacing.md),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = message, color = MaterialTheme.colorScheme.error)
        Button(
            onClick = onRetry,
            modifier = Modifier.padding(top = Spacing.md),
        ) {
            Text(stringResource(R.string.client_retry))
        }
    }
}

@Composable
private fun EmptySystemsContent() {
    Text(
        text = stringResource(R.string.client_no_systems),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(Spacing.lg),
    )
}

@Composable
private fun SystemCard(system: RemoteSystem, onClick: () -> Unit) {
    OSHCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = Dimensions.screenHorizontal,
                vertical = Dimensions.cardOuterVertical,
            ),
    ) {
        OSHClickableStatusRowWithIcon(
            title = system.name,
            imageVector = Icons.Filled.Sensors,
            contentDescription = stringResource(R.string.content_desc_system),
            subtitle = system.uid,
            summary = stringResource(R.string.client_datastream_count, system.visualizations.size),
            status = system.status,
            onClick = onClick,
        )
    }
}
