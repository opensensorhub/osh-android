package org.sensorhub.android.ui.features.client.visualizations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import org.sensorhub.android.R
import org.sensorhub.android.ui.features.client.data.RemoteVisualization
import org.sensorhub.android.ui.features.client.data.StreamStatus
import org.sensorhub.android.ui.components.OSHCard
import org.sensorhub.android.ui.components.StatusDot
import org.sensorhub.android.ui.theme.ControlOverlay
import org.sensorhub.android.ui.theme.ControlSurface
import org.sensorhub.android.ui.theme.OSHShapes
import org.sensorhub.android.ui.theme.Dimensions

@Composable
fun VideoStreamCard(
    visualization: RemoteVisualization,
    status: StreamStatus,
    error: String?,
    renderer: VideoStream,
    onPlayPause: () -> Unit
) {
    val video = stringResource(R.string.system_detail_video)
    OSHCard(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimensions.screenHorizontal)
    ) {
        Column(Modifier.padding(Dimensions.cardContent)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(if (status == StreamStatus.RECEIVING) "started" else if (error != null) "error" else "unknown")
                Spacer(Modifier.width(Dimensions.titleGap))
                Text(
                    visualization.name.ifBlank { video },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Filled.Videocam,
                    contentDescription = stringResource(R.string.system_detail_video_stream)
                )
            }
            Text(
                when {
                    status == StreamStatus.RECEIVING -> stringResource(R.string.system_detail_live); error != null -> stringResource(
                    R.string.system_detail_disconnected_with_error,
                    error
                ); else -> stringResource(R.string.system_detail_paused)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Dimensions.compactGap),
                maxLines = 1
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = Dimensions.contentGap)
                    .aspectRatio(16f / 9f)
                    .clip(OSHShapes.medium)
                    .background(ControlSurface)
            ) {
                VideoFrame(renderer)
                if (status != StreamStatus.RECEIVING) Box(
                    Modifier
                        .fillMaxSize()
                        .background(ControlOverlay),
                    contentAlignment = Alignment.Center
                ) { Text(status.message(), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(
                    onPlayPause,
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(Dimensions.contentGap)
                        .size(Dimensions.controlButtonSize)
                        .clip(CircleShape)
                        .background(ControlOverlay)
                ) {
                    Icon(
                        if (status == StreamStatus.RECEIVING) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (status == StreamStatus.RECEIVING) stringResource(R.string.system_detail_pause_video) else stringResource(
                            R.string.system_detail_play_video
                        )
                    )
                }
            }
        }
    }
}
