package org.sensorhub.android.ui.features.client

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.sensorhub.android.R
import org.sensorhub.android.ui.features.client.data.RemoteSystem
import org.sensorhub.android.ui.features.client.data.RemoteVisualization
import org.sensorhub.android.ui.features.client.data.StreamCardState
import org.sensorhub.android.ui.features.client.data.StreamStatus
import org.sensorhub.android.ui.features.client.data.SystemDetailUiState
import org.sensorhub.android.ui.components.OSHTopAppBarWithBack
import org.sensorhub.android.ui.features.client.visualizations.MapLocationCard
import org.sensorhub.android.ui.features.client.visualizations.OtherStreamCard
import org.sensorhub.android.ui.features.client.visualizations.PtzCommandCard
import org.sensorhub.android.ui.features.client.visualizations.VideoStream
import org.sensorhub.android.ui.features.client.visualizations.VideoStreamCard
import org.sensorhub.android.ui.theme.Dimensions

@Composable
fun SystemDetailRoute(
    profileId: String,
    systemId: String,
    onBack: () -> Unit,
    onOpenFullscreenMap: (String) -> Unit,
    viewModel: OshClientViewModel
) {
    val detailState by viewModel.systemDetailState(profileId, systemId)
        .collectAsStateWithLifecycle(SystemDetailUiState())
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val system = detailState.system
    system?.let { selectedSystem ->
        DisposableEffect(profileId, selectedSystem.id, selectedSystem.visualizations, lifecycle) {
            var streamsStarted = false
            fun startStreams() {
                if (!streamsStarted) {
                    streamsStarted = true; viewModel.startSystemLocationStreams(
                        profileId,
                        selectedSystem
                    ); viewModel.startSystemOtherStreams(profileId, selectedSystem)
                }
            }

            fun stopStreams() {
                if (streamsStarted) {
                    streamsStarted = false; viewModel.stopSystemVideos(
                        profileId,
                        selectedSystem
                    ); viewModel.stopSystemLocationStreams(
                        profileId,
                        selectedSystem
                    ); viewModel.stopSystemOtherStreams(profileId, selectedSystem)
                }
            }

            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> startStreams(); Lifecycle.Event.ON_STOP -> stopStreams(); else -> Unit
                }
            }
            lifecycle.addObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) startStreams()
            onDispose { lifecycle.removeObserver(observer); stopStreams() }
        }
    }
    val rendererFor: (RemoteSystem, RemoteVisualization) -> VideoStream = remember(viewModel) {
        { selectedSystem, visualization ->
            viewModel.videoRenderer(
                selectedSystem,
                visualization
            )
        }
    }
    SystemDetailScreen(
        system?.name ?: stringResource(R.string.system_detail_default_title),
        onBack
    ) { padding ->
        if (system == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) { Text(stringResource(R.string.system_detail_missing_system)) }
        } else {
            val videoCards = detailState.cards.filterIsInstance<StreamCardState.Video>()
            val locationCards = detailState.cards.filterIsInstance<StreamCardState.Location>()
            val valueCards = detailState.cards.filterIsInstance<StreamCardState.Values>()
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = Dimensions.cardOuterVertical),
                verticalArrangement = Arrangement.spacedBy(Dimensions.cardOuterVertical)
            ) {
                items(videoCards, key = { it.streamId }) { card ->
                    val visualization = RemoteVisualization(
                        card.streamId,
                        card.name,
                        RemoteVisualization.Kind.VIDEO
                    )
                    VideoStreamCard(
                        visualization,
                        card.status,
                        card.error,
                        remember(visualization.dataStreamId) {
                            rendererFor(
                                system,
                                visualization
                            )
                        }) {
                        viewModel.setVideoEnabled(
                            profileId,
                            visualization,
                            card.status != StreamStatus.RECEIVING
                        )
                    }
                }
                items(locationCards, key = { it.streamId }) { card ->
                    MapLocationCard(
                        RemoteVisualization(
                            card.streamId,
                            card.name,
                            RemoteVisualization.Kind.LOCATION
                        ),
                        card.status,
                        card.position,
                        onFullscreen = { onOpenFullscreenMap(card.streamId) },
                    )
                }
                items(valueCards, key = { it.streamId }) { card ->
                    OtherStreamCard(
                        RemoteVisualization(
                            card.streamId,
                            card.name,
                            RemoteVisualization.Kind.OTHER
                        ),
                        card.values,
                        card.status
                    )
                }
                if (system.visualizations.isEmpty()) item(key = "empty") {
                    Text(
                        stringResource(R.string.system_detail_no_datastreams),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(Dimensions.cardContent)
                    )
                }
                system.ptz?.let { ptz ->
                    item(key = "ptz") {
                        PtzCommandCard(
                            ptz,
                            { command ->
                                viewModel.sendPtzCommand(
                                    profileId,
                                    ptz,
                                    mapOf(command.item to command.delta)
                                )
                            }) { field, value ->
                            viewModel.sendPtzCommand(
                                profileId,
                                ptz,
                                mapOf(field to value)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemDetailScreen(
    title: String,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = { OSHTopAppBarWithBack(title = title, onBackClick = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
        content = content
    )
}
