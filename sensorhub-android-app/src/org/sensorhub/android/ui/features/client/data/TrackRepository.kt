package org.sensorhub.android.ui.features.client.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class TrackRepository {
    private val maxTrailPoints = 200
    private val _tracks = MutableStateFlow<Map<String, RemoteTrack>>(emptyMap())
    val tracks: StateFlow<Map<String, RemoteTrack>> = _tracks.asStateFlow()

    fun update(streamId: String, systemId: String, label: String, latitude: Double, longitude: Double) {
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return
        _tracks.update { current ->
            val old = current[streamId]
            val points = (old?.trail.orEmpty() + (latitude to longitude)).takeLast(maxTrailPoints)
            current + (streamId to RemoteTrack(streamId, systemId, label, latitude, longitude, points))
        }
    }

    fun replaceSystemLocations(profileId: String, systems: List<RemoteSystem>) {
        val prefix = "system:$profileId:"
        _tracks.update { current ->
            val withoutProfile = current.filterKeys { !it.startsWith(prefix) }
            systems.fold(withoutProfile) { tracks, system ->
                val (latitude, longitude) = system.location ?: return@fold tracks
                if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return@fold tracks
                val key = "$prefix${system.id}"
                tracks + (key to RemoteTrack(key, system.id, system.name, latitude, longitude, emptyList()))
            }
        }
    }

    fun remove(streamId: String) { _tracks.update { it - streamId } }
    fun clear() { _tracks.value = emptyMap() }
}
