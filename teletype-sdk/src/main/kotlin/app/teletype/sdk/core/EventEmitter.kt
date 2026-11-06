package app.teletype.sdk.core

import android.util.Log

/**
 * Minimal event emitter. Framework-agnostic so the headless core can be used
 * without Compose.
 */
class TypedEventEmitter {
    private val listeners = mutableMapOf<String, MutableSet<(Any?) -> Unit>>()

    fun <T> on(event: String, listener: (T) -> Unit): () -> Unit {
        val wrapped: (Any?) -> Unit = { payload ->
            @Suppress("UNCHECKED_CAST")
            listener(payload as T)
        }
        listeners.getOrPut(event) { mutableSetOf() }.add(wrapped)
        return { off(event, wrapped) }
    }

    fun onUnit(event: String, listener: () -> Unit): () -> Unit =
        on<Any?>(event) { listener() }

    fun <T> once(event: String, listener: (T) -> Unit): () -> Unit {
        var off: () -> Unit = {}
        off = on<T>(event) { payload ->
            off()
            listener(payload)
        }
        return off
    }

    private fun off(event: String, listener: (Any?) -> Unit) {
        listeners[event]?.remove(listener)
    }

    fun emit(event: String, payload: Any? = null) {
        val snapshot = listeners[event]?.toList().orEmpty()
        snapshot.forEach { listener ->
            try {
                listener(payload)
            } catch (err: Throwable) {
                Log.w("teletype", "event listener threw", err)
            }
        }
    }

    fun removeAllListeners() {
        listeners.clear()
    }
}
