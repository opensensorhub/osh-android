package org.sensorhub.android.ui.features.client.data

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.ConcurrentHashMap

/** Owns the current WebSocket for every active observation stream. */
class ObservationSubscriptionManager {
    private val sockets = ConcurrentHashMap<String, WebSocket>()

    fun subscribe(
        client: OkHttpClient,
        streamId: String,
        request: Request,
        onText: (String) -> Unit = {},
        onBytes: (ByteArray) -> Unit = {},
        onDisconnected: (Throwable?) -> Unit = {},
    ) {
        val socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) = onText(text)

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = onBytes(bytes.toByteArray())

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (sockets.remove(streamId, webSocket)) onDisconnected(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (sockets.remove(streamId, webSocket)) onDisconnected(null)
            }
        })
        sockets.put(streamId, socket)?.cancel()
    }

    fun unsubscribe(streamId: String, reason: String) {
        sockets.remove(streamId)?.close(1000, reason)
    }

    fun closeAll(reason: String) {
        sockets.keys.toList().forEach { unsubscribe(it, reason) }
    }
}
