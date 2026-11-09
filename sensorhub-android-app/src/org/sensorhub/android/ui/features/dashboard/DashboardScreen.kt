package org.sensorhub.android.ui.features.dashboard

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.preference.PreferenceManager
import org.sensorhub.android.R
import org.sensorhub.android.ui.features.sensors.data.SensorRegistry
import org.sensorhub.android.ui.HubUiState
import org.sensorhub.android.ui.SensorHubViewModel
import org.sensorhub.android.ui.components.OSHButton
import org.sensorhub.android.ui.components.OSHCard
import org.sensorhub.android.ui.components.OSHStatusChip
import org.sensorhub.android.ui.components.OSHStatusRow
import org.sensorhub.android.ui.components.OSHTextInputDialog
import org.sensorhub.android.ui.components.OSHTopAppBarWithLogo
import org.sensorhub.android.ui.components.OshStatusTone
import org.sensorhub.android.ui.theme.OSHTheme
import org.sensorhub.android.ui.theme.Spacing
import org.sensorhub.api.module.ModuleEvent.ModuleState

@Composable
fun DashboardRoute(onNavigateToSettings: () -> Unit, viewModel: SensorHubViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissions = permissionsForEnabledSensors(context)
    fun permissionsGranted() = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (permissionsGranted()) viewModel.startHub()
        else viewModel.reportError(context.getString(R.string.ui_grant_required_sensor_permissions))
    }
    LaunchedEffect(Unit) {
        if (permissionsGranted()) viewModel.connect()
    }
    DashboardScreen(
        state = state,
        onNavigateToSettings = onNavigateToSettings,
        onRunNameChange = viewModel::setRunName,
        onStart = { if (permissionsGranted()) viewModel.startHub() else launcher.launch(permissions) },
        onStop = viewModel::stopHub,
    )
}

private fun permissionsForEnabledSensors(context: android.content.Context): Array<String> {
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    return SensorRegistry.requiredPermissions(prefs, Build.VERSION.SDK_INT)
}

@Composable
fun DashboardScreen(
    state: HubUiState,
    onNavigateToSettings: () -> Unit,
    onRunNameChange: (String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    var showRunNameDialog by rememberSaveable { mutableStateOf(false) }
    var runNameDraft by rememberSaveable { mutableStateOf("") }
    val running = state.hubStatus == ModuleState.STARTED
    val busy = state.connecting || state.hubStatus in listOf(ModuleState.STARTING, ModuleState.STOPPING)
    val buttonText = when {
        state.connecting -> stringResource(R.string.ui_connecting)
        state.hubStatus == ModuleState.STARTING -> stringResource(R.string.ui_starting)
        state.hubStatus == ModuleState.STOPPING -> stringResource(R.string.ui_stopping)
        running -> stringResource(R.string.ui_stop_streaming)
        else -> stringResource(R.string.ui_start_streaming)
    }
    if (showRunNameDialog) {
        OSHTextInputDialog(
            onDismissRequest = { showRunNameDialog = false },
            dialogTitle = stringResource(R.string.ui_start_streaming_title),
            dialogText = stringResource(R.string.ui_run_name),
            onConfirmation = {
                onRunNameChange(runNameDraft.trim())
                showRunNameDialog = false
                onStart()
            },
            icon = Icons.Default.PlayCircleFilled,
            initialValue = runNameDraft,
            placeholder = stringResource(R.string.ui_run_name_placeholder)
        )
    }
    Scaffold(topBar = {
        OSHTopAppBarWithLogo(title = stringResource(R.string.app_name), actions = {
            IconButton(onClick = onNavigateToSettings) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.action_settings))
            }
        })
    },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(top = Spacing.md, bottom = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                OSHCard(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md)) {
                    OSHStatusRow(
                        title = stringResource(R.string.ui_smarthub),
                        subtitle = state.runName.takeIf { running }.orEmpty(),
                        status = if (state.error != null) "error" else state.hubStatus.name,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (running && state.serverStatuses.isNotEmpty()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = Spacing.md),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        state.serverStatuses.forEach { server ->
                            val hasError = !server.errorText.isNullOrBlank()
                            val started = server.moduleState == ModuleState.STARTED.name
                            val summary = stringResource(when {
                                hasError -> R.string.destination_error
                                !started -> R.string.destination_not_started
                                server.sensorGroups.isEmpty() -> R.string.destination_waiting
                                server.allOk -> R.string.destination_active
                                else -> R.string.destination_attention
                            })
                            OSHStatusRow(
                                title = server.serverName,
                                subtitle = if (hasError) {
                                    "$summary\n${server.errorText}"
                                } else {
                                    summary
                                },
                                status = when {
                                    hasError -> "error"
                                    started && server.allOk -> "ok"
                                    else -> "nok"
                                },
                            )
                        }
                    }
                }
            }
            item {
                OSHButton(
                    onClick = {
                        if (running) onStop() else {
                            runNameDraft = state.runName
                            showRunNameDialog = true
                        }
                    },
                    text = buttonText,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md)
                )
            }
            if (!running) {
                item {
                    Text(
                        stringResource(R.string.sensors_live_start_run),
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.lg),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (state.sensorCards.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.sensors_live_no_enabled),
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.lg),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.sensorCards, key = { "sensor:${it.id}" }) { sensor ->
                    Box(Modifier.padding(horizontal = Spacing.md)) {
                        SensorOutputCard(sensor)
                    }
                }
            }

            state.error?.let { error ->
                item {
                    OSHCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md),
                    ) {
                        Column(
                            modifier = Modifier.padding(Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            OSHStatusChip(
                                label = stringResource(R.string.destination_error),
                                tone = OshStatusTone.Error,
                            )
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
private fun DashboardPreview() {
    OSHTheme {
        DashboardScreen(
            HubUiState(
                hubStatus = ModuleState.STOPPED,
                runName = "Field survey",
            ),
            onNavigateToSettings = {},
            onRunNameChange = {},
            onStart = {},
            onStop = {},
        )
    }
}
