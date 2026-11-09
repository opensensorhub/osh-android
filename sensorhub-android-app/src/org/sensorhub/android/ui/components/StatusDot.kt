package org.sensorhub.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.sensorhub.android.R
import org.sensorhub.android.ui.theme.LocalOshStatusColors

@Composable
fun StatusDot(
    status: String,
    modifier: Modifier = Modifier,
    dotSize: Dp = 10.dp
) {
    val _status = status.trim().lowercase()
    val statusColors = LocalOshStatusColors.current
    val color = when {
        _status == "ok" || _status == "started" || _status == "success" -> statusColors.onSuccess
        _status == "nok" -> statusColors.onWarning
        _status.contains("error") || _status == "failed" -> MaterialTheme.colorScheme.error
        _status.contains("starting") || _status.contains("initializ") -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.outline
    }
    val description = stringResource(when {
        _status == "ok" || _status == "started" -> R.string.health_active
        _status == "nok" -> R.string.health_attention
        _status.contains("error") -> R.string.health_error
        _status.contains("stopping") -> R.string.health_stopping
        _status.contains("stop") -> R.string.health_stopped
        _status.contains("starting") || _status.contains("initializ") -> R.string.health_starting
        else -> R.string.health_unknown
    })
    Box(
        modifier = modifier
            .semantics { contentDescription = description }
            .size(dotSize)
            .background(
                color = color,
                shape = CircleShape
            )
    )
}
