package org.sensorhub.android.ui.features.client.visualizations

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import org.sensorhub.android.ui.features.client.data.RemoteVisualization
import org.sensorhub.android.ui.features.client.data.StreamCardState
import org.sensorhub.android.ui.features.client.data.SystemDetailUiState
import org.sensorhub.android.ui.components.OSHTopAppBarWithBack
import org.sensorhub.android.ui.features.client.OshClientViewModel

@Composable
fun FullscreenMap(
    onBackClick: () -> Unit,
    profileId: String,
    systemId: String,
    streamId: String,
    viewModel: OshClientViewModel,
) {
    val detailState by viewModel.systemDetailState(profileId, systemId)
        .collectAsStateWithLifecycle(SystemDetailUiState())
    val system = detailState.system
    val visualization = system?.visualizations?.firstOrNull {
        it.dataStreamId == streamId && it.kind == RemoteVisualization.Kind.LOCATION
    }
    val card = detailState.cards.filterIsInstance<StreamCardState.Location>()
        .firstOrNull { it.streamId == streamId }

    LaunchedEffect(system, visualization) {
        if (system != null && visualization != null) {
            viewModel.setLocationEnabled(profileId, system, visualization, true)
        }
    }

    Scaffold(
        topBar = {
            OSHTopAppBarWithBack(
                title = visualization?.name?.ifBlank { null }
                    ?: stringResource(R.string.system_detail_location),
                onBackClick = onBackClick,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            val position = card?.position
            if (position != null) {
                LocationMap(
                    position = position,
                    label = visualization?.name.orEmpty(),
                    interactive = true,
                )
            } else {
                Text(
                    text = stringResource(R.string.system_detail_waiting_for_observations),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
