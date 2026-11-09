package org.sensorhub.android.ui.features.settings.help

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.sensorhub.android.R
import org.sensorhub.android.ui.components.OSHExpandableCard
import org.sensorhub.android.ui.components.OSHTopAppBarWithBack
import org.sensorhub.android.ui.theme.OSHTheme
import org.sensorhub.android.ui.theme.Primary


@Composable
fun HelpFaqScreen(
    items: List<FaqItem>,
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            OSHTopAppBarWithBack(
                title = stringResource(R.string.title_help_faq),
                onBackClick = onBackClick
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            items(
                items = items,
                key = { item ->
                    when (item) {
                        is FaqItem.Header -> "header:${item.title}"
                        is FaqItem.Entry -> "entry:${item.question}"
                    }
                },
            ) { item ->
                when (item) {
                    is FaqItem.Header -> {
                        Text(
                            text = item.title.uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            color = Primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 12.dp)
                        )
                    }
                    is FaqItem.Entry -> {
                        OSHExpandableCard(
                            title = item.question,
                            modifier = Modifier.padding(vertical = 4.dp),
                            expandedContent = {
                                Text(
                                    text = item.answer,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}



private fun buildSampleFaqItems(): List<FaqItem> = listOf(
    FaqItem.Header("Getting Started"),
    FaqItem.Entry(
        "What is OpenSensorHub?",
        "OpenSensorHub is a sensor aggregation and streaming app. It collects data from your device\u2019s built-in sensors and connected Bluetooth peripherals, then streams that data to an OpenSensorHub server in real time."
    ),
    FaqItem.Entry(
        "How do I connect to a server?",
        "Go to the Settings tab, tap \"Manage Servers\", then tap the add button. Enter a name for the profile, the server host address, port, and endpoint path."
    ),
)

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun HelpFaqScreenPreview() {
    OSHTheme {
        HelpFaqScreen(
            items = buildSampleFaqItems(),
            onBackClick = {}
        )
    }
}
