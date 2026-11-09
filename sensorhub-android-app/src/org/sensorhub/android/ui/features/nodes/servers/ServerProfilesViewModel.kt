package org.sensorhub.android.ui.features.nodes.servers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.sensorhub.android.ui.features.nodes.servers.data.ServerProfileItem
import org.sensorhub.android.ui.features.nodes.servers.data.ServerProfileRepository

class ServerProfilesViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = ServerProfileRepository.getInstance(application)
    private val _profiles = MutableStateFlow<List<ServerProfileItem>>(emptyList())
    val profiles: StateFlow<List<ServerProfileItem>> = _profiles.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _profiles.value = repo.all
    }

    fun setEnabled(id: String, enabled: Boolean) {
        repo.setEnabled(id, enabled)
        refresh()
    }

    fun delete(id: String) {
        repo.delete(id)
        refresh()
    }
}
