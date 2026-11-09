package org.sensorhub.android.ui.features.nodes


import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.sensorhub.android.R
import org.sensorhub.android.ui.components.OSHClickableCardWithIcon
import org.sensorhub.android.ui.components.OSHTopAppBarWithLogo
import org.sensorhub.android.ui.theme.OSHTheme

@Composable
fun NodeScreen(
    onNavigateToNodes : () -> Unit,
    onNavigateToVisualizations : () -> Unit,
    onNavigateToSettings : () -> Unit,
) {
    Scaffold(
        topBar = {
            OSHTopAppBarWithLogo(
                title = stringResource(R.string.action_nodes),
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.action_settings),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
        ) {

            Spacer(modifier = Modifier.height(16.dp))

            OSHClickableCardWithIcon(
                title = stringResource(R.string.node_manager_title),
                imageVector = Icons.Default.Cloud,
                contentDescription = stringResource(R.string.node_manager_title),
                onClick = onNavigateToNodes,
            )

            OSHClickableCardWithIcon(
                title = stringResource(R.string.visualizations_title),
                imageVector = Icons.Default.ViewStream,
                contentDescription = stringResource(R.string.visualizations_title),
                onClick = onNavigateToVisualizations,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun NodesScreenPreview() {
    OSHTheme {
        NodeScreen({}, {}, {})
    }
}
