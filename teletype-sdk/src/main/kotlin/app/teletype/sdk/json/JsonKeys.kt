package app.teletype.sdk.json

import org.json.JSONArray
import org.json.JSONObject

private fun toSnake(str: String): String =
    str
        .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
        .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1_$2")
        .replace(Regex("[\\s-]+"), "_")
        .lowercase()

private fun toCamel(str: String): String =
    str.replace(Regex("[_-]([a-zA-Z0-9])")) { it.groupValues[1].uppercase() }

fun isPlainObject(value: Any?): Boolean =
    value is JSONObject || value is JSONArray || value is Map<*, *> || value is Iterable<*>

/**
 * Recursively convert object keys to snake_case (for socket/REST payloads).
 * `null` becomes `""` — matching the web widget (`api.snakeKeys(null)`).
 */
fun snakeKeys(input: Any?): Any? {
    if (input == null || input === JSONObject.NULL) return ""
    return when (input) {
        is JSONArray -> JSONArray().also { out ->
            for (i in 0 until input.length()) out.put(snakeKeys(input.opt(i)))
        }
        is JSONObject -> JSONObject().also { out ->
            val keys = input.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = input.opt(key)
                out.put(toSnake(key), if (isPlainObject(value)) snakeKeys(value) else value ?: JSONObject.NULL)
            }
        }
        is Map<*, *> -> snakeKeys(mapToJson(input))
        is Iterable<*> -> snakeKeys(JSONArray().also { arr -> input.forEach { arr.put(anyToJson(it)) } })
        else -> input
    }
}

/** Recursively convert object keys to camelCase (for parsing responses). */
fun camelKeys(input: Any?): Any? {
    if (input == null || input === JSONObject.NULL) return input
    return when (input) {
        is JSONArray -> JSONArray().also { out ->
            for (i in 0 until input.length()) out.put(camelKeys(input.opt(i)))
        }
        is JSONObject -> JSONObject().also { out ->
            val keys = input.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = input.opt(key)
                out.put(toCamel(key), if (isPlainObject(value)) camelKeys(value) else value)
            }
        }
        is Map<*, *> -> camelKeys(mapToJson(input))
        else -> input
    }
}

fun serializeForm(obj: Map<String, Any?>, prefix: String = ""): String {
    val parts = mutableListOf<String>()
    obj.forEach { (p, v) ->
        val k = if (prefix.isEmpty()) p else "$prefix[$p]"
        if (v is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            parts += serializeForm(v as Map<String, Any?>, k)
        } else if (v is JSONObject) {
            parts += serializeForm(jsonToMap(v), k)
        } else {
            parts += "${encode(k)}=${encode(v?.toString() ?: "")}"
        }
    }
    return parts.joinToString("&")
}

private fun encode(s: String): String = java.net.URLEncoder.encode(s, Charsets.UTF_8.name()).replace("+", "%20")

fun anyToJson(value: Any?): Any = when (value) {
    null -> JSONObject.NULL
    is JSONObject, is JSONArray -> value
    is Map<*, *> -> mapToJson(value)
    is Iterable<*> -> JSONArray().also { arr -> value.forEach { arr.put(anyToJson(it)) } }
    is Boolean, is Number, is String -> value
    else -> value.toString()
}

fun mapToJson(map: Map<*, *>): JSONObject = JSONObject().also { obj ->
    map.forEach { (k, v) ->
        if (k != null) obj.put(k.toString(), anyToJson(v))
    }
}

fun jsonToMap(obj: JSONObject): Map<String, Any?> {
    val out = linkedMapOf<String, Any?>()
    val keys = obj.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        out[key] = obj.opt(key)
    }
    return out
}

fun JSONObject.optStr(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return opt(key)?.toString()
}

fun JSONObject.optBool(key: String, default: Boolean = false): Boolean {
    if (!has(key) || isNull(key)) return default
    return when (val v = opt(key)) {
        is Boolean -> v
        is Number -> v.toInt() != 0
        is String -> v.equals("true", ignoreCase = true) || v == "1"
        else -> default
    }
}

fun JSONObject.optIntOrNull(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    return when (val v = opt(key)) {
        is Number -> v.toInt()
        is String -> v.toIntOrNull()
        else -> null
    }
}

fun JSONObject.optLongOrNull(key: String): Long? {
    if (!has(key) || isNull(key)) return null
    return when (val v = opt(key)) {
        is Number -> v.toLong()
        is String -> v.toLongOrNull()
        else -> null
    }
}

fun JSONObject.optObj(key: String): JSONObject? = opt(key) as? JSONObject

fun JSONObject.optArr(key: String): JSONArray? = opt(key) as? JSONArray

fun parseJson(raw: String): Any {
    val trimmed = raw.trim()
    if (trimmed.startsWith("[")) return JSONArray(trimmed)
    if (trimmed.startsWith("{")) return JSONObject(trimmed)
    return trimmed
}
