package app.teletype.sdk.core

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

interface KeyValueStore {
    fun getItem(key: String): String?
    fun setItem(key: String, value: String)
    fun removeItem(key: String)
    fun getAllKeys(): Set<String>
}

class MemoryStore : KeyValueStore {
    private val map = mutableMapOf<String, String>()
    override fun getItem(key: String): String? = map[key]
    override fun setItem(key: String, value: String) { map[key] = value }
    override fun removeItem(key: String) { map.remove(key) }
    override fun getAllKeys(): Set<String> = map.keys.toSet()
}

class SharedPrefsStore(context: Context) : KeyValueStore {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("teletype_sdk", Context.MODE_PRIVATE)

    override fun getItem(key: String): String? = prefs.getString(key, null)
    override fun setItem(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    override fun removeItem(key: String) { prefs.edit().remove(key).apply() }
    override fun getAllKeys(): Set<String> = prefs.all.keys
}

/**
 * Persistence abstraction. Mirrors the web widget's namespaced localStorage
 * (`teletype-{apiId}-{key}`).
 */
class Storage(
    private val apiId: String,
    backend: KeyValueStore? = null,
    context: Context? = null,
) {
    private val backend: KeyValueStore = backend ?: context?.let { SharedPrefsStore(it) } ?: MemoryStore()
    private val cache = mutableMapOf<String, Any?>()

    private fun fullKey(key: String): String = "teletype-$apiId-$key"

    fun hydrate(keys: List<String>) {
        keys.forEach { key ->
            cache[key] = parse(backend.getItem(fullKey(key)))
        }
    }

    private fun parse(raw: String?): Any? {
        if (raw == null) return null
        return try {
            val trimmed = raw.trim()
            when {
                trimmed.startsWith("{") -> JSONObject(trimmed)
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed == "true" -> true
                trimmed == "false" -> false
                trimmed.toLongOrNull() != null && !trimmed.contains('.') -> trimmed.toLong()
                trimmed.toDoubleOrNull() != null -> trimmed.toDouble()
                else -> JSONObject.quote(trimmed).let {
                    if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                        org.json.JSONTokener(trimmed).nextValue()
                    } else trimmed
                }
            }
        } catch (_: Exception) {
            raw
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String, defaultValue: T? = null): T? {
        val value = cache[key]
        return if (value == null) defaultValue else value as? T ?: defaultValue
    }

    fun getString(key: String, defaultValue: String = ""): String {
        val value = cache[key] ?: return defaultValue
        return value.toString().ifBlank { defaultValue }
    }

    fun getLong(key: String, defaultValue: Long = 0L): Long {
        return when (val value = cache[key]) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull() ?: defaultValue
            else -> defaultValue
        }
    }

    fun getJson(key: String): JSONObject? = cache[key] as? JSONObject

    fun getJsonArray(key: String): JSONArray? = cache[key] as? JSONArray

    fun set(key: String, value: Any?) {
        cache[key] = value
        val encoded = when (value) {
            null -> {
                backend.removeItem(fullKey(key))
                return
            }
            is JSONObject, is JSONArray -> value.toString()
            is String -> JSONObject.quote(value)
            else -> JSONObject.wrap(value)?.toString() ?: value.toString()
        }
        backend.setItem(fullKey(key), encoded)
    }

    fun remove(key: String) {
        cache.remove(key)
        backend.removeItem(fullKey(key))
    }

    fun clearAllTeletypeKeys() {
        backend.getAllKeys()
            .filter { it.startsWith("teletype-") }
            .forEach { backend.removeItem(it) }
        cache.clear()
    }
}
