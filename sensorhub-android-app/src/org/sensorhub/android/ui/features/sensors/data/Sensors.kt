package org.sensorhub.android.ui.features.sensors.data

import android.Manifest
import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.StringRes
import org.sensorhub.android.R
import org.sensorhub.impl.sensor.android.*
import org.sensorhub.impl.sensor.android.audio.AndroidAudioOutput
import org.sensorhub.impl.sensor.android.video.AndroidCameraOutput

enum class SensorCategory(@StringRes val labelRes: Int) {
    ON_DEVICE(R.string.category_on_device),
    BLUETOOTH(R.string.category_bluetooth),
    USB(R.string.category_usb),
    OTHERS(R.string.category_others),
}

sealed class SensorUIOption {
    data class BluetoothDevice(
        @StringRes val selectLabelRes: Int,
        val addressPrefKey: String,
    ) : SensorUIOption()

    data object Video : SensorUIOption()
    data object Audio : SensorUIOption()
    data object TruPulse : SensorUIOption() {
        const val addressPrefKey = "trupulse_device_address"
    }
}

data class SensorUiEntry(
    val id: String,
    val prefKey: String,
    @StringRes val nameRes: Int,
    val category: SensorCategory,
    val runtime: SensorBinding,
    val permissions: Set<SensorPermission> = emptySet(),
    val config: SensorUIOption? = null,
) {
    val runtimeModuleId: String get() = runtime.moduleId
    val bluetoothAddressKey: String? get() = when (val options = config) {
        is SensorUIOption.BluetoothDevice -> options.addressPrefKey
        SensorUIOption.TruPulse -> SensorUIOption.TruPulse.addressPrefKey
        else -> null
    }
    fun isEnabled(prefs: SharedPreferences): Boolean = prefs.getBoolean(prefKey, false)
    fun bluetoothAddress(prefs: SharedPreferences): String =
        bluetoothAddressKey?.let { prefs.getString(it, "") }.orEmpty()
}

enum class SensorPermission {
    FINE_LOCATION, COARSE_LOCATION, CAMERA, MICROPHONE, BLUETOOTH, NEARBY_WIFI;

    fun forSdk(sdkInt: Int): List<String> = when (this) {
        FINE_LOCATION -> listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        COARSE_LOCATION -> listOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        CAMERA -> listOf(Manifest.permission.CAMERA)
        MICROPHONE -> listOf(Manifest.permission.RECORD_AUDIO)
        BLUETOOTH -> if (sdkInt >= Build.VERSION_CODES.S)
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else FINE_LOCATION.forSdk(sdkInt)
        NEARBY_WIFI -> if (sdkInt >= Build.VERSION_CODES.TIRAMISU)
            listOf(Manifest.permission.NEARBY_WIFI_DEVICES) else emptyList()
    }
}

object SensorRegistry {
    const val ANDROID_MODULE_ID = "ANDROID_SENSORS"

    val entries: List<SensorUiEntry> = listOf(
        // On-device sensors
        SensorUiEntry(
            "accelerometer",
            "accel_enabled",
            R.string.sensor_accelerometer,
            SensorCategory.ON_DEVICE,
            runtime = SensorBinding.Android(
                configure = { config, enabled -> config.activateAccelerometer = enabled },
                isEnabled = { it.activateAccelerometer },
                matchesOutput = { it is AndroidAcceleroOutput },
            ),
        ),
        SensorUiEntry(
            "gyroscope",
            "gyro_enabled",
            R.string.sensor_gyroscope,
            SensorCategory.ON_DEVICE,
            runtime = SensorBinding.Android(
                configure = { config, enabled -> config.activateGyrometer = enabled },
                isEnabled = { it.activateGyrometer },
                matchesOutput = { it is AndroidGyroOutput },
            ),
        ),
        SensorUiEntry(
            "magnetometer",
            "mag_enabled",
            R.string.sensor_magnetometer,
            SensorCategory.ON_DEVICE,
            runtime = SensorBinding.Android(
                configure = { config, enabled -> config.activateMagnetometer = enabled },
                isEnabled = { it.activateMagnetometer },
                matchesOutput = { it is AndroidMagnetoOutput },
            ),
        ),
        SensorUiEntry(
            "orient_q",
            "orient_quat_enabled",
            R.string.sensor_orientation_quat,
            SensorCategory.ON_DEVICE,
            runtime = SensorBinding.Android(
                configure = { config, enabled -> config.activateOrientationQuat = enabled },
                isEnabled = { it.activateOrientationQuat },
                matchesOutput = { it is AndroidOrientationQuatOutput },
            ),
        ),
        SensorUiEntry(
            "orient_e",
            "orient_euler_enabled",
            R.string.sensor_orientation_euler,
            SensorCategory.ON_DEVICE,
            runtime = SensorBinding.Android(
                configure = { config, enabled -> config.activateOrientationEuler = enabled },
                isEnabled = { it.activateOrientationEuler },
                matchesOutput = { it is AndroidOrientationEulerOutput },
            ),
        ),
        SensorUiEntry(
            "gps",
            "gps_enabled",
            R.string.sensor_gps,
            SensorCategory.ON_DEVICE,
            runtime = SensorBinding.Android(
                configure = { config, enabled -> config.activateGpsLocation = enabled },
                isEnabled = { it.activateGpsLocation },
                matchesOutput = { it is AndroidLocationOutput && it.name == "gps_data" },
            ),
            permissions = setOf(SensorPermission.FINE_LOCATION),
        ),
        SensorUiEntry(
            "network",
            "netloc_enabled",
            R.string.sensor_network_location,
            SensorCategory.ON_DEVICE,
            runtime = SensorBinding.Android(
                configure = { config, enabled -> config.activateNetworkLocation = enabled },
                isEnabled = { it.activateNetworkLocation },
                matchesOutput = { it is AndroidLocationOutput && it.name == "network_data" },
            ),
            permissions = setOf(SensorPermission.COARSE_LOCATION),
        ),
        SensorUiEntry(
            "camera",
            "cam_enabled",
            R.string.sensor_video,
            SensorCategory.ON_DEVICE,
            runtime = SensorBinding.Android(
                configure = { config, enabled -> config.enableCamera = enabled },
                isEnabled = { it.enableCamera },
                matchesOutput = { it is AndroidCameraOutput || it is AndroidCamera2Output },
            ),
            permissions = setOf(SensorPermission.CAMERA),
            config = SensorUIOption.Video
        ),
        SensorUiEntry(
            "audio",
            "audio_enabled",
            R.string.sensor_audio,
            SensorCategory.ON_DEVICE,
            runtime = SensorBinding.Android(
                configure = { config, enabled -> config.activateMicAudio = enabled },
                isEnabled = { it.activateMicAudio },
                matchesOutput = { it is AndroidAudioOutput },
            ),
            permissions = setOf(SensorPermission.MICROPHONE),
            config = SensorUIOption.Audio
        ),

        // Bluetooth sensors
        SensorUiEntry(
            "meshtastic",
            "meshtastic_enabled",
            R.string.sensor_meshtastic,
            SensorCategory.BLUETOOTH,
            runtime = SensorBinding.Dedicated("MESHTASTIC_SENSOR", "Meshtastic", SensorDriverConfigs::meshtastic),
            permissions = setOf(SensorPermission.BLUETOOTH),
            config = SensorUIOption.BluetoothDevice(R.string.title_select_meshtastic, "meshtastic_device_address")
        ),
        SensorUiEntry(
            "polar",
            "polar_enabled",
            R.string.sensor_polar,
            SensorCategory.BLUETOOTH,
            runtime = SensorBinding.Dedicated("POLAR_HEART_SENSOR", "Polar Heart", SensorDriverConfigs::polar),
            permissions = setOf(SensorPermission.BLUETOOTH),
            config = SensorUIOption.BluetoothDevice(R.string.title_select_polar, "polar_device_address")
        ),
        SensorUiEntry(
            "kestrel",
            "kestrel_enabled",
            R.string.sensor_kestrel,
            SensorCategory.BLUETOOTH,
            runtime = SensorBinding.Dedicated("KESTREL_WEATHER", "Kestrel Weather", SensorDriverConfigs::kestrel),
            permissions = setOf(SensorPermission.BLUETOOTH),
            config = SensorUIOption.BluetoothDevice(R.string.title_select_kestrel, "kestrel_device_address")
        ),
        SensorUiEntry(
            "ste",
            "ste_radpager_enabled",
            R.string.sensor_ste,
            SensorCategory.BLUETOOTH,
            runtime = SensorBinding.Dedicated("STE_RADPAGER_SENSOR", "STE Rad Pager", SensorDriverConfigs::ste),
            permissions = setOf(SensorPermission.BLUETOOTH),
            config = SensorUIOption.BluetoothDevice(R.string.title_select_device, "ste_device_address")
        ),
        SensorUiEntry(
            "trupulse",
            "trupulse_enabled",
            R.string.sensor_trupulse,
            SensorCategory.BLUETOOTH,
            runtime = SensorBinding.Dedicated("TRUPULSE_SENSOR", "TruPulse Range Finder", SensorDriverConfigs::trupulse),
            permissions = setOf(SensorPermission.BLUETOOTH),
            config = SensorUIOption.TruPulse
        ),

        SensorUiEntry(
            "angel",
            "angel_enabled",
            R.string.sensor_angel,
            SensorCategory.BLUETOOTH,
            runtime = SensorBinding.Dedicated("ANGEL_SENSOR", "Angel Wearable", SensorDriverConfigs::angel),
            permissions = setOf(SensorPermission.BLUETOOTH),
            config = SensorUIOption.BluetoothDevice(R.string.title_select_kestrel, "angel_device_address")
        ),

        // USB sensors
        SensorUiEntry(
            "controller",
            "controller_enabled",
            R.string.sensor_controller,
            SensorCategory.USB,
            runtime = SensorBinding.Dedicated("CONTROLLER", "Controller", SensorDriverConfigs::controller)
        ),
        SensorUiEntry(
            "flirone",
            "flirone_enabled",
            R.string.sensor_flirone,
            SensorCategory.USB,
            runtime = SensorBinding.Dedicated("FLIRONE_SENSOR", "FLIR One Camera", SensorDriverConfigs::flir)
        ),

        // Others
        SensorUiEntry(
            "wardriving",
            "wardriving_enabled",
            R.string.sensor_wardriving,
            SensorCategory.OTHERS,
            runtime = SensorBinding.Dedicated("WARDRIVING_", "Wardriving", SensorDriverConfigs::wardriving),
            permissions = setOf(SensorPermission.FINE_LOCATION, SensorPermission.NEARBY_WIFI)
        ),
        SensorUiEntry(
            "template",
            "template_enabled",
            R.string.sensor_template,
            SensorCategory.OTHERS,
            runtime = SensorBinding.Dedicated("TEMPLATE_DRIVER_", "Template", SensorDriverConfigs::template),
            permissions = setOf(SensorPermission.BLUETOOTH),
            config = SensorUIOption.BluetoothDevice(R.string.title_select_device, "template_device_address")
        )
    )


    val bluetoothAddressPreferenceKeys: List<String>
        get() = entries.mapNotNull { it.bluetoothAddressKey }.distinct()

    fun enabledSensorEntries(prefs: SharedPreferences): List<SensorUiEntry> =
        entries.filter { it.isEnabled(prefs) }

    fun hasEnabledSensor(prefs: SharedPreferences): Boolean = entries.any { it.isEnabled(prefs) }

    fun runtimeModuleId(sensorId: String): String = entries.first { it.id == sensorId }.runtimeModuleId

    fun isEnabled(prefs: SharedPreferences, sensorId: String): Boolean =
        entries.firstOrNull { it.id == sensorId }?.isEnabled(prefs) ?: false

    fun requiredPermissions(prefs: SharedPreferences, sdkInt: Int): Array<String> =
        enabledSensorEntries(prefs).flatMap { it.permissions }.distinct()
            .flatMap { it.forSdk(sdkInt) }.distinct().toTypedArray()
}
