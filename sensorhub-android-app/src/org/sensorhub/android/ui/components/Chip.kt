package org.sensorhub.android.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.sensorhub.android.ui.theme.OSHTheme


@Composable
fun OSHFilterChip(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = true,
) {
    FilterChip(
        onClick = onClick,
        label = {
            Text(text)
        },
        selected = selected,
        leadingIcon = if (selected) {
            {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected filter",
                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                )
            }
        } else {
            null
        }
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun ChipPreview() {
    OSHTheme {
        Column {
            OSHFilterChip(
                onClick = {},
                text = "Bluetooth",
                selected = true
            )
        }
    }
}
