package org.sensorhub.android.ui.features.nodes.servers

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.sensorhub.android.R
import org.sensorhub.android.ui.features.nodes.servers.data.ServerConnectionTester
import org.sensorhub.android.ui.features.nodes.servers.data.ServerProfileItem
import org.sensorhub.android.ui.features.nodes.servers.data.ServerProfileRepository
import org.sensorhub.android.ui.features.nodes.servers.data.serverUrlError
import org.sensorhub.android.ui.features.nodes.servers.data.requireHttpsUrl

class ServerFormViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = ServerProfileRepository.getInstance(application)

    var state by mutableStateOf(ServerProfileItem())
        private set

    var nameError by mutableStateOf<String?>(null)
        private set
    var endpointUrlError by mutableStateOf<String?>(null)
        private set

    var tokenEndpointError by mutableStateOf<String?>(null)
        private set
    var clientIdError by mutableStateOf<String?>(null)
        private set
    var clientSecretError by mutableStateOf<String?>(null)
        private set
    var usernameError by mutableStateOf<String?>(null)
        private set

    var connectionTestResult by mutableStateOf<String?>(null)
        private set
    var connectionTestSuccessful by mutableStateOf(false)
        private set

    var isTestingConnection by mutableStateOf(false)
        private set

    private var existingProfileId: String? = null
    var isEdit = false
        private set

    fun loadProfile(profileId: String?) {
        isEdit = profileId != null && profileId != "new"
        if (!isEdit) return

        val profile = repo.getById(profileId) ?: return
        existingProfileId = profile.id
        state = profile.copy(
            password = repo.getPassword(profile.id),
            clientId = repo.getOAuthClientId(profile.id),
            clientSecret = repo.getOAuthClientSecret(profile.id),
            tokenEndpoint = repo.getOAuthTokenEndpoint(profile.id),
        )
    }

    fun updateServerName(value: String) {
        state = state.copy(serverName = value)
        nameError = null
        connectionTestResult = null
        connectionTestSuccessful = false
    }

    fun updateUseConSysClient(value: Boolean) {
        state = state.copy(useConSysClient = value)
        connectionTestResult = null
        connectionTestSuccessful = false
    }

    fun updateEndpointUrl(value: String) {
        state = state.copy(endpointUrl = value)
        endpointUrlError = null
        connectionTestResult = null
        connectionTestSuccessful = false
    }

    fun updateDisableSsl(value: Boolean) {
        state = state.copy(disableSSL = value)
        connectionTestResult = null
        connectionTestSuccessful = false
    }

    fun updateUsername(value: String) {
        state = state.copy(username = value)
        usernameError = null
        connectionTestResult = null
        connectionTestSuccessful = false
    }

    fun updatePassword(value: String) {
        state = state.copy(password = value)
        connectionTestResult = null
        connectionTestSuccessful = false
    }

    fun updateEnableOAuth(value: Boolean) {
        state = state.copy(enableOAuth = value)
        tokenEndpointError = null
        clientIdError = null
        clientSecretError = null
        usernameError = null
        connectionTestResult = null
        connectionTestSuccessful = false
    }

    fun updateClientId(value: String) {
        state = state.copy(clientId = value)
        clientIdError = null
        connectionTestResult = null
        connectionTestSuccessful = false
    }

    fun updateClientSecret(value: String) {
        state = state.copy(clientSecret = value)
        clientSecretError = null
        connectionTestResult = null
        connectionTestSuccessful = false
    }

    fun updateTokenEndpoint(value: String) {
        state = state.copy(tokenEndpoint = value)
        tokenEndpointError = null
        connectionTestResult = null
        connectionTestSuccessful = false
    }

    fun validate(requireName: Boolean = true): Boolean {
        nameError = if (requireName && state.serverName.isBlank()) getApplication<Application>().getString(R.string.ui_enter_a_server_name) else null
        endpointUrlError = serverUrlError(state.endpointUrl)?.let { getApplication<Application>().getString(it) }
        tokenEndpointError = if (state.enableOAuth) serverUrlError(state.tokenEndpoint)?.let { getApplication<Application>().getString(it) } else null
        clientIdError = if (state.enableOAuth && state.clientId.isBlank()) getApplication<Application>().getString(R.string.ui_enter_a_client_id) else null
        clientSecretError = if (state.enableOAuth && state.clientSecret.isBlank()) getApplication<Application>().getString(R.string.ui_enter_a_client_secret) else null
        usernameError = if (!state.enableOAuth && (state.username.contains(':') ||
            (state.username.isBlank() && state.password.isNotEmpty()))) getApplication<Application>().getString(R.string.ui_enter_a_username_without_a_colon) else null
        return listOf(nameError, endpointUrlError, tokenEndpointError, clientIdError, clientSecretError, usernameError).all { it == null }
    }

    fun saveProfile(): Boolean {
        if (!validate()) return false

        val profile = state.copy(
            id = existingProfileId ?: state.id,
            serverName = state.serverName.trim(),
            endpointUrl = requireHttpsUrl(state.endpointUrl, "Server endpoint"),
            username = if (!state.enableOAuth) state.username.trim() else "",
        )

        val pwd = if (!state.enableOAuth) state.password else ""
        repo.setPassword(profile.id, pwd)

        if (state.enableOAuth) {
            repo.setOAuthClientId(profile.id, state.clientId.trim())
            repo.setOAuthClientSecret(profile.id, state.clientSecret)
            repo.setOAuthTokenEndpoint(profile.id, requireHttpsUrl(state.tokenEndpoint, "OAuth token endpoint"))
        }

        repo.save(profile)
        return true
    }

    fun testConnection() {
        if (isTestingConnection) return
        if (!validate(requireName = false)) {
            connectionTestSuccessful = false
            connectionTestResult = getApplication<Application>().getString(R.string.ui_check_the_highlighted_fields_before_testing)
            return
        }
        val snapshot = state
        isTestingConnection = true
        connectionTestResult = null
        connectionTestSuccessful = false
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { ServerConnectionTester { id, args -> getApplication<Application>().getString(id, *args) }.testResult(snapshot) }
                if (state == snapshot) {
                    connectionTestResult = result.message
                    connectionTestSuccessful = result.successful
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                isTestingConnection = false
            }
        }
    }
}
