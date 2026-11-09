package org.sensorhub.android.ui.features.sensors.data

import android.content.SharedPreferences

data class SensorChoice(
    val key: String,
    val values: List<String>,
    val defaultValue: String = values.first(),
    val legacyDefault: ((SharedPreferences) -> String)? = null,
) {
    init { require(defaultValue in values) }

    fun read(prefs: SharedPreferences): String {
        val fallback = legacyDefault?.invoke(prefs) ?: defaultValue
        return prefs.getString(key, fallback)?.takeIf { it in values } ?: fallback
    }
}

object SensorSettings {
    val audioCodec = SensorChoice("audio_codec", listOf("AAC", "OPUS"))
    val audioSampleRate = SensorChoice("audio_samplerate", listOf("8000", "11025", "22050", "44100", "48000"))
    val audioBitRate = SensorChoice("audio_bitrate", listOf("32", "64", "96", "128", "160", "192"), "64")
    val videoCodec = SensorChoice("video_codec", listOf("JPEG", "H264", "H265", "VP9", "VP8"))
    val videoFrameRate = SensorChoice("video_framerate", listOf("10", "15", "24", "30", "60", "120"), "30")
    val videoResolution = SensorChoice("video_resolution", listOf("320x240", "640x480", "1280x720", "1920x1080"), "640x480")
    val camera = SensorChoice("camera_select", listOf("0", "1"))
    val truPulseSource = SensorChoice("trupulse_datasource", listOf("STREAM", "SIMULATED"), legacyDefault = {
        if (it.getBoolean("trupulse_simu", false)) "SIMULATED" else "STREAM"
    })
}
