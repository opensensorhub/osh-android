package org.sensorhub.android.ui.components

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.sensorhub.android.R
import org.sensorhub.android.ui.theme.OSHTheme

@Composable
fun OSHAlertDialog(
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit,
    dialogTitle: String,
    dialogText: String,
    icon: ImageVector,
) {
    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        iconContentColor = MaterialTheme.colorScheme.primary,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = dialogTitle,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        text = {
            Text(text = dialogText)
        },
        onDismissRequest = { onDismissRequest() },
        confirmButton = {
            OSHButton(
                onClick = { onConfirmation() },
                text = stringResource(R.string.btn_ok)
            )
        },
        dismissButton = {
            TextButton(onClick = { onDismissRequest() }) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}

@Composable
fun OSHAlertDialogWithoutDismiss(
    onConfirmation: () -> Unit,
    dialogTitle: String,
    dialogText: String,
    icon: ImageVector,
) {
    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        iconContentColor = MaterialTheme.colorScheme.primary,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = dialogTitle,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        text = {
            Text(text = dialogText)
        },
        onDismissRequest = {},
        confirmButton = {
            OSHButton(
                onClick = { onConfirmation() },
                text = stringResource(R.string.btn_ok)
            )
        }
    )
}

@Composable
fun OSHTextInputDialog(
    onDismissRequest: () -> Unit,
    onConfirmation: (String) -> Unit,
    dialogTitle: String,
    dialogText: String,
    icon: ImageVector,
    initialValue: String = "",
    placeholder: String = ""
) {
    var textValue by rememberSaveable(initialValue) { mutableStateOf(initialValue) }

    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        iconContentColor = MaterialTheme.colorScheme.primary,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = dialogTitle,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        text = {
            Column {
                Text(text = dialogText)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    placeholder = { Text(placeholder) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    )
                )
            }
        },
        onDismissRequest = { onDismissRequest() },
        confirmButton = {
            OSHButton(
                onClick = { onConfirmation(textValue) },
                text = stringResource(R.string.btn_ok)
            )
        },
        dismissButton = {
            TextButton(onClick = { onDismissRequest() }) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}

@Composable
fun OSHSingleChoiceDialog(
    onDismissRequest: () -> Unit,
    dialogTitle: String,
    icon: ImageVector,
    options: List<String>,
    selectedIndex: Int,
    onOptionSelected: (Int) -> Unit
) {
    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        iconContentColor = MaterialTheme.colorScheme.primary,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = dialogTitle,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        text = {
            Column {
                options.forEachIndexed { index, label ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = index == selectedIndex,
                                onClick = {
                                    onOptionSelected(index)
                                    onDismissRequest()
                                }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = index == selectedIndex,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary,
                                unselectedColor = MaterialTheme.colorScheme.outline,
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = label)
                    }
                }
            }
        },
        onDismissRequest = { onDismissRequest() },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { onDismissRequest() }) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}

@SuppressLint("MissingPermission")
@Composable
fun OSHBluetoothPickerDialog(
    devices: List<BluetoothDevice>,
    currentAddress: String,
    onDeviceSelected: (address: String, displayName: String) -> Unit,
    onDismiss: () -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
) {
    var manualEntry by remember { mutableStateOf("") }
    var showManualInput by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { onStartScan() }
    DisposableEffect(Unit) { onDispose { onStopScan() } }

    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        iconContentColor = MaterialTheme.colorScheme.primary,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Bluetooth, contentDescription = null) },
        title = { Text(stringResource(R.string.title_select_device)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (showManualInput) {
                    OutlinedTextField(
                        value = manualEntry,
                        onValueChange = { manualEntry = it },
                        placeholder = { Text(stringResource(R.string.hint_manual_device)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = {
                        if (manualEntry.isNotBlank()) {
                            onDeviceSelected(manualEntry.trim(), manualEntry.trim())
                        }
                    }) {
                        Text(stringResource(R.string.btn_ok))
                    }
                    HorizontalDivider()
                }

                TextButton(onClick = { showManualInput = !showManualInput }) {
                    Text(stringResource(R.string.manual_entry_option))
                }

                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                if (devices.isEmpty()) {
                    Text(
                        text = "Scanning...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    devices.forEach { device ->
                        val name = device.name ?: "Unknown Device"
                        val mac = device.address
                        val displayText = "$name ($mac)"
                        val isSelected = mac == currentAddress

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = isSelected,
                                    role = Role.RadioButton,
                                    onClick = { onDeviceSelected(mac, displayText) }
                                )
                                .padding(vertical = 10.dp, horizontal = 4.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = mac,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun DialogPreview() {
    OSHTheme {
        OSHAlertDialog(
            onConfirmation = {},
            onDismissRequest = {},
            dialogText = "Please enter a run name",
            dialogTitle = "Run Name",
            icon = Icons.Filled.PlayArrow
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun DialogNoDismissPreview() {
    OSHTheme {
        OSHAlertDialogWithoutDismiss(
            onConfirmation = {},
            dialogText = "A software platform for building smart sensor networks and the internet of things",
            dialogTitle = "OSH-Android",
            icon = Icons.Filled.Info
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun TextInputDialogPreview() {
    OSHTheme {
        OSHTextInputDialog(
            onDismissRequest = {},
            onConfirmation = {},
            dialogTitle = "Run Name",
            dialogText = "Please enter the name for this run",
            icon = Icons.Filled.PlayArrow,
            initialValue = "Run-20260708-120000",
            placeholder = "Enter run name"
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun SingleChoiceDialogPreview() {
    OSHTheme {
        OSHSingleChoiceDialog(
            onDismissRequest = {},
            dialogTitle = "Language",
            icon = Icons.Filled.Language,
            options = listOf("English", "中文 (台灣)", "Español", "Français", "Deutsch"),
            selectedIndex = 0,
            onOptionSelected = {}
        )
    }
}
