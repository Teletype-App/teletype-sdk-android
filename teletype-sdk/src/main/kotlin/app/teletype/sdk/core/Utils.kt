package app.teletype.sdk.core

import app.teletype.sdk.i18n.TeletypeStrings
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow

fun guid(): String = UUID.randomUUID().toString()

fun makeId(length: Int = 10): String {
    val set = "ABCDEFGHIJKLMNOPQRSTUVWXYXYZabcdefghijklmnopqrstuvwxyz0123456789"
    return buildString {
        repeat(length + 1) { append(set.random()) }
    }
}

fun isEmojiOnly(text: String?): Boolean {
    if (text.isNullOrBlank()) return false
    val stripped = text
        .replace("\uFE0F", "")
        .replace("\u200D", "")
        .replace(Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]|[\\u2600-\\u27BF]"), "")
        .trim()
    return stripped.isEmpty() && text.trim().isNotEmpty()
}

fun formatBytes(
    bytes: Long?,
    decimals: Int = 2,
    labels: TeletypeStrings? = null,
): String {
    val sizes = if (labels != null) {
        listOf(labels.Bt, labels.Kb, labels.Mb, labels.Gb, labels.Tb, labels.Pb, labels.Eb, labels.Zb, labels.Yb)
    } else {
        listOf("BT", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB")
    }
    val value = bytes ?: 0L
    if (value <= 0L) return "0 ${sizes[0]}"
    val k = 1024.0
    val dm = decimals.coerceAtLeast(0)
    val i = floor(ln(value.toDouble()) / ln(k)).toInt().coerceIn(0, sizes.lastIndex)
    val num = value / k.pow(i.toDouble())
    val formatted = if (dm == 0) num.toInt().toString() else "%.${dm}f".format(num).trimEnd('0').trimEnd('.')
    return "$formatted ${sizes[i]}"
}

fun getExtension(nameOrUrl: String?): String {
    if (nameOrUrl.isNullOrBlank()) return ""
    val clean = nameOrUrl.split("?", "#").first()
    val dot = clean.lastIndexOf('.')
    if (dot < 0 || dot == clean.lastIndex) return ""
    return clean.substring(dot + 1).lowercase()
}

fun extractUrls(text: String): List<String> {
    val re = Regex("https?://[^\\s]+")
    return re.findAll(text).map { it.value }.toList()
}

fun <T> uniqueBy(arr: List<T>, keyFn: (T) -> String): List<T> {
    val seen = mutableSetOf<String>()
    val out = mutableListOf<T>()
    arr.forEach { item ->
        val key = keyFn(item)
        if (seen.add(key)) out += item
    }
    return out
}

fun JSONArray.toObjList(): List<JSONObject> {
    val out = mutableListOf<JSONObject>()
    for (i in 0 until length()) {
        val item = opt(i)
        if (item is JSONObject) out += item
    }
    return out
}
