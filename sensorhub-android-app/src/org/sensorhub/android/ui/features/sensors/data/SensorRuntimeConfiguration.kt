package org.sensorhub.android.ui.features.sensors.data

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Camera
import android.location.LocationManager
import org.sensorhub.android.comm.BluetoothCommProvider
import org.sensorhub.android.comm.BluetoothCommProviderConfig
import org.sensorhub.android.comm.ble.BleConfig
import org.sensorhub.android.comm.ble.BleNetwork
import org.sensorhub.impl.sensor.android.AndroidSensorsConfig
import org.sensorhub.impl.sensor.android.video.VideoEncoderConfig.VideoPreset
import org.sensorhub.impl.sensor.controller.ControllerConfig
import org.sensorhub.impl.sensor.kestrel.KestrelConfig
import org.sensorhub.impl.sensor.meshtastic.MeshtasticConfig
import org.sensorhub.impl.sensor.polar.PolarConfig
import org.sensorhub.impl.sensor.ste.STERadPagerConfig
import org.sensorhub.impl.sensor.template.TemplateConfig
import org.sensorhub.impl.sensor.trupulse.SimulatedDataStream
import org.sensorhub.impl.sensor.trupulse.TruPulseConfig
import org.sensorhub.impl.sensor.trupulse.TruPulseWithGeolocConfig
import org.sensorhub.impl.sensor.wardriving.WardrivingConfig
import java.util.Date
import org.sensorhub.api.module.IModuleConfigRepository
import org.sensorhub.api.sensor.SensorConfig
import org.sensorhub.api.data.IStreamingDataInterface
import org.sensorhub.impl.driver.flir.FlirOneCameraConfig
import org.sensorhub.impl.sensor.angel.AngelSensorConfig

sealed class SensorBinding(val moduleId: String) {
    class Android(
        val configure: (AndroidSensorsConfig, Boolean) -> Unit,
        val isEnabled: (AndroidSensorsConfig) -> Boolean,
        override val matchesOutput: (IStreamingDataInterface) -> Boolean,
    ) : SensorBinding(SensorRegistry.ANDROID_MODULE_ID)

    class Dedicated(
        moduleId: String,
        val moduleName: String,
        val create: (SensorBuildContext, SensorUiEntry) -> SensorConfig,
    ) : SensorBinding(moduleId) {
        override val matchesOutput: (IStreamingDataInterface) -> Boolean = { true }
    }

    abstract val matchesOutput: (IStreamingDataInterface) -> Boolean
}

data class SensorBuildContext(
    val context: Context,
    val prefs: SharedPreferences,
    val modules: IModuleConfigRepository,
    val androidSensors: AndroidSensorsConfig,
    val deviceId: String,
)

internal object SensorDriverConfigs {
    fun trupulse(build: SensorBuildContext, sensor: SensorUiEntry): SensorConfig {
        var trupulseConfig = TruPulseConfig()

        if (build.androidSensors.activateGpsLocation) {
            var gpsOutputName: String? = null
            if (build.context.packageManager
                    .hasSystemFeature(PackageManager.FEATURE_LOCATION)
            ) {
                val locationManager =
                    build.context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val locProviders = locationManager.allProviders
                for (provName in locProviders) {
                    val locProvider = locationManager.getProvider(provName)
                    if (locProvider!!.requiresSatellite()) gpsOutputName =
                        locProvider.name.replace(" ".toRegex(), "_") + "_data"
                }
            }

            trupulseConfig = TruPulseWithGeolocConfig()
            trupulseConfig.locationSourceUID =
                "urn:osh:android" + build.androidSensors.getAndroidSensorsUidWithExt()
            trupulseConfig.locationOutputName = gpsOutputName
        }

        trupulseConfig.serialNumber = build.deviceId

        val btConf = BluetoothCommProviderConfig()
        btConf.protocol.deviceName = sensor.bluetoothAddress(build.prefs)
        if (SensorSettings.truPulseSource.read(build.prefs) == "SIMULATED") {
            btConf.moduleClass = SimulatedDataStream::class.java.canonicalName
        } else {
            btConf.moduleClass = BluetoothCommProvider::class.java.canonicalName
            trupulseConfig.connection.connectTimeout = 100000
            trupulseConfig.connection.reconnectAttempts = 10
        }
        trupulseConfig.commSettings = btConf

        return trupulseConfig
    }

    fun ste(build: SensorBuildContext, sensor: SensorUiEntry): SensorConfig {
        val steRadPagerConfig = STERadPagerConfig()
        steRadPagerConfig.autoStart = true

        return steRadPagerConfig
    }

    fun angel(build: SensorBuildContext, sensor: SensorUiEntry): SensorConfig {
        val bleConf = BleConfig()
        bleConf.id = "BLE"
        bleConf.moduleClass = BleNetwork::class.java.canonicalName
        bleConf.androidContext = build.context
        bleConf.autoStart = true
        build.modules.add(bleConf)

        val angelConfig = AngelSensorConfig()
        angelConfig.networkID = bleConf.id
        angelConfig.btAddress = sensor.bluetoothAddress(build.prefs)

        return angelConfig
    }
    fun meshtastic(build: SensorBuildContext, sensor: SensorUiEntry): SensorConfig {
        val meshtasticConfig = MeshtasticConfig()
        meshtasticConfig.device_name = sensor.bluetoothAddress(build.prefs)
        meshtasticConfig.uid_extension = build.prefs.getString("uid_extension", "")

        return meshtasticConfig
    }

    fun polar(build: SensorBuildContext, sensor: SensorUiEntry): SensorConfig {
        val polarConfig = PolarConfig()
        polarConfig.deviceId = sensor.bluetoothAddress(build.prefs)
        polarConfig.uid_extension = build.prefs.getString("uid_extension", "")

        return polarConfig
    }

    fun kestrel(build: SensorBuildContext, sensor: SensorUiEntry): SensorConfig {
        val bleConf = BleConfig()
        bleConf.id = "BLE_NETWORK"
        bleConf.moduleClass = BleNetwork::class.java.canonicalName
        bleConf.androidContext = build.context
        bleConf.autoStart = true
        build.modules.add(bleConf)

        val kestrelConfig = KestrelConfig()
        kestrelConfig.networkID = bleConf.id
        kestrelConfig.deviceAddress = sensor.bluetoothAddress(build.prefs)

        return kestrelConfig
    }

    fun controller(build: SensorBuildContext, sensor: SensorUiEntry): SensorConfig {
        val controllerConfig = ControllerConfig()
        controllerConfig.uid_extension = build.prefs.getString("uid_extension", "")

        return controllerConfig
    }

    fun flir(build: SensorBuildContext, sensor: SensorUiEntry): SensorConfig {
        val flirOneCameraConfig = FlirOneCameraConfig()
        flirOneCameraConfig.autoStart = true
        flirOneCameraConfig.androidContext = build.context

        return flirOneCameraConfig
    }
    fun wardriving(build: SensorBuildContext, sensor: SensorUiEntry): SensorConfig {
        val wardrivingConfig = WardrivingConfig()
        wardrivingConfig.uid_extension = build.prefs.getString("uid_extension", "")

        return wardrivingConfig
    }

    fun template(build: SensorBuildContext, sensor: SensorUiEntry): SensorConfig {
        val templateConfig = TemplateConfig()
        templateConfig.uid_extension = build.prefs.getString("uid_extension", "")

        return templateConfig
    }
}

object SensorRuntimeConfiguration {
    fun create(
        context: Context,
        prefs: SharedPreferences,
        modules: IModuleConfigRepository,
        deviceID: String,
        deviceName: String?,
        runName: String?,
        sensorsLastUpdated: Date,
    ): AndroidSensorsConfig {
        val sensorsConfig = AndroidSensorsConfig()
        sensorsConfig.name = "Android Sensors [" + deviceName + "]"
        sensorsConfig.id = SensorRegistry.ANDROID_MODULE_ID
        sensorsConfig.autoStart = true
        sensorsConfig.lastUpdated = sensorsLastUpdated
        SensorRegistry.entries.forEach { sensor ->
            (sensor.runtime as? SensorBinding.Android)?.configure?.invoke(sensorsConfig, sensor.isEnabled(prefs))
        }

        sensorsConfig.selectedCameraId = SensorSettings.camera.read(prefs).toInt()
        val cameraCount = try { Camera.getNumberOfCameras() } catch (_: Exception) { 0 }
        if (cameraCount > 0) sensorsConfig.selectedCameraId =
            sensorsConfig.selectedCameraId.coerceAtMost(cameraCount - 1)

        // video settings
        sensorsConfig.videoConfig.codec =
            SensorSettings.videoCodec.read(prefs)
        sensorsConfig.videoConfig.frameRate = SensorSettings.videoFrameRate.read(prefs).toInt()

        val resolutionStr = SensorSettings.videoResolution.read(prefs)
        val (width, height) = resolutionStr.split("x").map { it.toInt() }
        val videoPreset = VideoPreset()
        videoPreset.width = width
        videoPreset.height = height
        sensorsConfig.videoConfig.presets = arrayOf(videoPreset)
        sensorsConfig.videoConfig.selectedPreset = 0

        // audio
        sensorsConfig.audioConfig.codec =
            SensorSettings.audioCodec.read(prefs)
        sensorsConfig.audioConfig.sampleRate = SensorSettings.audioSampleRate.read(prefs).toInt()
        sensorsConfig.audioConfig.bitRate = SensorSettings.audioBitRate.read(prefs).toInt()

        sensorsConfig.runName = runName
        sensorsConfig.uidExtension = prefs.getString("uid_extension", "0")

        modules.add(sensorsConfig)
        val build = SensorBuildContext(context, prefs, modules, sensorsConfig, deviceID)
        SensorRegistry.enabledSensorEntries(prefs).forEach { sensor ->
            val binding = sensor.runtime as? SensorBinding.Dedicated ?: return@forEach
            val driver = binding.create(build, sensor)
            driver.id = binding.moduleId
            driver.name = "${binding.moduleName} [$deviceName]"
            driver.autoStart = true
            driver.lastUpdated = sensorsLastUpdated
            modules.add(driver)
        }
        return sensorsConfig
    }
}
