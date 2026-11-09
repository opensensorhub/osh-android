package org.sensorhub.android.ui.features.nodes.ondevice

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.sensorhub.android.R
import org.sensorhub.android.ui.components.OSHCard
import org.sensorhub.android.ui.components.OSHInfoRow
import org.sensorhub.android.ui.components.OSHInputField
import org.sensorhub.android.ui.components.OSHSwitchRow
import org.sensorhub.android.ui.components.OSHTextInputDialog
import org.sensorhub.android.ui.components.OSHTopAppBarWithBack
import org.sensorhub.android.ui.theme.Dimensions

@Composable
fun OnDeviceNodeScreen(
    onBackClick: () -> Unit,
    viewModel: OnDeviceViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editingName by rememberSaveable { mutableStateOf(false) }

    if (editingName) {
        OSHTextInputDialog(
            onDismissRequest = { editingName = false },
            onConfirmation = { name ->
                viewModel.updateDeviceName(name)
                editingName = false
            },
            dialogTitle = stringResource(R.string.device_name),
            dialogText = stringResource(R.string.device_name),
            icon = Icons.Filled.Info,
            initialValue = state.deviceName,
        )
    }

    Scaffold(
        topBar = {
            OSHTopAppBarWithBack(
                title = stringResource(R.string.this_device),
                onBackClick = onBackClick,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(16.dp))
            OSHCard(Modifier.padding(horizontal = Dimensions.screenHorizontal)) {
                OSHInfoRow(
                    label = stringResource(R.string.device_name),
                    value = state.deviceName,
                    onClick = { editingName = true },
                )
                OSHInfoRow(
                    label = stringResource(R.string.device_ip_address),
                    value = state.ipAddress.ifBlank { stringResource(R.string.ip_address_unavailable) },
                )
            }
            Spacer(Modifier.height(16.dp))
            OSHCard(Modifier.padding(horizontal = Dimensions.screenHorizontal)) {
                OSHSwitchRow(
                    title = stringResource(R.string.enable_sos_service),
                    subtitle = stringResource(R.string.summary_sos),
                    checked = state.services.sosEnabled,
                    onCheckedChange = viewModel::setSosEnabled,
                )
                OSHSwitchRow(
                    title = stringResource(R.string.enable_csapi_service),
                    subtitle = stringResource(R.string.summary_csapi),
                    checked = state.services.csApiEnabled,
                    onCheckedChange = viewModel::setCsApiEnabled,
                )
                OSHSwitchRow(
                    title = stringResource(R.string.enable_discovery_service),
                    subtitle = stringResource(R.string.summary_discovery),
                    checked = state.services.discoveryEnabled,
                    onCheckedChange = viewModel::setDiscoveryEnabled,
                )
                if (state.services.discoveryEnabled) {
                    OSHInputField(
                        value = state.services.rulesLink,
                        onValueChange = viewModel::setDiscoveryRulesLink,
                        label = stringResource(R.string.title_discovery_rules),
                    )
                }
            }
        }
    }
}
