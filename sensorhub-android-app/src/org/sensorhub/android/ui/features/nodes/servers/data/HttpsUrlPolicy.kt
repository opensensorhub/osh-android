package org.sensorhub.android.ui.features.nodes.servers.data

import org.sensorhub.android.R
import java.net.URI
import java.util.Locale

internal fun serverUrlError(value: String): Int? {
    val uri = try {
        URI(value.trim())
    } catch (_: Exception) {
        return R.string.ui_enter_a_valid_https_url
    }
    return when {
        value.isBlank() -> R.string.ui_enter_a_valid_url
        !uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank() || uri.userInfo != null ||
            uri.rawQuery != null || uri.rawFragment != null ->
            R.string.ui_enter_a_valid_https_url
        uri.port != -1 && uri.port !in 1..65535 -> R.string.ui_port_must_be_between_1_and_65535
        uri.rawAuthority?.endsWith(":") == true -> R.string.ui_enter_a_valid_port
        else -> null
    }
}

internal fun requireHttpsUrl(value: String, description: String): String {
    require(serverUrlError(value) == null) { "$description must use a valid HTTPS URL." }
    return normalizeHttpsUrl(value)
}

private fun normalizeHttpsUrl(value: String): String {
    val uri = URI(value.trim()).normalize()
    val path = uri.path.orEmpty().trimEnd('/').ifEmpty { null }
    return URI(
        "https",
        null,
        requireNotNull(uri.host).lowercase(Locale.ROOT),
        if (uri.port == 443) -1 else uri.port,
        path,
        null,
        null,
    ).toASCIIString()
}
