package app.teletype.sdk.core

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.net.URI

/**
 * socket.io wrapper. The Teletype backend runs socket.io **v2**, so the Java
 * client is pinned to 1.x for Engine.IO v3 / Socket.IO v2 wire compatibility.
 */
class SocketClient(
    private val url: String,
    private val extraOptions: IO.Options? = null,
    private val debug: Boolean = false,
) {
    private var socket: Socket? = null

    val connected: Boolean get() = socket?.connected() == true
    val disconnected: Boolean get() = socket == null || socket?.connected() != true

    fun ensureCreated() {
        if (socket != null) return
        val opts = IO.Options().apply {
            forceNew = true
            reconnection = true
            extraOptions?.let { src ->
                src.query?.let { query = it }
                src.transports?.let { transports = it }
            }
        }
        socket = IO.socket(URI.create(url), opts)
    }

    fun connect() {
        ensureCreated()
        if (socket?.connected() != true) {
            socket?.connect()
        }
    }

    fun disconnect() {
        socket?.disconnect()
    }

    fun on(event: String, cb: (Array<Any>) -> Unit) {
        socket?.on(event) { args -> cb(args ?: emptyArray()) }
    }

    fun once(event: String, cb: (Array<Any>) -> Unit) {
        socket?.once(event) { args -> cb(args ?: emptyArray()) }
    }

    fun off(event: String? = null) {
        val s = socket ?: return
        if (event != null) {
            s.off(event)
        } else {
            s.off()
        }
    }

    fun emit(event: String, payload: Any? = null) {
        if (debug) {
            Log.d("teletype", "[ws] ⬆ $event ${payload ?: "(no payload)"}")
        }
        if (payload == null) {
            socket?.emit(event)
        } else {
            socket?.emit(event, payload)
        }
    }

    fun firstPayload(args: Array<Any>): Any? = args.firstOrNull()

    fun firstObject(args: Array<Any>): JSONObject {
        return when (val first = args.firstOrNull()) {
            is JSONObject -> first
            is String -> try {
                JSONObject(first)
            } catch (_: Exception) {
                JSONObject()
            }
            else -> JSONObject()
        }
    }
}
