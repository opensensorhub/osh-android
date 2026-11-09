package org.sensorhub.android.ui.features.client

import android.app.Application
import android.util.Base64
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.sensorhub.android.ui.features.client.data.ObservationSubscriptionManager
import org.sensorhub.android.ui.features.client.data.PtzAxisRange
import org.sensorhub.android.ui.features.client.data.RemoteControlStream
import org.sensorhub.android.ui.features.client.data.RemoteNodeState
import org.sensorhub.android.ui.features.client.data.RemoteSystem
import org.sensorhub.android.ui.features.client.data.RemoteVisualization
import org.sensorhub.android.ui.features.client.data.StreamCardState
import org.sensorhub.android.ui.features.client.data.StreamStatus
import org.sensorhub.android.ui.features.client.data.SystemDetailUiState
import org.sensorhub.android.ui.features.client.data.TrackRepository
import org.sensorhub.android.ui.features.nodes.servers.data.ServerProfileItem
import org.sensorhub.android.ui.features.nodes.servers.data.ServerProfileRepository
import org.sensorhub.android.ui.features.nodes.servers.data.requireHttpsUrl
import org.sensorhub.android.UnsafeTls
import org.sensorhub.android.ui.features.client.visualizations.VideoStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class OshClientViewModel(application: Application) : AndroidViewModel(application) {
    private val profiles = ServerProfileRepository.getInstance(application)
    private val http = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val insecureHttp by lazy { UnsafeTls.insecureClient(http) }
    private val subscriptions = ObservationSubscriptionManager()
    private val trackRepository = TrackRepository()
    val tracks = trackRepository.tracks
    private val videoRenderers = ConcurrentHashMap<String, VideoStream>()

    private val _nodes = MutableStateFlow<List<RemoteNodeState>>(emptyList())
    val nodes: StateFlow<List<RemoteNodeState>> = _nodes.asStateFlow()

    private val _enabledLocations = MutableStateFlow<Set<String>>(emptySet())
    val enabledLocations: StateFlow<Set<String>> = _enabledLocations.asStateFlow()

    private val _enabledVideos = MutableStateFlow<Set<String>>(emptySet())
    val enabledVideos: StateFlow<Set<String>> = _enabledVideos.asStateFlow()

    private val _videoErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val videoErrors: StateFlow<Map<String, String>> = _videoErrors.asStateFlow()

    private val _otherValues = MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())
    val otherValues: StateFlow<Map<String, Map<String, String>>> = _otherValues.asStateFlow()

    private val _streamStatuses = MutableStateFlow<Map<String, StreamStatus>>(emptyMap())
    val streamStatuses: StateFlow<Map<String, StreamStatus>> = _streamStatuses.asStateFlow()

    fun systemDetailState(profileId: String, systemId: String) = combine(
        nodes,
        enabledLocations,
        enabledVideos,
        videoErrors,
        otherValues,
    ) { nodes, locations, videos, errors, values ->
        SystemDetailUiState(
            system = nodes.firstOrNull { it.profileId == profileId }?.systems?.firstOrNull { it.id == systemId },
            enabledLocations = locations,
            enabledVideos = videos,
            videoErrors = errors,
            otherValues = values,
        )
    }.combine(streamStatuses) { state, statuses -> state.copy(streamStatuses = statuses) }
        .combine(tracks) { state, tracks ->
            state.copy(tracks = tracks).withCards()
        }

    private fun SystemDetailUiState.withCards(): SystemDetailUiState {
        val currentSystem = system ?: return this
        val cards = currentSystem.visualizations.map { visualization ->
            val status = streamStatuses[visualization.dataStreamId] ?: when (visualization.kind) {
                RemoteVisualization.Kind.VIDEO -> StreamStatus.PAUSED
                else -> StreamStatus.CONNECTING
            }
            when (visualization.kind) {
                RemoteVisualization.Kind.VIDEO -> StreamCardState.Video(
                    streamId = visualization.dataStreamId,
                    name = visualization.name,
                    status = status,
                    error = videoErrors[visualization.dataStreamId],
                )

                RemoteVisualization.Kind.LOCATION -> StreamCardState.Location(
                    streamId = visualization.dataStreamId,
                    name = visualization.name,
                    status = status,
                    position = tracks[visualization.dataStreamId]
                        ?.let { it.latitude to it.longitude }
                        ?: currentSystem.location,
                )

                RemoteVisualization.Kind.OTHER -> StreamCardState.Values(
                    streamId = visualization.dataStreamId,
                    name = visualization.name,
                    status = status,
                    values = otherValues[visualization.dataStreamId].orEmpty(),
                )
            }
        }
        return copy(cards = cards)
    }

    init {
        refreshProfiles()
    }

    fun refreshProfiles() {
        val configuredProfiles = profiles.all
        val currentNodes = _nodes.value.associateBy { it.profileId }

        _nodes.value = configuredProfiles.map { profile ->
            currentNodes[profile.id]?.copy(
                name = profile.serverName,
                endpointUrl = profile.endpointUrl,
            ) ?: RemoteNodeState(
                profileId = profile.id,
                name = profile.serverName,
                endpointUrl = profile.endpointUrl,
            )
        }
    }

    fun discover(profileId: String) {
        val profile = profiles.getById(profileId) ?: return
        updateNode(profileId) { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { discoverNode(profile) } }
                .onSuccess { systems ->
                    trackRepository.replaceSystemLocations(profileId, systems)
                    updateNode(profileId) { it.copy(loading = false, systems = systems) }
                }
                .onFailure { error ->
                    updateNode(profileId) {
                        it.copy(loading = false, error = error.message ?: "Unable to connect")
                    }
                }
        }
    }

    fun setVideoEnabled(profileId: String, visualization: RemoteVisualization, enabled: Boolean) {
        if (!enabled) {
            subscriptions.unsubscribe(visualization.dataStreamId, "disabled")
            _enabledVideos.update { it - visualization.dataStreamId }
            _videoErrors.update { it - visualization.dataStreamId }
            _streamStatuses.update { it + (visualization.dataStreamId to StreamStatus.PAUSED) }
            return
        }

        val profile = profiles.getById(profileId) ?: return
        _videoErrors.update { it - visualization.dataStreamId }
        _streamStatuses.update { it + (visualization.dataStreamId to StreamStatus.CONNECTING) }
        val url = apiBase(profile.endpointUrl)
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://") +
                // A literal '+' in a query parameter is decoded as a space.
                // This must match platform-ui's SWE_BINARY_FORMAT exactly.
                "/datastreams/${visualization.dataStreamId}/observations?format=application/swe%2Bbinary"
        val request = authorizedRequest(url, profile).build()
        subscriptions.subscribe(
            client = clientFor(profile),
            streamId = visualization.dataStreamId,
            request = request,
            onBytes = { bytes ->
                videoRenderers[visualization.dataStreamId]
                    ?.update(System.currentTimeMillis(), bytes)
                _streamStatuses.update { it + (visualization.dataStreamId to StreamStatus.RECEIVING) }
            },
            onDisconnected = { error ->
                _enabledVideos.update { it - visualization.dataStreamId }
                _videoErrors.update {
                    it + (visualization.dataStreamId to (error?.message ?: "Connection closed."))
                }
                _streamStatuses.update { it + (visualization.dataStreamId to StreamStatus.DISCONNECTED) }
            },
        )
        _enabledVideos.update { it + visualization.dataStreamId }
    }

    fun videoRenderer(system: RemoteSystem, visualization: RemoteVisualization): VideoStream =
        videoRenderers.getOrPut(visualization.dataStreamId) {
            VideoStream(listOf(visualization.dataStreamId), system.name, 1280, 720)
        }

    fun stopSystemVideos(profileId: String, system: RemoteSystem) {
        system.visualizations
            .filter { it.kind == RemoteVisualization.Kind.VIDEO }
            .forEach { visualization ->
                setVideoEnabled(profileId, visualization, false)
                videoRenderers.remove(visualization.dataStreamId)?.disconnect()
            }
    }

    fun startSystemLocationStreams(profileId: String, system: RemoteSystem) {
        system.visualizations
            .filter { it.kind == RemoteVisualization.Kind.LOCATION }
            .forEach { setLocationEnabled(profileId, system, it, true) }
    }

    fun stopSystemLocationStreams(profileId: String, system: RemoteSystem) {
        system.visualizations
            .filter { it.kind == RemoteVisualization.Kind.LOCATION }
            .forEach { setLocationEnabled(profileId, system, it, false) }
    }

    fun startSystemOtherStreams(profileId: String, system: RemoteSystem) {
        system.visualizations
            .filter { it.kind == RemoteVisualization.Kind.OTHER }
            .forEach { setOtherEnabled(profileId, it, true) }
    }

    fun stopSystemOtherStreams(profileId: String, system: RemoteSystem) {
        system.visualizations
            .filter { it.kind == RemoteVisualization.Kind.OTHER }
            .forEach { setOtherEnabled(profileId, it, false) }
    }

    private fun setOtherEnabled(
        profileId: String,
        visualization: RemoteVisualization,
        enabled: Boolean
    ) {
        if (!enabled) {
            subscriptions.unsubscribe(visualization.dataStreamId, "system screen closed")
            _otherValues.update { it - visualization.dataStreamId }
            _streamStatuses.update { it - visualization.dataStreamId }
            return
        }

        val profile = profiles.getById(profileId) ?: return
        _streamStatuses.update { it + (visualization.dataStreamId to StreamStatus.CONNECTING) }
        val url = apiBase(profile.endpointUrl)
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://") +
                "/datastreams/${visualization.dataStreamId}/observations?format=application/json"
        val request = authorizedRequest(url, profile).build()
        subscriptions.subscribe(
            client = clientFor(profile),
            streamId = visualization.dataStreamId,
            request = request,
            onText = { text ->
                handleOtherMessage(text, visualization.dataStreamId)
            },
            onBytes = { bytes ->
                handleOtherMessage(
                    bytes.toString(Charsets.UTF_8),
                    visualization.dataStreamId
                )
            },
            onDisconnected = {
                _streamStatuses.update { it + (visualization.dataStreamId to StreamStatus.DISCONNECTED) }
            },
        )
    }

    private fun handleOtherMessage(text: String, dataStreamId: String) {
        val values = runCatching {
            val observation = org.json.JSONTokener(text).nextValue() as? JSONObject
                ?: return@runCatching emptyMap()
            flattenFields(observation.opt("result"))
        }.getOrNull() ?: return
        if (values.isNotEmpty()) _otherValues.update { it + (dataStreamId to values) }
        if (values.isNotEmpty()) _streamStatuses.update { it + (dataStreamId to StreamStatus.RECEIVING) }
    }

    private fun flattenFields(value: Any?, prefix: String = ""): Map<String, String> = buildMap {
        when (value) {
            is JSONObject -> value.keys().forEach { key ->
                putAll(flattenFields(value.opt(key), if (prefix.isBlank()) key else "$prefix.$key"))
            }

            is JSONArray -> {
                if (value.length() == 0) put(prefix, "[]")
                else (0 until value.length()).forEach { index ->
                    putAll(flattenFields(value.opt(index), "$prefix[$index]"))
                }
            }

            else -> if (prefix.isNotBlank()) put(prefix, value?.toString() ?: "null")
        }
    }

    fun setLocationEnabled(
        profileId: String,
        system: RemoteSystem,
        visualization: RemoteVisualization,
        enabled: Boolean,
    ) {
        if (!enabled) {
            subscriptions.unsubscribe(visualization.dataStreamId, "disabled")
            _enabledLocations.update { it - visualization.dataStreamId }
            _streamStatuses.update { it - visualization.dataStreamId }
            trackRepository.remove(visualization.dataStreamId)
            return
        }

        val profile = profiles.getById(profileId) ?: return
        _streamStatuses.update { it + (visualization.dataStreamId to StreamStatus.CONNECTING) }
        val url = apiBase(profile.endpointUrl)
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://") +
                "/datastreams/${visualization.dataStreamId}/observations?format=application/om%2Bjson"
        val request = authorizedRequest(url, profile).build()
        subscriptions.subscribe(
            client = clientFor(profile),
            streamId = visualization.dataStreamId,
            request = request,
            onText = { text ->
                handleLocationMessage(text, visualization, system)
            },
            onBytes = { bytes ->
                handleLocationMessage(
                    bytes.toString(Charsets.UTF_8),
                    visualization,
                    system
                )
            },
            onDisconnected = {
                _enabledLocations.update { it - visualization.dataStreamId }
                _streamStatuses.update { it + (visualization.dataStreamId to StreamStatus.DISCONNECTED) }
            },
        )
        _enabledLocations.update { it + visualization.dataStreamId }
    }


    private fun handleLocationMessage(
        text: String,
        visualization: RemoteVisualization,
        system: RemoteSystem,
    ) {
        findCoordinates(text)?.let { (lat, lon) ->
            trackRepository.update(visualization.dataStreamId, system.id, system.name, lat, lon)
            _streamStatuses.update { it + (visualization.dataStreamId to StreamStatus.RECEIVING) }
        }
    }

    private fun discoverNode(profile: ServerProfileItem): List<RemoteSystem> {
        val systemsJson =
            getJson("${apiBase(profile.endpointUrl)}/systems?limit=10000&f=geojson", profile)
        val datastreamsJson =
            getJson("${apiBase(profile.endpointUrl)}/datastreams?limit=10000", profile)
        val controlStreamsJson =
            getJson("${apiBase(profile.endpointUrl)}/controlstreams?limit=10000", profile)
        val visualizations = classifyDatastreams(datastreamsJson)
        val ptzBySystem = classifyControlStreams(controlStreamsJson, profile)

        val items =
            systemsJson.optJSONArray("features") ?: systemsJson.optJSONArray("items") ?: JSONArray()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val id = item.optString("id")
                if (id.isBlank()) continue
                val properties = item.optJSONObject("properties")
                val name = item.optString("label").ifBlank {
                    item.optString("name").ifBlank {
                        properties?.optString("name").orEmpty().ifBlank { id }
                    }
                }
                val description = item.optString("description").ifBlank {
                    properties?.optString("description").orEmpty()
                }
                val uid = item.optString("uid").ifBlank {
                    properties?.optString("uid").orEmpty()
                }
                add(
                    RemoteSystem(
                        id = id,
                        uid = uid,
                        name = name,
                        description = description,
                        location = pointFromGeoJson(item.optJSONObject("geometry")),
                        visualizations = visualizations[id].orEmpty(),
                        status = "",
                        ptz = ptzBySystem[id],
                    )
                )
            }
        }
    }

    private fun classifyControlStreams(
        json: JSONObject,
        profile: ServerProfileItem,
    ): Map<String, RemoteControlStream> {
        val result = mutableMapOf<String, RemoteControlStream>()
        val items = json.optJSONArray("items") ?: return emptyMap()

        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val systemId = item.optString("system@id")
            val controlStreamId = item.optString("id")
            if (systemId.isBlank() || controlStreamId.isBlank()) continue

            val definitions = item.optJSONArray("controlledProperties")?.let { props ->
                (0 until props.length()).mapNotNull {
                    props.optJSONObject(it)?.optString("definition")
                }
            }.orEmpty().toSet()

            val hasPtz = definitions.any {
                it.endsWith("Pan") || it.endsWith("Tilt") || it.endsWith("ZoomFactor")
            }
            if (!hasPtz) continue

            val schema = runCatching {
                getJson(
                    "${apiBase(profile.endpointUrl)}/controlstreams/$controlStreamId/schema",
                    profile,
                )
            }.getOrNull()
            val absolutePanRange = schema?.let { absolutePtzRange(it, "pan") }
            val absoluteTiltRange = schema?.let { absolutePtzRange(it, "tilt") }
            val absoluteZoomRange = schema?.let { absolutePtzRange(it, "zoom") }

            result[systemId] = RemoteControlStream(
                controlStreamId = controlStreamId,
                supportsAbsolutePan = "http://sensorml.com/ont/swe/property/Pan" in definitions,
                supportsAbsoluteTilt = "http://sensorml.com/ont/swe/property/Tilt" in definitions,
                supportsAbsoluteZoom = "http://sensorml.com/ont/swe/property/ZoomFactor" in definitions,
                supportsRelativePan = "http://sensorml.com/ont/swe/property/RelativePan" in definitions,
                supportsRelativeTilt = "http://sensorml.com/ont/swe/property/RelativeTilt" in definitions,
                supportsRelativeZoom = "http://sensorml.com/ont/swe/property/RelativeZoomFactor" in definitions,
                supportsPresets = "http://sensorml.com/ont/swe/property/CameraPresetPositionName" in definitions,
                absolutePanRange = absolutePanRange,
                absoluteTiltRange = absoluteTiltRange,
                absoluteZoomRange = absoluteZoomRange,
            )
        }
        return result
    }

    private fun absolutePtzRange(controlStream: JSONObject, name: String): PtzAxisRange? {
        val schema = controlStream.optJSONObject("parametersSchema") ?: return null
        val directItems = schema.optJSONArray("items") ?: return null
        val candidates = buildList {
            for (index in 0 until directItems.length()) {
                val item = directItems.optJSONObject(index) ?: continue
                add(item)
                if (item.optString("type") == "DataRecord") {
                    val fields = item.optJSONArray("fields") ?: continue
                    for (fieldIndex in 0 until fields.length()) {
                        fields.optJSONObject(fieldIndex)?.let(::add)
                    }
                }
            }
        }
        val parameter = candidates.firstOrNull { it.optString("name") == name } ?: return null
        val interval = parameter.optJSONObject("constraint")
            ?.optJSONArray("intervals")
            ?.optJSONArray(0)
            ?: return null
        val minimum = interval.optDouble(0, Double.NaN)
        val maximum = interval.optDouble(1, Double.NaN)
        if (!minimum.isFinite() || !maximum.isFinite() || minimum >= maximum) return null

        return PtzAxisRange(
            minimum = minimum.toFloat(),
            maximum = maximum.toFloat(),
            unit = parameter.optJSONObject("uom")?.optString("code").orEmpty(),
        )
    }

    private fun pointFromGeoJson(geometry: JSONObject?): Pair<Double, Double>? {
        if (geometry?.optString("type") != "Point") return null
        val coordinates = geometry.optJSONArray("coordinates") ?: return null
        val longitude = coordinates.optDouble(0, Double.NaN)
        val latitude = coordinates.optDouble(1, Double.NaN)
        return if (latitude.isFinite() && longitude.isFinite()) latitude to longitude else null
    }

    private fun classifyDatastreams(json: JSONObject): Map<String, List<RemoteVisualization>> {
        val result = mutableMapOf<String, MutableList<RemoteVisualization>>()
        val items = json.optJSONArray("items") ?: return emptyMap()

        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val systemId = item.optString("system@id")
            val dataStreamId = item.optString("id")
            val name = item.optString("name")
            if (systemId.isBlank() || dataStreamId.isBlank()) continue

            val resultType = item.optString("resultType")
            val definitions = item.optJSONArray("observedProperties")?.let { props ->
                (0 until props.length()).mapNotNull {
                    props.optJSONObject(it)?.optString("definition")
                }
            }.orEmpty()

            val kind = when {
                resultType == "coverage" && definitions.any { it.endsWith("/RasterImage") } ->
                    RemoteVisualization.Kind.VIDEO

                resultType == "vector" && definitions.any {
                    it.endsWith("/LocationVector") || it.endsWith(
                        "SensorLocation"
                    )
                } ->
                    RemoteVisualization.Kind.LOCATION

                definitions.any { it.endsWith("/GeodeticLatitude") } &&
                        definitions.any { it.endsWith("/Longitude") } ->
                    RemoteVisualization.Kind.LOCATION

                else -> RemoteVisualization.Kind.OTHER
            }

            result.getOrPut(systemId) { mutableListOf() }
                .add(RemoteVisualization(dataStreamId, name, kind))
        }
        return result
    }

    fun sendPtzCommand(
        profileId: String,
        controlStream: RemoteControlStream,
        params: Map<String, Any>
    ) {
        val profile = profiles.getById(profileId) ?: return

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    postCommand(controlStream.controlStreamId, params, profile)
                }
            }
                .onFailure { error ->
                    Toast.makeText(getApplication<Application>(), "Command Failed: ${error.message ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun postCommand(
        controlStreamId: String,
        params: Map<String, Any>,
        profile: ServerProfileItem,
    ) {
        val url = "${apiBase(profile.endpointUrl)}/controlstreams/$controlStreamId/commands"
        val body = JSONObject(params)
            .toString()
            .toByteArray(Charsets.UTF_8)
            .toRequestBody("application/swe+json".toMediaType())
        val request = authorizedRequest(url, profile).post(body).build()
        clientFor(profile).newCall(request).execute().use {
            if (!it.isSuccessful) error("${it.code} ${it.message} sending PTZ command to $controlStreamId")
        }
    }

    private fun getJson(url: String, profile: ServerProfileItem): JSONObject {
        val response = clientFor(profile).newCall(authorizedRequest(url, profile).get().build()).execute()
        response.use {
            if (!it.isSuccessful) error("${it.code} ${it.message} from $url")
            return JSONObject(it.body?.string().orEmpty())
        }
    }

    private fun authorizedRequest(url: String, profile: ServerProfileItem): Request.Builder {
        val builder = Request.Builder().url(url)
        if (profile.username.isNotBlank()) {
            val password = profiles.getPassword(profile.id)
            val token = Base64.encodeToString(
                "${profile.username}:$password".toByteArray(),
                Base64.NO_WRAP,
            )
            builder.header("Authorization", "Basic $token")
        }
        return builder
    }

    private fun clientFor(profile: ServerProfileItem): OkHttpClient =
        if (profile.disableSSL) insecureHttp else http

    private fun findCoordinates(text: String): Pair<Double, Double>? = runCatching {
        findCoordinates(JSONObject(text))
    }.getOrNull()

    private fun findCoordinates(value: Any?): Pair<Double, Double>? {
        when (value) {
            is JSONObject -> {
                val latitude = number(value, "lat", "latitude")
                val longitude = number(value, "lon", "lng", "longitude")
                if (latitude != null && longitude != null) return latitude to longitude
                value.keys().forEach { key -> findCoordinates(value.opt(key))?.let { return it } }
            }

            is JSONArray -> for (index in 0 until value.length()) {
                findCoordinates(value.opt(index))?.let { return it }
            }
        }
        return null
    }

    private fun number(json: JSONObject, vararg keys: String): Double? {
        for (key in keys) {
            if (!json.has(key)) continue
            when (val value = json.opt(key)) {
                is Number -> return value.toDouble()
                is String -> value.toDoubleOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun apiBase(endpoint: String): String {
        val normalized = requireHttpsUrl(endpoint, "Node endpoint").trimEnd('/')
        return if (normalized.endsWith("/api")) normalized else
            "${nodeBase(normalized)}/sensorhub/api"
    }

    private fun nodeBase(endpoint: String): String {
        val normalized = endpoint.trim().trimEnd('/')
        return normalized.substringBefore("/sensorhub/").removeSuffix("/sensorhub")
    }

    private fun updateNode(id: String, transform: (RemoteNodeState) -> RemoteNodeState) {
        _nodes.update { nodes -> nodes.map { if (it.profileId == id) transform(it) else it } }
    }

    override fun onCleared() {
        subscriptions.closeAll("client closed")
        videoRenderers.values.forEach { it.disconnect() }
        videoRenderers.clear()
        http.dispatcher.executorService.shutdown()
        super.onCleared()
    }
}
