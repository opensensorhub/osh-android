package org.sensorhub.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
fun EdgeSwipeContainer(
    onEdgeSwipeLeftToRight: () -> Unit,
    content: @Composable () -> Unit
) {
    val edgeThreshold = with(LocalDensity.current) { 50.dp.toPx() }
    val swipeThreshold = with(LocalDensity.current) { 80.dp.toPx() }
    val onSwipe by rememberUpdatedState(onEdgeSwipeLeftToRight)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(edgeThreshold, swipeThreshold) {
                var startX = 0f
                var horizontalDrag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        startX = offset.x
                        horizontalDrag = 0f
                    },
                    onDragEnd = {
                        if (startX <= edgeThreshold && horizontalDrag >= swipeThreshold) {
                            onSwipe()
                        }
                    },
                    onDragCancel = {},
                    onHorizontalDrag = { change, dragAmount ->
                        if (startX <= edgeThreshold) {
                            horizontalDrag += dragAmount
                            if (horizontalDrag > 0f) change.consume()
                        }
                    }
                )
            }
    ) {
        content()
    }
}
