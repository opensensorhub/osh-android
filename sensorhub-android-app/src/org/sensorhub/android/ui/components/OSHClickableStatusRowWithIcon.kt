package org.sensorhub.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.sensorhub.android.ui.theme.Spacing


@Composable
fun OSHClickableStatusRowWithIcon(
    title: String,
    modifier: Modifier = Modifier,
    imageVector: ImageVector,
    contentDescription: String,
    subtitle: String,
    summary: String,
    status: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(Spacing.lg)
    ) {
        Icon(imageVector, contentDescription = contentDescription)
        Spacer(modifier = Modifier.width(Spacing.lg))

//        if (subtitle != null) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ){
                    StatusDot(status = status)
                    Spacer(modifier = Modifier.width(Spacing.md))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
//        } else {
//            Row(){
//                StatusDot(status = status)
//                Spacer(modifier = Modifier.width(10.dp))
//                Text(
//                    text = title,
//                    style = MaterialTheme.typography.bodyLarge,
//                    modifier = Modifier.weight(1f)
//                )
//            }
//        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null)
    }
}
