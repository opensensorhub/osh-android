package org.sensorhub.android.ui.features.sensors

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.sensorhub.android.R
import org.sensorhub.android.ui.features.sensors.data.SensorCategory
import org.sensorhub.android.ui.features.sensors.data.SensorChoice
import org.sensorhub.android.ui.features.sensors.data.SensorRegistry
import org.sensorhub.android.ui.features.sensors.data.SensorSettings
import org.sensorhub.android.ui.features.sensors.data.SensorUIOption
import org.sensorhub.android.ui.features.sensors.data.SensorUiEntry
import org.sensorhub.android.ui.components.OSHBluetoothPickerDialog
import org.sensorhub.android.ui.components.OSHClickableRowWithIcon
import org.sensorhub.android.ui.components.OSHExpandableSwitchCard
import org.sensorhub.android.ui.components.OSHFilterChip
import org.sensorhub.android.ui.components.OSHSensorCard
import org.sensorhub.android.ui.components.OSHSingleChoiceDialog
import org.sensorhub.android.ui.components.OSHSwitchRow
import org.sensorhub.android.ui.components.OSHTopAppBarWithLogo
import org.sensorhub.android.ui.theme.OSHTheme

@Composable
fun SensorsScreen(
    onNavigateToSettings : () -> Unit,
    viewModel: SensorsViewModel = viewModel(),
) {
    var selectedCategories by rememberSaveable { mutableStateOf(emptySet<SensorCategory>()) }
    val scrollState = rememberScrollState()
    var activeDialog by rememberSaveable { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scanner = remember(context) { BluetoothScanner(context) }
    val bluetoothDevices = scanner.foundDevices

    val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.all { it }) {
            scanner.startDiscovery()
        }
    }

    DisposableEffect(Unit) {
        onDispose { scanner.stopDiscovery() }
    }

    if (activeDialog != null && activeDialog in SensorRegistry.bluetoothAddressPreferenceKeys) {
        val addressKey = activeDialog!!
        OSHBluetoothPickerDialog(
            devices = bluetoothDevices,
            currentAddress = viewModel.stringStates[addressKey] ?: "",
            onDeviceSelected = { address, _ ->
                viewModel.setStringPref(addressKey, address)
                scanner.stopDiscovery()
                activeDialog = null
            },
            onDismiss = {
                activeDialog = null
                scanner.stopDiscovery()
            },
            onStartScan = { permissionLauncher.launch(requiredPermissions) },
            onStopScan = scanner::stopDiscovery,
        )
    }

    CHOICE_DIALOGS.find { it.prefKey == activeDialog }?.let { spec ->
        val current = viewModel.stringStates[spec.prefKey] ?: spec.defaultValue
        OSHSingleChoiceDialog(
            onDismissRequest = { activeDialog = null },
            dialogTitle = stringResource(spec.titleRes),
            icon = spec.icon,
            options = spec.labels,
            selectedIndex = spec.values.indexOf(current).coerceAtLeast(0),
            onOptionSelected = { index ->
                viewModel.setStringPref(spec.prefKey, spec.values[index])
            },
        )
    }

    Scaffold(
        topBar = {
            OSHTopAppBarWithLogo(
                title = stringResource(R.string.tab_sensors),
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.action_settings),
                        )
                    }
                },
            )
        },
    ) { padding ->
        val resolvedItems = SensorRegistry.entries.map {
            SensorItem(it, enabled = viewModel.toggleStates[it.prefKey] ?: false)
        }
        val filteredItems = if (selectedCategories.isEmpty()) resolvedItems
        else resolvedItems.filter { it.category in selectedCategories }
        val grouped = filteredItems.groupBy { it.category }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(scrollState)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OSHFilterChip(
                    onClick = { selectedCategories = emptySet() },
                    text = stringResource(R.string.category_all),
                    selected = selectedCategories.isEmpty(),
                )
                SensorCategory.entries.forEach { category ->
                    OSHFilterChip(
                        onClick = {
                            selectedCategories = if (category in selectedCategories)
                                selectedCategories - category
                            else
                                selectedCategories + category
                        },
                        text = stringResource(category.labelRes),
                        selected = category in selectedCategories,
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                grouped.forEach { (category, sensors) ->
                    item {
                        Text(
                            text = stringResource(category.labelRes),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                    }
                    items(sensors, key = { it.id }) { sensor ->
                        SensorUiEntry(
                            sensor = sensor,
                            viewModel = viewModel,
                            onActiveDialogChange = { activeDialog = it },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SensorUiEntry(
    sensor: SensorItem,
    viewModel: SensorsViewModel,
    onActiveDialogChange: (String) -> Unit,
) {
    val onToggle = { checked: Boolean ->
        viewModel.toggleSensor(sensor.definition, checked)
    }

    when (val config = sensor.config) {
        is SensorUIOption.Audio -> ConfigurableSensorCard(sensor, onToggle) {
            AudioConfig(
                codec = viewModel.choiceValue(SensorSettings.audioCodec),
                sampleRate = viewModel.choiceValue(SensorSettings.audioSampleRate),
                bitRate = viewModel.choiceValue(SensorSettings.audioBitRate),
                onCodecClick = { onActiveDialogChange(SensorSettings.audioCodec.key) },
                onSampleRateClick = { onActiveDialogChange(SensorSettings.audioSampleRate.key) },
                onBitRateClick = { onActiveDialogChange(SensorSettings.audioBitRate.key) },
            )
        }

        is SensorUIOption.Video -> ConfigurableSensorCard(sensor, onToggle) {
            VideoConfig(
                codec = viewModel.choiceValue(SensorSettings.videoCodec),
                frameRate = viewModel.choiceValue(SensorSettings.videoFrameRate),
                resolution = viewModel.choiceValue(SensorSettings.videoResolution),
                camera = viewModel.choiceValue(SensorSettings.camera),
                onCodecClick = { onActiveDialogChange(SensorSettings.videoCodec.key) },
                onFrameRateClick = { onActiveDialogChange(SensorSettings.videoFrameRate.key) },
                onResolutionClick = { onActiveDialogChange(SensorSettings.videoResolution.key) },
                onCameraClick = { onActiveDialogChange(SensorSettings.camera.key) },
            )
        }

        is SensorUIOption.BluetoothDevice -> ConfigurableSensorCard(sensor, onToggle) {
            val address = viewModel.stringStates[config.addressPrefKey].orEmpty()
            BluetoothDeviceConfig(
                selectLabelRes = config.selectLabelRes,
                currentAddress =  address,
                onSelect = { onActiveDialogChange(config.addressPrefKey) },
            )
        }

        is SensorUIOption.TruPulse -> {
            val dsSpec = CHOICE_DIALOGS.first { it.prefKey == SensorSettings.truPulseSource.key }
            val dsIndex = dsSpec.values.indexOf(
                viewModel.choiceValue(SensorSettings.truPulseSource),
            ).coerceAtLeast(0)

            ConfigurableSensorCard(sensor, onToggle) {
                TruPulseConfig(
                    datasource = dsSpec.labels[dsIndex],
                    deviceAddress = viewModel.stringStates[SensorUIOption.TruPulse.addressPrefKey] ?: "",
                    onDatasourceClick = { onActiveDialogChange(SensorSettings.truPulseSource.key) },
                    onDeviceClick = { onActiveDialogChange(SensorUIOption.TruPulse.addressPrefKey) },
                )
            }
        }

        null -> {
            OSHSensorCard {
                OSHSwitchRow(
                    title = stringResource(sensor.nameRes),
                    checked = sensor.enabled,
                    onCheckedChange = onToggle,
                )
            }
        }
    }
}

@Composable
private fun ConfigurableSensorCard(
    sensor: SensorItem,
    onToggle: (Boolean) -> Unit,
    expandedContent: @Composable () -> Unit,
) {
    OSHExpandableSwitchCard(
        title = stringResource(sensor.nameRes),
        checked = sensor.enabled,
        onCheckedChange = onToggle,
        configHint = stringResource(R.string.hint_tap_to_configure),
        expandedContent = {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            expandedContent()
        },
    )
}

@Composable
private fun AudioConfig(
    codec: String,
    sampleRate: String,
    bitRate: String,
    onCodecClick: () -> Unit,
    onSampleRateClick: () -> Unit,
    onBitRateClick: () -> Unit,
) {
    OSHClickableRowWithIcon(
        title = stringResource(R.string.title_codec),
        subtitle = codec,
        imageVector = Icons.Default.Audiotrack,
        contentDescription = stringResource(R.string.title_codec),
        onClick = onCodecClick,
    )
    OSHClickableRowWithIcon(
        title = stringResource(R.string.title_sample_rate),
        subtitle = stringResource(R.string.sensors_sample_rate_hz, sampleRate),
        imageVector = Icons.Default.Audiotrack,
        contentDescription = stringResource(R.string.title_sample_rate),
        onClick = onSampleRateClick,
    )
    OSHClickableRowWithIcon(
        title = stringResource(R.string.title_bitrate),
        subtitle = stringResource(R.string.sensors_bit_rate_kbps, bitRate),
        imageVector = Icons.Default.Audiotrack,
        contentDescription = stringResource(R.string.title_bitrate),
        onClick = onBitRateClick,
    )
}

@Composable
private fun VideoConfig(
    codec: String,
    frameRate: String,
    resolution: String,
    camera: String,
    onCodecClick: () -> Unit,
    onFrameRateClick: () -> Unit,
    onResolutionClick: () -> Unit,
    onCameraClick: () -> Unit,
) {
    OSHClickableRowWithIcon(
        title = stringResource(R.string.title_camera),
        subtitle = stringResource(R.string.sensors_camera_number, camera),
        imageVector = Icons.Default.Videocam,
        contentDescription = stringResource(R.string.title_camera),
        onClick = onCameraClick,
    )
    OSHClickableRowWithIcon(
        title = stringResource(R.string.title_codec),
        subtitle = codec,
        imageVector = Icons.Default.Videocam,
        contentDescription = stringResource(R.string.title_codec),
        onClick = onCodecClick,
    )
    OSHClickableRowWithIcon(
        title = stringResource(R.string.title_resolution),
        subtitle = resolution,
        imageVector = Icons.Default.Videocam,
        contentDescription = stringResource(R.string.title_resolution),
        onClick = onResolutionClick,
    )
    OSHClickableRowWithIcon(
        title = stringResource(R.string.title_frame_rate),
        subtitle = stringResource(R.string.sensors_frame_rate_fps, frameRate),
        imageVector = Icons.Default.Videocam,
        contentDescription = stringResource(R.string.title_frame_rate),
        onClick = onFrameRateClick,
    )
}

@Composable
private fun BluetoothDeviceConfig(
    @StringRes selectLabelRes: Int,
    currentAddress: String,
    onSelect: () -> Unit,
) {
    OSHClickableRowWithIcon(
        title = stringResource(selectLabelRes),
        subtitle = currentAddress.ifEmpty { stringResource(R.string.summary_select_device) },
        imageVector = Icons.Default.Bluetooth,
        contentDescription = stringResource(selectLabelRes),
        onClick = onSelect,
    )
}

@Composable
private fun TruPulseConfig(
    datasource: String,
    deviceAddress: String,
    onDatasourceClick: () -> Unit,
    onDeviceClick: () -> Unit,
) {
    OSHClickableRowWithIcon(
        title = stringResource(R.string.title_trupulse_datasource),
        subtitle = datasource,
        imageVector = Icons.Default.DevicesOther,
        contentDescription = stringResource(R.string.title_trupulse_datasource),
        onClick = onDatasourceClick,
    )
    OSHClickableRowWithIcon(
        title = stringResource(R.string.title_select_trupulse),
        subtitle = deviceAddress.ifEmpty { stringResource(R.string.summary_select_device) },
        imageVector = Icons.Default.Bluetooth,
        contentDescription = stringResource(R.string.title_select_trupulse),
        onClick = onDeviceClick,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun SensorsScreenPreview() {
    OSHTheme {
        SensorsScreen(
            {}
        )
    }
}

data class SensorItem(val definition: SensorUiEntry, val enabled: Boolean) {
    val id get() = definition.id
    val prefKey get() = definition.prefKey
    val nameRes get() = definition.nameRes
    val category get() = definition.category
    val config get() = definition.config
}

data class ChoiceDialogSpec(
    val setting: SensorChoice,
    @StringRes val titleRes: Int,
    val icon: ImageVector,
    val labels: List<String> = setting.values,
) {
    val prefKey get() = setting.key
    val values get() = setting.values
    val defaultValue get() = setting.defaultValue
}

val CHOICE_DIALOGS = listOf(
    ChoiceDialogSpec(SensorSettings.audioCodec, R.string.title_codec, Icons.Default.Audiotrack),
    ChoiceDialogSpec(SensorSettings.audioSampleRate, R.string.title_sample_rate, Icons.Default.Audiotrack,
        SensorSettings.audioSampleRate.values.map { "$it Hz" }),
    ChoiceDialogSpec(SensorSettings.audioBitRate, R.string.title_bitrate, Icons.Default.Audiotrack,
        SensorSettings.audioBitRate.values.map { "$it kbps" }),
    ChoiceDialogSpec(SensorSettings.videoCodec, R.string.title_codec, Icons.Default.Videocam),
    ChoiceDialogSpec(SensorSettings.videoFrameRate, R.string.title_frame_rate, Icons.Default.Videocam,
        SensorSettings.videoFrameRate.values.map { "$it fps" }),
    ChoiceDialogSpec(SensorSettings.videoResolution, R.string.title_resolution, Icons.Default.Videocam),
    ChoiceDialogSpec(SensorSettings.camera, R.string.title_camera, Icons.Default.Videocam,
        SensorSettings.camera.values.map { "Camera $it" }),
    ChoiceDialogSpec(SensorSettings.truPulseSource, R.string.title_trupulse_datasource, Icons.Default.DevicesOther,
        listOf("Streaming Physical Device", "Simulate Virtual Device")),
)
