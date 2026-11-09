package org.sensorhub.android.ui.features.dashboard

import android.content.Context
import androidx.annotation.StringRes
import org.sensorhub.android.R
import org.sensorhub.android.SensorHubService
import org.sensorhub.impl.client.sost.SOSTClient
import org.sensorhub.impl.service.consys.client.ConSysApiClientModule
import java.util.regex.Pattern

class ServerStatusReader(private val context: Context) {

    fun read(service: SensorHubService?): List<ServerStatusUi> {
        val hub = service?.sensorHub ?: return emptyList()
        if (service.hubState != SensorHubService.HubState.RUNNING) return emptyList()

        val modules = try {
            hub.moduleRegistry.loadedModules.toList()
        } catch (_: Exception) {
            return emptyList()
        }

        val now = System.currentTimeMillis()
        val results = mutableListOf<ServerStatusUi>()

        for (module in modules) {
            when (module) {
                is SOSTClient -> results.add(readSostClient(module, now))
                is ConSysApiClientModule -> results.add(readConSysClient(module, now))
            }
        }

        return results
    }

    private fun readSostClient(client: SOSTClient, now: Long): ServerStatusUi {
        val clientId = client.localID
        val serverName = extractServerName(client.name, context.getString(R.string.dashboard_sost))
        val dataStreams = client.dataStreams
        val moduleState = client.currentState?.name ?: "UNKNOWN"

        var errorText: String? = null
        val statusMsg = client.statusMessage

        client.currentError?.let { error ->
            var msg = error.message?.trim() ?: "Unknown error"
            if (!msg.endsWith(".")) msg += "."
            error.cause?.message?.let { msg += " $it" }
            errorText = msg
        }

        val sensorGroups = buildSensorGroups(dataStreams, now)
        val allOk = errorText == null && sensorGroups.isNotEmpty() && sensorGroups.all { it.allOk }

        return ServerStatusUi(
            clientId = clientId,
            serverName = serverName,
            clientMode = context.getString(R.string.dashboard_sost),
            allOk = allOk,
            moduleState = moduleState,
            errorText = errorText,
            statusMsg = statusMsg,
            sensorGroups = sensorGroups
        )
    }

    private fun readConSysClient(client: ConSysApiClientModule, now: Long): ServerStatusUi {
        val clientId = client.localID
        val serverName = extractServerName(client.name, context.getString(R.string.dashboard_consys))
        val dataStreams = client.dataStreams
        val moduleState = client.currentState?.name ?: "UNKNOWN"

        var errorText: String? = null
        val statusMsg = client.statusMessage

        client.currentError?.let { error ->
            var msg = error.message?.trim() ?: "Unknown error"
            if (!msg.endsWith(".")) msg += "."
            error.cause?.message?.let { msg += " $it" }
            errorText = msg
        }

        val sensorGroups = buildConSysGroups(dataStreams, now)
        val allOk = errorText == null && sensorGroups.isNotEmpty() && sensorGroups.all { it.allOk }

        return ServerStatusUi(
            clientId = clientId,
            serverName = serverName,
            clientMode = context.getString(R.string.dashboard_consys),
            allOk = allOk,
            moduleState = moduleState,
            errorText = errorText,
            statusMsg = statusMsg,
            sensorGroups = sensorGroups
        )
    }

    private fun buildSensorGroups(
        dataStreams: Map<String, SOSTClient.StreamInfo>,
        now: Long
    ): List<SensorGroupUi> {
        val grouped = linkedMapOf<String, MutableList<Pair<String, SOSTClient.StreamInfo>>>()
        for ((key, info) in dataStreams) {
            val sensorId = extractSensorId(key)
            grouped.getOrPut(sensorId) { mutableListOf() }.add(key to info)
        }
        return grouped.map { (sensorId, streams) ->
            val statusList = streams.map { (key, info) ->
                streamStatus(formatOutputName(key), info.lastEventTime, info.measPeriodMs.toLong(), info.errorCount, now)
            }
            SensorGroupUi(
                sensorId = sensorId,
                sensorName = formatSensorName(sensorId),
                streams = statusList,
                allOk = statusList.all { it.isOk }
            )
        }
    }

    private fun buildConSysGroups(
        dataStreams: Map<String, ConSysApiClientModule.StreamInfo>,
        now: Long
    ): List<SensorGroupUi> {
        val grouped = linkedMapOf<String, MutableList<Pair<String, ConSysApiClientModule.StreamInfo>>>()
        for ((key, info) in dataStreams) {
            val sensorId = extractSensorId(key)
            grouped.getOrPut(sensorId) { mutableListOf() }.add(key to info)
        }
        return grouped.map { (sensorId, streams) ->
            val statusList = streams.map { (key, info) ->
                streamStatus(formatOutputName(key), info.lastEventTime, info.measPeriodMs.toLong(), info.errorCount, now)
            }
            SensorGroupUi(
                sensorId = sensorId,
                sensorName = formatSensorName(sensorId),
                streams = statusList,
                allOk = statusList.all { it.isOk }
            )
        }
    }

    private fun streamStatus(
        outputName: String,
        lastEventTime: Long,
        measPeriodMs: Long,
        errorCount: Int,
        now: Long
    ): DataStreamStatusUi {
        val isRecent = lastEventTime > 0 &&
            now - lastEventTime <= maxOf(10_000L, measPeriodMs.coerceAtLeast(0) * 3)
        val isOk = errorCount == 0 && isRecent
        val statusText = context.getString(when {
            errorCount > 0 -> R.string.destination_stream_error
            lastEventTime <= 0 -> R.string.destination_stream_waiting
            isRecent -> R.string.destination_stream_active
            else -> R.string.destination_stream_stale
        })

        return DataStreamStatusUi(outputName = outputName, statusText = statusText, isOk = isOk)
    }

    private companion object {
        private val SYSTEM_ID_PATTERN = Pattern.compile("systems/([^/]+)/")
        private val OUTPUT_NAME_PATTERN = Pattern.compile("outputs/([^/]+)")

        fun extractServerName(clientName: String?, fallback: String): String {
            if (clientName != null && clientName.contains(" -> ")) {
                return clientName.substring(clientName.lastIndexOf(" -> ") + 4)
            }
            return fallback
        }

        fun extractSensorId(streamKey: String): String {
            val m = SYSTEM_ID_PATTERN.matcher(streamKey)
            return if (m.find()) m.group(1)!! else streamKey
        }

        fun formatSensorName(sensorId: String): String {
            val parts = sensorId.removePrefix("urn:").split(":")
            val name = when {
                parts.size >= 3 -> parts[parts.size - 2]
                parts.size == 2 -> parts[0]
                else -> sensorId
            }.replace('_', ' ').replace('-', ' ')
            return name.replaceFirstChar { it.uppercase() }
        }

        fun formatOutputName(streamKey: String): String {
            val m = OUTPUT_NAME_PATTERN.matcher(streamKey)
            val raw = if (m.find()) m.group(1)!! else streamKey
            return raw.replace(Regex("(?i)_data$"), "")
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceFirstChar { it.uppercase() }
        }
    }
}

enum class EnabledSensorCategory(@StringRes val labelRes: Int) {
    ALL(R.string.category_all),
    ATTENTION(R.string.category_attn),
    ON_DEVICE(R.string.category_on_device),
    BLUETOOTH(R.string.category_bluetooth),
    USB(R.string.category_usb),
    OTHERS(R.string.category_others),
}

data class DataStreamStatusUi(
    val outputName: String,
    val statusText: String,
    val isOk: Boolean
)

data class SensorGroupUi(
    val sensorId: String,
    val sensorName: String,
    val streams: List<DataStreamStatusUi>,
    val allOk: Boolean
)

data class ServerStatusUi(
    val clientId: String,
    val serverName: String,
    val clientMode: String,
    val allOk: Boolean,
    val moduleState: String = "",
    val errorText: String? = null,
    val statusMsg: String? = null,
    val sensorGroups: List<SensorGroupUi> = emptyList()
)
