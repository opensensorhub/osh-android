package org.sensorhub.android.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.sensorhub.android.R
import org.sensorhub.android.SensorHubService
import org.sensorhub.android.config.DiscoveryRulesDownloader
import org.sensorhub.android.config.PreferenceSnapshot
import org.sensorhub.android.config.SensorHubConfigFactory
import org.sensorhub.android.ui.features.sensors.data.SensorBinding
import org.sensorhub.android.ui.features.sensors.data.SensorRegistry
import org.sensorhub.android.ui.features.sensors.data.SensorUiEntry
import org.sensorhub.android.ui.features.nodes.servers.data.ServerProfileRepository
import org.sensorhub.android.ui.features.nodes.ondevice.LocalServiceSettings
import org.sensorhub.android.ui.features.dashboard.SensorCardReader
import org.sensorhub.android.ui.features.dashboard.SensorCardUi
import org.sensorhub.android.ui.features.dashboard.ServerStatusReader
import org.sensorhub.android.ui.features.dashboard.ServerStatusUi
import org.sensorhub.api.module.ModuleEvent.ModuleState
import org.sensorhub.impl.sensor.android.AndroidSensorsConfig
import org.sensorhub.impl.sensor.controller.ControllerDriver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SensorHubViewModel(private val application: Application) : AndroidViewModel(application) {
    private companion object {
        const val TAG = "SensorHubViewModel"
    }
    private val prefs = PreferenceManager.getDefaultSharedPreferences(application)
    private val profiles = ServerProfileRepository.getInstance(application)
    private val sensorCardReader = SensorCardReader(application)
    private val serverStatusReader = ServerStatusReader(application)
    private val _uiState = MutableStateFlow(HubUiState(runName = application.getString(
        R.string.ui_run_1_s, SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    )))
    val state = _uiState.asStateFlow()
    private var activeSensors: List<SensorUiEntry> = emptyList()
    private var pendingRunName: String? = null
    private var foreground = false
    private var service: SensorHubService? = null
    private var bound = false
    private var preparing = false
    private var startWhenConnected = false
    private val refreshMutex = Mutex()
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as SensorHubService.LocalBinder).service
            _uiState.update { it.copy(serviceConnected = true, connecting = false, error = null) }
            if (activeSensors.isEmpty()) {
                val modules = service?.sensorHub?.moduleRegistry?.loadedModules.orEmpty()
                activeSensors = SensorRegistry.entries.filter { sensor ->
                    val module = modules.firstOrNull { it.localID == sensor.runtimeModuleId }
                    when (val runtime = sensor.runtime) {
                        is SensorBinding.Android -> (module?.configuration as? AndroidSensorsConfig)
                            ?.let(runtime.isEnabled) ?: false
                        is SensorBinding.Dedicated -> module != null
                    }
                }
            }
            refresh()
            if (startWhenConnected) {
                startWhenConnected = false
                startHub(pendingRunName.orEmpty())
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            _uiState.update { it.copy(serviceConnected = false, connecting = false) }
            reportError(application.getString(R.string.ui_connection_to_smart_hub_was_lost))
        }
    }

    init {
        viewModelScope.launch {
            while (isActive) {
                if (foreground && _uiState.subscriptionCount.value > 0) {
                    try {
                        refresh()
                    } catch (e: Exception) {
                        _uiState.update { it.copy(error = application.getString(R.string.ui_unable_to_read_current_sensor_status)) }
                    }
                }
                delay(1000)
            }
        }
    }

    fun attach() {
        foreground = true
        connect()
    }

    fun detach() {
        foreground = false
        if (!preparing) disconnect()
    }

    private fun disconnect() {
        startWhenConnected = false
        if (bound) application.unbindService(connection)
        bound = false
        service = null
        _uiState.update { it.copy(serviceConnected = false, connecting = false) }
    }

    fun controller(): ControllerDriver? = try {
        service?.sensorHub?.moduleRegistry?.getModuleByType(ControllerDriver::class.java)
    } catch (_: Exception) { null }

    fun connect() {
        if (service != null) {
            _uiState.update { it.copy(error = null) }
            refresh()
            return
        }
        if (bound) return
        _uiState.update { it.copy(connecting = true, error = null) }
        try {
            val app = application
            bound = app.bindService(Intent(app, SensorHubService::class.java), connection, Context.BIND_AUTO_CREATE)
            if (!bound) reportError(application.getString(R.string.ui_unable_to_bind_to_smart_hub))
        } catch (e: Exception) {
            reportError(e.message ?: application.getString(R.string.ui_unable_to_connect_to_smart_hub))
        }
    }

    fun reportError(message: String) {
        startWhenConnected = false
        _uiState.update { it.copy(error = message, connecting = false) }
    }

    fun setRunName(value: String) { _uiState.update { it.copy(runName = value) } }

    fun startHub(runName: String = state.value.runName) {
        pendingRunName = runName
        val current = service ?: run {
            if (!startWhenConnected) {
                startWhenConnected = true
                connect()
            }
            return
        }
        if (preparing || current.hubState !in listOf(SensorHubService.HubState.STOPPED, SensorHubService.HubState.ERROR)) return
        preparing = true
        _uiState.update { it.copy(hubStatus = ModuleState.STARTING, error = null) }
        val snapshot = PreferenceSnapshot(prefs)
        viewModelScope.launch {
            try {
                Log.i(TAG, "Preparing SensorHub configuration for run '${runName.trim()}'")
                val config = withContext(Dispatchers.IO) {
                    val settings = LocalServiceSettings.read(snapshot)
                    val rules = if (settings.discoveryEnabled)
                        DiscoveryRulesDownloader().download(settings.rulesLink, application.filesDir) else null
                    SensorHubConfigFactory(application, profiles).create(snapshot, runName.trim(), rules?.absolutePath)
                }
                val app = application
                app.startService(Intent(app, SensorHubService::class.java))
                activeSensors = SensorRegistry.enabledSensorEntries(snapshot).toList()
                Log.i(TAG, "Requesting SensorHub start with ${activeSensors.size} enabled sensor(s)")
                current.startSensorHub(
                    config.modules,
                    activeSensors.any { it.id == "gps" || it.id == "network" },
                    config.cameraEnabled,
                    activeSensors.any { it.id == "audio" }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Unable to prepare or request SensorHub startup", e)
                reportError(e.message ?: application.getString(R.string.ui_unable_to_start_smart_hub))
            } finally {
                preparing = false
                refresh()
                if (!foreground) disconnect()
            }
        }
    }

    fun stopHub() {
        if (state.value.hubStatus != ModuleState.STARTED) return
        _uiState.update { it.copy(hubStatus = ModuleState.STOPPING) }
        try {
            service?.stopSensorHub()
        } catch (e: Exception) {
            reportError(e.message ?: application.getString(R.string.ui_unable_to_stop_smart_hub))
            refresh()
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            refreshMutex.withLock {
                val current = service
                val sensors = activeSensors
                val isPreparing = preparing
                val snapshot = withContext(Dispatchers.Default) {
                    val hubState = current?.hubState
                    HubSnapshot(
                        cards = sensorCardReader.read(current, sensors),
                        servers = serverStatusReader.read(current),
                        status = if (isPreparing) ModuleState.STARTING else when (hubState) {
                            SensorHubService.HubState.STOPPED -> ModuleState.STOPPED
                            SensorHubService.HubState.STARTING -> ModuleState.STARTING
                            SensorHubService.HubState.RUNNING -> ModuleState.STARTED
                            SensorHubService.HubState.STOPPING -> ModuleState.STOPPING
                            SensorHubService.HubState.ERROR -> ModuleState.STOPPED
                            else -> ModuleState.LOADED
                        },
                        hasError = hubState == SensorHubService.HubState.ERROR,
                    )
                }
                _uiState.update {
                    it.copy(
                        sensorCards = snapshot.cards,
                        serverStatuses = snapshot.servers,
                        hubStatus = snapshot.status,
                        error = it.error ?: if (snapshot.hasError)
                            application.getString(R.string.ui_unable_to_start_sensor_settings) else null,
                    )
                }
            }
        }
    }

    override fun onCleared() {
        disconnect()
        super.onCleared()
    }
}

private data class HubSnapshot(
    val cards: List<SensorCardUi>,
    val servers: List<ServerStatusUi>,
    val status: ModuleState,
    val hasError: Boolean,
)

data class HubUiState(
    val runName: String = "",
    val hubStatus: ModuleState = ModuleState.LOADED,
    val sensorCards: List<SensorCardUi> = emptyList(),
    val serverStatuses: List<ServerStatusUi> = emptyList(),
    val connecting: Boolean = false,
    val serviceConnected: Boolean = false,
    val error: String? = null,
)
