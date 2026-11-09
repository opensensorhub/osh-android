package org.sensorhub.android.config

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings.Secure
import com.botts.impl.service.discovery.DiscoveryService
import com.botts.impl.service.discovery.DiscoveryServiceConfig
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.Date
import org.sensorhub.android.InsecureOkHttpClientWrapper
import org.sensorhub.android.InsecureSOSTClient
import org.sensorhub.android.OkHttpClientWrapper
import org.sensorhub.android.ui.features.sensors.data.SensorRegistry
import org.sensorhub.android.ui.features.sensors.data.SensorRuntimeConfiguration
import org.sensorhub.android.ui.features.nodes.servers.data.ServerProfileItem
import org.sensorhub.android.ui.features.nodes.servers.data.ServerProfileRepository
import org.sensorhub.android.ui.features.nodes.servers.data.serverUrlError
import org.sensorhub.api.module.IModuleConfigRepository
import org.sensorhub.api.sensor.SensorConfig
import org.sensorhub.impl.client.sost.SOSTClientConfig
import org.sensorhub.impl.datastore.h2.MVObsSystemDatabaseConfig
import org.sensorhub.impl.datastore.view.ObsSystemDatabaseViewConfig
import org.sensorhub.impl.module.InMemoryConfigDb
import org.sensorhub.impl.module.ModuleClassFinder
import org.sensorhub.impl.service.HttpServerConfig
import org.sensorhub.impl.service.consys.ConSysApiService
import org.sensorhub.impl.service.consys.ConSysApiServiceConfig
import org.sensorhub.impl.service.consys.client.ConSysApiClientConfig
import org.sensorhub.impl.service.consys.client.ConSysOAuthConfig
import org.sensorhub.impl.service.sos.SOSService
import org.sensorhub.impl.service.sos.SOSServiceConfig
import org.slf4j.LoggerFactory
import java.net.URI
import org.sensorhub.android.ui.features.nodes.ondevice.LocalServiceSettings
class SensorHubConfigFactory(
    context: Context,
    private val serverRepository: ServerProfileRepository,
    private val sensorsLastUpdated: Date = Date()
) {
    private val context = context.applicationContext
    private val log = LoggerFactory.getLogger(SensorHubConfigFactory::class.java)

    fun create(prefs: SharedPreferences, runName: String?, discoveryRulesPath: String? = null): SensorHubConfiguration {
        val config = InMemoryConfigDb(ModuleClassFinder())


        val services = LocalServiceSettings.read(prefs)

        val serverRepo = serverRepository
        val enabledServers: MutableList<ServerProfileItem> = serverRepo.getEnabled()
        //---------- SENSORS ---------------------
        val deviceID = Secure.getString(context.contentResolver, Secure.ANDROID_ID)
        var deviceName = prefs.getString("device_name", null)
        if (deviceName == null || deviceName.length < 2) deviceName = deviceID


        // HTTP Server
        val serverConfig = HttpServerConfig()
        serverConfig.proxyBaseUrl = ""
        serverConfig.httpPort = 8585
        serverConfig.autoStart = true
        config.add(serverConfig)

        val sensorsConfig = SensorRuntimeConfiguration.create(
            context, prefs, config, deviceID, deviceName, runName, sensorsLastUpdated
        )

        if (SensorRegistry.hasEnabledSensor(prefs)) {
            for (sp in enabledServers) {
                if (serverUrlError(sp.endpointUrl) != null ||
                    (sp.enableOAuth && serverUrlError(serverRepo.getOAuthTokenEndpoint(sp.id)) != null)) {
                    log.error("Skipping server profile '{}': remote endpoints must use HTTPS", sp.serverName)
                    continue
                }
                val profileUrl = try {
                    URI(sp.endpointUrl.trim()).toURL()
                } catch (_: Exception) {
                    null
                }
                if (profileUrl == null) {
                    log.error(
                        "Skipping server profile '{}': invalid URL '{}'",
                        sp.serverName, sp.endpointUrl
                    )
                    continue
                }

                val pwd = serverRepo.getPassword(sp.id)

                if (sp.useConSysClient) {
                    val oAuthConfig = ConSysOAuthConfig()
                    oAuthConfig.oAuthEnabled = sp.enableOAuth
                    oAuthConfig.tokenEndpoint = serverRepo.getOAuthTokenEndpoint(sp.id)
                    oAuthConfig.clientID = serverRepo.getOAuthClientId(sp.id)
                    oAuthConfig.clientSecret = serverRepo.getOAuthClientSecret(sp.id)
                    addCSApiConfig(
                        config,
                        sensorsConfig,
                        sp,
                        profileUrl,
                        sp.username,
                        pwd,
                        oAuthConfig
                    )
                } else {
                    addSosTConfig(config, sensorsConfig, sp, profileUrl, sp.username, pwd)
                }
            }
        }

        if (shouldStore(prefs)) {
            val dbFile = File(context.filesDir.toString() + "/db/")
            dbFile.mkdirs()
            val basicStorageConfig = MVObsSystemDatabaseConfig()
            basicStorageConfig.storagePath = dbFile.absolutePath + "/\${STORAGE_ID}.dat"
            basicStorageConfig.autoStart = true
            config.add(basicStorageConfig)
        }

        //---------- SERVICES ---------------------
        if (services.csApiEnabled) {
            val conSysApiService = ConSysApiServiceConfig()
            conSysApiService.moduleClass = ConSysApiService::class.java.canonicalName
            conSysApiService.id = "CON_SYS_SERVICE"
            conSysApiService.name = "Connected Systems API Service"
            conSysApiService.autoStart = true
            conSysApiService.enableTransactional = true
            conSysApiService.exposedResources = ObsSystemDatabaseViewConfig()

            config.add(conSysApiService)
        }

        if (services.sosEnabled) {
            val sosConfig = SOSServiceConfig()
            sosConfig.moduleClass = SOSService::class.java.canonicalName
            sosConfig.id = "SOS_SERVICE"
            sosConfig.name = "SOS Service"
            sosConfig.autoStart = true
            sosConfig.enableTransactional = true
            sosConfig.exposedResources = ObsSystemDatabaseViewConfig()

            config.add(sosConfig)
        }

        if (services.discoveryEnabled) {
            val discoveryServiceConfig = DiscoveryServiceConfig()
            discoveryServiceConfig.moduleClass = DiscoveryService::class.java.canonicalName
            discoveryServiceConfig.id = "DISCOVERY_SERVICE"
            discoveryServiceConfig.name = "Discovery Service"
            discoveryServiceConfig.autoStart = true

            discoveryServiceConfig.rulesFilePath = requireNotNull(discoveryRulesPath) {
                "Discovery rules must be downloaded before starting the hub"
            }

            config.add(discoveryServiceConfig)
        }

        return SensorHubConfiguration(config, deviceID, sensorsConfig.enableCamera)
    }

    private fun addSosTConfig(
        config: IModuleConfigRepository,
        sensorConf: SensorConfig,
        profile: ServerProfileItem,
        serverUrl: URL,
        user: String,
        pwd: String
    ) {
        val sosConfig = SOSTClientConfig()
        if (profile.disableSSL) {
            sosConfig.moduleClass = InsecureSOSTClient::class.java.canonicalName
        }
        sosConfig.id = sensorConf.id + "_SOST_" + profile.id
        sosConfig.name =
            sensorConf.name.replace("\\[.*\\]".toRegex(), "") + " -> " + profile.serverName
        sosConfig.autoStart = true
        sosConfig.sos.remoteHost = serverUrl.host
        sosConfig.sos.remotePort = if (serverUrl.port < 0) serverUrl.defaultPort else serverUrl.port
        sosConfig.sos.resourcePath = serverUrl.path
        sosConfig.sos.enableTLS = serverUrl.protocol == "https"
        sosConfig.sos.user = user
        sosConfig.sos.password = pwd
        sosConfig.connection.connectTimeout = 10000
        sosConfig.connection.usePersistentConnection = true
        sosConfig.connection.reconnectAttempts = 9
        sosConfig.connection.maxQueueSize = 100
        sosConfig.dataSourceSelector = ObsSystemDatabaseViewConfig()
        config.add(sosConfig)
    }

    private fun addCSApiConfig(
        config: IModuleConfigRepository,
        sensorConf: SensorConfig,
        profile: ServerProfileItem,
        serverUrl: URL,
        apiUser: String,
        apiPwd: String,
        oAuthConfig: ConSysOAuthConfig
    ) {
        val consysConfig = ConSysApiClientConfig()
        consysConfig.id = sensorConf.id + "_CONSYS_" + profile.id
        consysConfig.name =
            sensorConf.name.replace("\\[.*\\]".toRegex(), "") + " -> " + profile.serverName
        consysConfig.autoStart = true
        consysConfig.conSys.remoteHost = serverUrl.host
        consysConfig.conSys.remotePort =
            if (serverUrl.port < 0) serverUrl.defaultPort else serverUrl.port
        consysConfig.conSys.resourcePath = serverUrl.path
        consysConfig.conSys.enableTLS = serverUrl.protocol == "https"
        consysConfig.conSys.user = apiUser
        consysConfig.conSys.password = apiPwd
        consysConfig.connection.connectTimeout = 10000
        consysConfig.connection.reconnectAttempts = 9
        consysConfig.httpClientImplClass = if (profile.disableSSL) {
            InsecureOkHttpClientWrapper::class.java.canonicalName
        } else {
            OkHttpClientWrapper::class.java.canonicalName
        }
        consysConfig.dataSourceSelector = ObsSystemDatabaseViewConfig()
        consysConfig.conSysOAuth = oAuthConfig
        config.add(consysConfig)
    }

    private fun shouldStore(prefs: SharedPreferences): Boolean {
        val prefMap = prefs.all
        for ((_, value) in prefMap) {
            if (value is Set<*>) {
                if (value.contains("STORE_LOCAL")) {
                    return true
                }
            }
        }
        return false
    }

}

data class SensorHubConfiguration(
    val modules: IModuleConfigRepository,
    val deviceId: String,
    val cameraEnabled: Boolean,
)

internal class DiscoveryRulesDownloader {
    fun download(link: String, directory: File): File {
        val url = URL(link)
        require(url.protocol.equals("https", ignoreCase = true)) { "Discovery rules URL must use HTTPS" }
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = false
        val destination = File(directory, "rules.txt")
        val temporary = File.createTempFile("rules-", ".download", directory)
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("Discovery rules download failed: HTTP ${connection.responseCode}")
            }
            if (connection.contentLengthLong > MAX_BYTES) throw IOException("Discovery rules file is too large")
            connection.inputStream.use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_BYTES) throw IOException("Discovery rules file is too large")
                        output.write(buffer, 0, count)
                    }
                }
            }
            if (!temporary.renameTo(destination)) throw IOException("Unable to install discovery rules file")
            return destination
        } finally {
            temporary.delete()
            connection.disconnect()
        }
    }

    private companion object { const val MAX_BYTES = 5L * 1024 * 1024 }
}

internal class PreferenceSnapshot(prefs: SharedPreferences) : SharedPreferences {
    private val values = prefs.all.mapValues { (_, value) ->
        if (value is Set<*>) Collections.unmodifiableSet(HashSet(value)) else value
    }

    override fun getAll(): Map<String, *> = values.toMap()
    override fun contains(key: String?) = values.containsKey(key)
    override fun getString(key: String?, defValue: String?): String? = values[key] as String? ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (values[key] as Set<String>?)?.toMutableSet() ?: defValues?.toMutableSet()
    override fun getInt(key: String?, defValue: Int) = values[key] as Int? ?: defValue
    override fun getLong(key: String?, defValue: Long) = values[key] as Long? ?: defValue
    override fun getFloat(key: String?, defValue: Float) = values[key] as Float? ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean) = values[key] as Boolean? ?: defValue
    override fun edit(): SharedPreferences.Editor = error("Run preferences are read-only")
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
}
