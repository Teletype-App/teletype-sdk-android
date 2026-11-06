package app.teletype.sdk.core

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import app.teletype.sdk.config.TeletypeEnvironment
import app.teletype.sdk.json.camelKeys
import app.teletype.sdk.json.optObj
import app.teletype.sdk.json.optStr
import app.teletype.sdk.json.parseJson
import app.teletype.sdk.json.serializeForm
import app.teletype.sdk.json.snakeKeys
import app.teletype.sdk.types.LocalFile
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.buffer
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

typealias UploadProgressHandler = (progress: Int) -> Unit

/**
 * Thin REST client for the Teletype widget HTTP API.
 * Endpoints match the web widget's `ApiService`.
 */
class ApiClient(
    private val env: TeletypeEnvironment,
    private val externalId: String,
    private val context: Context,
    debug: Boolean = false,
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .apply {
            if (debug) {
                addInterceptor(
                    HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC },
                )
            }
        }
        .build()

    private fun base(path: String): String = "${env.apiUrl}$path"

    private fun v(path: String): String = base("api/v${env.apiVersion}/$path")

    private fun getJson(url: String): JSONObject {
        val req = Request.Builder().url(url).get().build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            val parsed = camelKeys(parseJson(body))
            return parsed as? JSONObject ?: JSONObject().put("data", parsed)
        }
    }

    private fun postForm(url: String, payload: Map<String, Any?>): JSONObject {
        val encoded = serializeForm(payload)
        val req = Request.Builder()
            .url(url)
            .post(encoded.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            val parsed = camelKeys(parseJson(body))
            return parsed as? JSONObject ?: JSONObject()
        }
    }

    fun getIp(): JSONObject = getJson(v("widget/ip"))

    fun getConfig(): JSONObject = getJson(v("widget/$externalId/config"))

    fun getHistory(appealId: String): JSONObject {
        val dt = java.net.URLEncoder.encode("2018-02-12T17:08:55+0000", Charsets.UTF_8.name())
        return getJson(v("history/$externalId/$appealId/messages?datetime=$dt"))
    }

    fun uploadUrl(url: String, appealId: String? = null): JSONObject {
        val payload = mutableMapOf<String, Any?>("url" to url)
        if (appealId != null) payload["appeal_id"] = appealId
        return postForm(v("widget-attachment/$externalId"), payload)
    }

    /**
     * Upload a local file as multipart/form-data.
     * `POST api/v1/widget-attachment/<channel_id>` — body is `file` + `externalId`.
     */
    fun uploadFile(file: LocalFile, onProgress: UploadProgressHandler? = null): JSONObject {
        val url = v("widget-attachment/$externalId")
        val local = copyToCache(file)
        val mediaType = (file.type.ifBlank { guessMime(file.name) })
            .toMediaTypeOrNull()
            ?: "application/octet-stream".toMediaType()
        val fileBody = CountingRequestBody(local.asRequestBody(mediaType)) { written, total ->
            if (onProgress != null && total > 0) {
                onProgress(((written * 100) / total).toInt().coerceIn(0, 100))
            }
        }
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name.ifBlank { "file" }, fileBody)
            .addFormDataPart("externalId", externalId)
            .build()
        val req = Request.Builder().url(url).post(body).build()
        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("uploadFailed: ${resp.code} ${raw.take(200)}")
            }
            val parsed = try {
                camelKeys(parseJson(raw))
            } catch (_: Exception) {
                throw IllegalStateException("uploadFailed: ${resp.code} ${raw.take(200)}")
            }
            return parsed as? JSONObject ?: JSONObject()
        }
    }

    fun collect(data: Map<String, Any?>): JSONObject =
        postForm(base("api/v${env.apiVersion}/collect"), jsonToSnakeMap(data))

    fun reportError(data: Map<String, Any?>): JSONObject =
        postForm(base("api/v${env.apiVersion}/widget/error"), jsonToSnakeMap(data))

    fun notifyLocationChange(location: String): JSONObject =
        postForm(v("widget/$externalId/url"), mapOf("client_url" to location))

    private fun jsonToSnakeMap(data: Map<String, Any?>): Map<String, Any?> {
        val snaked = snakeKeys(data)
        val obj = snaked as? JSONObject ?: return data
        val out = linkedMapOf<String, Any?>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            out[key] = obj.opt(key)
        }
        return out
    }

    private fun copyToCache(file: LocalFile): File {
        val cacheDir = File(context.cacheDir, "teletype-up").apply { mkdirs() }
        val safeName = file.name.replace(Regex("[\\\\/]"), "_").ifBlank { "file" }
        val dest = File(cacheDir, "teletype-up-${System.currentTimeMillis()}-$safeName")
        val uri = Uri.parse(file.uri)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { output -> input.copyTo(output) }
            if (dest.length() > 0) return dest
        }
        val asFile = when {
            file.uri.startsWith("file://") -> File(Uri.parse(file.uri).path ?: file.uri)
            else -> File(file.uri)
        }
        if (asFile.exists()) {
            asFile.copyTo(dest, overwrite = true)
            return dest
        }
        throw IllegalStateException("uploadFailed: cannot read ${file.uri}")
    }

    private fun guessMime(name: String): String {
        val ext = MimeTypeMap.getFileExtensionFromUrl(name)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }
}

private class CountingRequestBody(
    private val delegate: RequestBody,
    private val onProgress: (written: Long, total: Long) -> Unit,
) : RequestBody() {
    override fun contentType() = delegate.contentType()
    override fun contentLength() = delegate.contentLength()
    override fun writeTo(sink: okio.BufferedSink) {
        val total = contentLength()
        val counting = object : okio.ForwardingSink(sink) {
            var written = 0L
            override fun write(source: okio.Buffer, byteCount: Long) {
                super.write(source, byteCount)
                written += byteCount
                onProgress(written, total)
            }
        }
        val buffered = counting.buffer()
        delegate.writeTo(buffered)
        buffered.flush()
    }
}

fun JSONObject.dataObject(): JSONObject? = optObj("data")

fun JSONObject.dataString(): String? = when (val data = opt("data")) {
    is String -> data
    else -> optStr("data")
}
