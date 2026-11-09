package org.sensorhub.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.sensorhub.android.ui.theme.LocalOshStatusColors
import org.sensorhub.android.ui.theme.Spacing

enum class OshStatusTone {
    Success,
    Warning,
    Error,
    Neutral,
}

@Composable
fun OSHStatusChip(
    label: String,
    tone: OshStatusTone,
    modifier: Modifier = Modifier,
) {
    val statusColors = LocalOshStatusColors.current
    val colors = when (tone) {
        OshStatusTone.Success -> statusColors.success to statusColors.onSuccess
        OshStatusTone.Warning -> statusColors.warning to statusColors.onWarning
        OshStatusTone.Error -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        OshStatusTone.Neutral -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier.semantics { contentDescription = label },
        shape = MaterialTheme.shapes.small,
        color = colors.first,
        contentColor = colors.second,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = Spacing.lg)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(
                Modifier
                    .size(Spacing.sm)
                    .clip(CircleShape)
                    .background(colors.second),
            )
            Spacer(Modifier.width(Spacing.xs))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
