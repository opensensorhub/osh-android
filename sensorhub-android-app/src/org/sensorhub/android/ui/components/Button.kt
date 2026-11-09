package org.sensorhub.android.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.sensorhub.android.ui.theme.OSHTheme
import org.sensorhub.android.ui.theme.OnPrimary
import org.sensorhub.android.ui.theme.Primary
import org.sensorhub.android.ui.theme.SecondaryContainer


private val PillShape = RoundedCornerShape(25)

@Composable
fun OSHButton(
    onClick: () -> Unit,
    text: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    ElevatedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = PillShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Primary,
            contentColor = OnPrimary
        ),

    ) {
        Text(text)
    }
}

@Composable
fun IconButtonWithText(
    onClick: () -> Unit,
    imageVector: ImageVector,
    text: String,
) {
    Button(
        onClick = onClick
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize)
        )

        Spacer(Modifier.width(ButtonDefaults.IconSpacing))

        Text(text = text)
    }
}

@Composable
fun OSHTonalButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = PillShape,
    ) {
        Text(text)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OSHSegmentedButton(
    modifier: Modifier = Modifier,
    options: List<String>,
    selectedIndex: Int = 0,
    onOptionSelected: (Int) -> Unit = {}
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, label ->
            SegmentedButton(
                modifier = Modifier.weight(1f),
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = options.size,
                    baseShape = RoundedCornerShape(25)
                ),
                onClick = { onOptionSelected(index) },
                selected = index == selectedIndex,
                label = { Text(label, maxLines = 1) },
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = Primary,
                    activeContentColor = OnPrimary,
                    activeBorderColor = Primary
                ),
                icon = {},
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun ButtonPreview() {
    OSHTheme {
        Column {
            OSHButton(onClick = {}, text = "Primary Button")
            OSHTonalButton(onClick = {}, text = "Secondary Button")
        }
    }
}


@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun SegmentedButtonPreview() {
    OSHTheme {
        OSHSegmentedButton(
            options = listOf("CS API Client", "SOS-T Client")
        )
    }
}

@Composable
fun OSHAddFAB(onClick: () -> Unit = {}) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = Primary,
        contentColor = OnPrimary,
    ) {
        Icon(imageVector = Icons.Default.Add, contentDescription = "Add new item")
    }
}
