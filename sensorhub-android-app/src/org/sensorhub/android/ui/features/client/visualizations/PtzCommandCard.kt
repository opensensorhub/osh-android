package org.sensorhub.android.ui.features.client.visualizations

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ControlCamera
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.sensorhub.android.R
import org.sensorhub.android.ui.features.client.data.PtzAxisRange
import org.sensorhub.android.ui.features.client.data.RemoteControlStream
import org.sensorhub.android.ui.components.OSHCard
import org.sensorhub.android.ui.components.StatusDot
import org.sensorhub.android.ui.theme.OSHShapes
import org.sensorhub.android.ui.theme.OSHTheme
import org.sensorhub.android.ui.theme.Dimensions

@Composable
fun PtzCommandCard(
    controlStream: RemoteControlStream,
    onPtz: (PtzCommand) -> Unit,
    onAbsolutePtz: (String, Double) -> Unit
) {
    val panRange = controlStream.absolutePanRange
    val tiltRange = controlStream.absoluteTiltRange
    val zoomRange = controlStream.absoluteZoomRange
    var pan by rememberSaveable(controlStream.controlStreamId) { mutableFloatStateOf(panRange.initialValue()) }
    var tilt by rememberSaveable(controlStream.controlStreamId) { mutableFloatStateOf(tiltRange.initialValue()) }
    var zoom by rememberSaveable(controlStream.controlStreamId) { mutableFloatStateOf(zoomRange.initialValue()) }
    var relativeExpanded by rememberSaveable(controlStream.controlStreamId) { mutableStateOf(true) }
    var absoluteExpanded by rememberSaveable(controlStream.controlStreamId) { mutableStateOf(true) }

    OSHCard(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimensions.screenHorizontal)
    ) {
        Column(
            Modifier.padding(Dimensions.cardContent)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.ControlCamera,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(Dimensions.titleGap))
                Text(
                    stringResource(R.string.system_detail_ptz_controls),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                StatusDot("started")
            }
            if (controlStream.supportsRelativePan || controlStream.supportsRelativeTilt || controlStream.supportsRelativeZoom) {
                PtzSection(
                    title = stringResource(R.string.system_detail_relative_controls),
                    expanded = relativeExpanded,
                    Icons.Default.Gamepad,
                    onExpandedChange = { relativeExpanded = it },
                ) {
                    RelativePtzControls(controlStream, onPtz)
                }
            }
            if (panRange != null || tiltRange != null || zoomRange != null) {
                PtzSection(
                    title = stringResource(R.string.system_detail_absolute_position),
                    expanded = absoluteExpanded,
                    Icons.Filled.Tune,
                    onExpandedChange = { absoluteExpanded = it },
                ) {
                    panRange?.let {
                        AbsolutePtzSlider(
                            stringResource(R.string.system_detail_pan),
                            pan,
                            it
                        ) {
                            pan = it; onAbsolutePtz("pan", it.toDouble())
                        }
                    }
                    tiltRange?.let {
                        AbsolutePtzSlider(
                            stringResource(R.string.system_detail_tilt),
                            tilt,
                            it
                        ) {
                            tilt = it; onAbsolutePtz("tilt", it.toDouble())
                        }
                    }
                    zoomRange?.let {
                        AbsolutePtzSlider(
                            stringResource(R.string.system_detail_zoom),
                            zoom,
                            it
                        ) {
                            zoom = it; onAbsolutePtz("zoom", it.toDouble())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RelativePtzControls(stream: RemoteControlStream, onPtz: (PtzCommand) -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row {
                Spacer(Modifier.size(Dimensions.controlButtonSize))
                PtzButton(
                    Icons.Default.KeyboardArrowUp,
                    stringResource(R.string.system_detail_tilt_up),
                    stream.supports(PtzCommand.TILT_UP),
                    size = Dimensions.controlButtonSize
                ) {
                    onPtz(PtzCommand.TILT_UP)
                }
                Spacer(Modifier.size(Dimensions.controlButtonSize))
            }
        Spacer(Modifier.size(Dimensions.compactGap))
        Row {
                PtzButton(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    stringResource(R.string.system_detail_pan_left),
                    stream.supports(PtzCommand.PAN_LEFT),
                    size = Dimensions.controlButtonSize
                ) { onPtz(PtzCommand.PAN_LEFT) }
                Spacer(Modifier.size(Dimensions.compactGap))
                PtzButton(
                    Icons.Default.Home,
                    stringResource(R.string.system_detail_home),
                    true,
                    size = Dimensions.controlButtonSize
                ) { onPtz(PtzCommand.PAN_RIGHT) }
                Spacer(Modifier.size(Dimensions.compactGap))
                PtzButton(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    stringResource(R.string.system_detail_pan_right),
                    stream.supports(PtzCommand.PAN_RIGHT),
                    size = Dimensions.controlButtonSize
                ) { onPtz(PtzCommand.PAN_RIGHT) }
            }
        Spacer(Modifier.size(Dimensions.compactGap))
        Row {
                Box(Modifier.padding(top = 10.dp)) {
                    PtzButton(
                        Icons.Default.Remove,
                        stringResource(R.string.system_detail_zoom_out),
                        stream.supports(PtzCommand.ZOOM_OUT),
                        size = Dimensions.controlButtonSizeSmall
                    ) {
                        onPtz(PtzCommand.ZOOM_OUT)
                    }
                }
                Spacer(Modifier.size(Dimensions.compactGap))
                PtzButton(
                    Icons.Default.KeyboardArrowDown,
                    stringResource(R.string.system_detail_tilt_down),
                    stream.supports(PtzCommand.TILT_DOWN),
                    size = Dimensions.controlButtonSize
                ) {
                    onPtz(PtzCommand.TILT_DOWN)
                }
                Spacer(Modifier.size(Dimensions.compactGap))
                Box(Modifier.padding(top = 10.dp)) {
                    PtzButton(
                        Icons.Default.Add,
                        stringResource(R.string.system_detail_zoom_in),
                        stream.supports(PtzCommand.ZOOM_IN),
                        size = Dimensions.controlButtonSizeSmall
                    ) {
                        onPtz(PtzCommand.ZOOM_IN)
                    }
                }
            }
    }
}

@Composable
private fun PtzSection(
    title: String,
    expanded: Boolean,
    imageVector: ImageVector,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = Dimensions.contentGap)
            .clip(OSHShapes.medium)
    ) {
        Column(Modifier.padding(Dimensions.compactGap)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onExpandedChange(!expanded) }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(
                            if (expanded) R.string.ui_collapse else R.string.ui_expand
                        ),
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(content = content)
            }
        }
    }
}

@Composable
private fun AbsolutePtzSlider(
    label: String,
    value: Float,
    range: PtzAxisRange,
    committed: (Float) -> Unit
) {
    var sliderValue by remember(label) { mutableFloatStateOf(value) }

    Column(
        Modifier.padding(top = Dimensions.contentGap)
    ) {
        Row(
            Modifier.fillMaxWidth()
        ) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
             if (range.unit != null)
                 Surface(
                     color = MaterialTheme.colorScheme.surfaceContainerHigh,
                     shape = MaterialTheme.shapes.small
                 ) {
                     Text(
                         text = sliderValue.format(range.unit),
                         style = MaterialTheme.typography.labelLarge,
                         modifier = Modifier.padding(
                             horizontal = 12.dp,
                             vertical = 6.dp
                         )
                     )
                 }
        }
        Slider(
            sliderValue,
            { sliderValue = it },
            valueRange = range.minimum..range.maximum,
            onValueChangeFinished = { committed(sliderValue) },
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}

private fun PtzAxisRange?.initialValue() = 0f.coerceIn(this?.minimum ?: 0f, this?.maximum ?: 0f)
private fun Float.format(unit: String) =
    if (unit == "deg") "${toInt()}°" else if (unit == "1") "${toInt()}×" else "$this $unit".trim()

@Composable
private fun PtzButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    size: Dp,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val colors = MaterialTheme.colorScheme
    val backgroundColor = when {
        !enabled -> colors.onSurface.copy(alpha = 0.12f)
        pressed -> colors.secondaryContainer
        else -> colors.surfaceContainerHigh
    }

    val contentColor = when {
        !enabled -> colors.onSurface.copy(alpha = 0.38f)
        else -> colors.onSurface
    }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(
                onClick = onClick,
                enabled = enabled,
                interactionSource = interaction,
                indication = null
            ), contentAlignment = Alignment.Center
    ) { Icon(icon, description, tint = contentColor) }
}

@Preview()
@Composable
private fun PtzCommandCardPreview() {
    val controlStream = RemoteControlStream(
        controlStreamId = "0134",
        supportsAbsolutePan = true,
        supportsAbsoluteTilt = true,
        supportsAbsoluteZoom = true,
        supportsRelativePan = true,
        supportsRelativeTilt = true,
        supportsRelativeZoom = true,
        supportsPresets = true,
        absolutePanRange = PtzAxisRange(-180f, 180f, "deg"),
        absoluteTiltRange = PtzAxisRange(-90f, 0f, "deg"),
        absoluteZoomRange = PtzAxisRange(1f, 9999f, "1"),
    )

    OSHTheme {
        PtzCommandCard(
            controlStream = controlStream,
            onPtz = {},
            onAbsolutePtz = { _, _ -> },
        )
    }
}
