package org.sensorhub.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.sensorhub.android.ui.theme.OverlayDark
import org.sensorhub.android.ui.theme.OverlayLight

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun <T> OSHDropDown(
    summary: String,
    items: List<T>,
    selectedIds: Set<String>,
    itemId: (T) -> String,
    itemLabel: (T) -> String,
    itemSummary: (T) -> String,
    onSelectionChange: (String, Boolean) -> Unit,
    emptyText: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var anchorWidth by remember { mutableIntStateOf(0) }
    val menuWidth = with(LocalDensity.current) { anchorWidth.toDp() }
    val selectedItems = items.filter { itemId(it) in selectedIds }

    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().onSizeChanged { anchorWidth = it.width }) {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (selectedItems.isEmpty()) {
                    Text(
                        text = summary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        maxItemsInEachRow = 2,
                    ) {
                        selectedItems.forEach { item ->
                            Surface(
                                color = OverlayDark,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.padding(end = 8.dp, bottom = 4.dp),
                            ) {
                                Text(
                                    text = itemLabel(item),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(
                expanded = expanded && enabled,
                onDismissRequest = { expanded = false },
                modifier = Modifier.width(menuWidth)
            ) {
                if (items.isEmpty()) {
                    DropdownMenuItem(text = { Text(emptyText) }, onClick = {}, enabled = false)
                }
                items.forEach { item ->
                    val id = itemId(item)
                    val checked = id in selectedIds
                    DropdownMenuItem(
                        text = {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = itemLabel(item),
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = itemSummary(item),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        leadingIcon = { Checkbox(checked = checked, onCheckedChange = null) },
                        modifier = Modifier.semantics { selected = checked },
                        onClick = { onSelectionChange(id, !checked) }
                    )
                }
            }
        }
    }
}
