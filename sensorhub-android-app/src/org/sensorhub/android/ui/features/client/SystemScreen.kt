package org.sensorhub.android.ui.features.client

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.sensorhub.android.R
import org.sensorhub.android.ui.components.OSHClickableStatusRowWithIcon
import org.sensorhub.android.ui.components.OSHTopAppBarWithBack
import org.sensorhub.android.ui.features.client.data.RemoteSystem


@Composable
private fun SystemScreen(
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

@Composable
private fun ServerSystemRow(
    system: RemoteSystem,
    isLastInSection: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 16.dp,
                end = 16.dp,
                bottom = if (isLastInSection) 8.dp else 0.dp,
            ),
        shape = if (isLastInSection) {
            RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
        } else {
            RectangleShape
        },
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column {
            HorizontalDivider()
            OSHClickableStatusRowWithIcon(
                title = system.name,
                imageVector = Icons.Filled.Sensors,
                contentDescription = stringResource(R.string.content_desc_system),
                subtitle = system.uid,
                summary = stringResource(R.string.client_datastream_count, system.visualizations.size),
                status = system.status,
                onClick = onClick,
            )
        }
    }
}
