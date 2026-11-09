package org.sensorhub.android.ui.features.client.visualizations

import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.sensorhub.android.ui.features.client.data.RemoteControlStream

@Composable
fun VideoFrame(
    renderer: VideoStream?,
    modifier: Modifier = Modifier,
) {
    var textureView by remember { mutableStateOf<TextureView?>(null) }

    AndroidView(
        factory = { context ->
            TextureView(context).also { textureView = it }
        },
        update = { },
        modifier = modifier,
    )

    DisposableEffect(renderer, textureView) {
        textureView?.let { view -> renderer?.attach(view) }
        onDispose {
            renderer?.disconnect()
        }
    }
}

fun RemoteControlStream.supports(command: PtzCommand): Boolean = when (command) {
    PtzCommand.PAN_LEFT, PtzCommand.PAN_RIGHT -> supportsRelativePan
    PtzCommand.TILT_UP, PtzCommand.TILT_DOWN -> supportsRelativeTilt
    PtzCommand.ZOOM_IN, PtzCommand.ZOOM_OUT -> supportsRelativeZoom
}
