package org.sensorhub.android.ui.features.nodes.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.sensorhub.android.R
import org.sensorhub.android.ui.components.OSHButton
import org.sensorhub.android.ui.components.OSHCard
import org.sensorhub.android.ui.components.OSHInputField
import org.sensorhub.android.ui.components.OSHSegmentedButton
import org.sensorhub.android.ui.components.OSHStatusChip
import org.sensorhub.android.ui.components.OSHSwitchRow
import org.sensorhub.android.ui.components.OSHTonalButton
import org.sensorhub.android.ui.components.OSHTopAppBarWithBack
import org.sensorhub.android.ui.components.OshStatusTone
import org.sensorhub.android.ui.theme.OSHTheme
import org.sensorhub.android.ui.theme.Dimensions
import org.sensorhub.android.ui.theme.Spacing

@Composable
fun ServerFormScreen(
    onBackClick: () -> Unit,
    profileId: String? = null,
    viewModel: ServerFormViewModel = viewModel()
) {
    LaunchedEffect(profileId) {
        viewModel.loadProfile(profileId)
    }

    val state = viewModel.state
    var isPasswordVisible by rememberSaveable(profileId) { mutableStateOf(false) }
    var isClientSecretVisible by rememberSaveable(profileId) { mutableStateOf(false) }

    Scaffold(
        topBar = {
            OSHTopAppBarWithBack(
                title = stringResource(if (viewModel.isEdit) R.string.title_edit_server else R.string.add_server),
                onBackClick = onBackClick
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item {
                OSHCard(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimensions.screenHorizontal)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.ui_connection), style = MaterialTheme.typography.titleMedium)
                        OSHSegmentedButton(
                            options = listOf(
                                stringResource(R.string.btn_csapi_client),
                                stringResource(R.string.btn_sost_client),
                            ),
                            selectedIndex = if (state.useConSysClient) 0 else 1,
                            onOptionSelected = { viewModel.updateUseConSysClient(it == 0) }
                        )
                        OSHInputField(
                            value = state.serverName,
                            onValueChange = { viewModel.updateServerName(it) },
                            label = stringResource(R.string.hint_server_name),
                            error = viewModel.nameError
                        )
                        OSHInputField(
                            value = state.endpointUrl,
                            onValueChange = { viewModel.updateEndpointUrl(it) },
                            label = stringResource(R.string.ui_connection_url),
                            error = viewModel.endpointUrlError,
                            placeholder = {
                                Text(
                                    stringResource(R.string.ui_https_ip_port_sensorhub_api),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                        )
                        OSHSwitchRow(
                            title = stringResource(R.string.switch_disable_ssl),
                            subtitle = stringResource(R.string.disable_ssl_description),
                            checked = state.disableSSL,
                            onCheckedChange = viewModel::updateDisableSsl,
                        )
                        if (state.disableSSL) {
                            OSHStatusChip(
                                label = stringResource(R.string.disable_ssl_warning),
                                tone = OshStatusTone.Warning,
                            )
                        }
                    }
                }
            }
            item {
                OSHCard(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimensions.screenHorizontal)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.ui_authentication), style = MaterialTheme.typography.titleMedium)
                        OSHInputField(
                            value = state.username,
                            onValueChange = { viewModel.updateUsername(it) },
                            label = stringResource(R.string.ui_username_optional),
                            error = viewModel.usernameError,
                        )
                        OSHInputField(
                            value = state.password,
                            onValueChange = { viewModel.updatePassword(it) },
                            label = stringResource(R.string.ui_password_optional),
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = stringResource(R.string.ui_toggle_password_visibility)
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            )
                        )
                    }
                }
            }
            item {
                if (state.useConSysClient) {
                    OSHCard(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Dimensions.screenHorizontal)
                    ) {
                        OSHSwitchRow(
                            title = stringResource(R.string.ui_enable_oauth),
                            checked = state.enableOAuth,
                            onCheckedChange = { viewModel.updateEnableOAuth(it) },
                        )
                        if (state.enableOAuth) {
                            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                OSHInputField(
                                    value = state.tokenEndpoint,
                                    onValueChange = { viewModel.updateTokenEndpoint(it) },
                                    label = stringResource(R.string.ui_token_endpoint),
                                    error = viewModel.tokenEndpointError,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                )
                                OSHInputField(
                                    value = state.clientId,
                                    onValueChange = { viewModel.updateClientId(it) },
                                    label = stringResource(R.string.hint_client_id),
                                    error = viewModel.clientIdError,
                                )
                                OSHInputField(
                                    value = state.clientSecret,
                                    onValueChange = { viewModel.updateClientSecret(it) },
                                    label = stringResource(R.string.hint_client_secret),
                                    error = viewModel.clientSecretError,
                                    visualTransformation = if (isClientSecretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            isClientSecretVisible = !isClientSecretVisible
                                        }) {
                                            Icon(
                                                imageVector = if (isClientSecretVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = stringResource(R.string.ui_toggle_secret_visibility)
                                            )
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Done
                                    )
                                )
                            }
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OSHButton(
                        onClick = {
                            if (viewModel.saveProfile()) onBackClick()
                        },
                        text = if (viewModel.isEdit) stringResource(R.string.ui_save_changes) else stringResource(R.string.btn_add_server),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !viewModel.isTestingConnection
                    )

                    OSHTonalButton(
                        onClick = { viewModel.testConnection() },
                        text = if (viewModel.isTestingConnection) stringResource(R.string.ui_testing)
                        else stringResource(R.string.action_test_connection),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !viewModel.isTestingConnection
                    )
                    viewModel.connectionTestResult?.let { result ->
                        ResultMessage(
                            label = result,
                            successful = viewModel.connectionTestSuccessful,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultMessage(
    label: String,
    successful: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OSHStatusChip(
            label = stringResource(if (successful) R.string.ui_connected else R.string.ui_connection_failed),
            tone = if (successful) OshStatusTone.Success else OshStatusTone.Error,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}


@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun ServerFormScreenPreview() {
    OSHTheme {
        ServerFormScreen(
            onBackClick = {},
            "0",
        )
    }
}
