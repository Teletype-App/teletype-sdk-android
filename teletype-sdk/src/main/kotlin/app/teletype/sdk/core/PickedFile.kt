package app.teletype.sdk.core

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import app.teletype.sdk.types.LocalFile
import java.io.File
import java.io.FileOutputStream

/**
 * Resolve a picker/content [uri] into a [LocalFile] the SDK can upload:
 * real display name, mime type, size, and a cached `file://` copy.
 */
fun localFileFromUri(context: Context, uri: Uri): LocalFile {
    var name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
    var size: Long? = null
    runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (nameIdx >= 0) cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { name = it }
            if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                cursor.getLong(sizeIdx).takeIf { it > 0 }?.let { size = it }
            }
        }
    }
    val type = context.contentResolver.getType(uri)
        ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
        ?: "application/octet-stream"

    val cacheDir = File(context.cacheDir, "teletype-up").apply { mkdirs() }
    val safeName = name.replace(Regex("[\\\\/:]"), "_").ifBlank { "file" }
    val dest = File(cacheDir, "teletype-pick-${System.currentTimeMillis()}-$safeName")
    context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(dest).use { output -> input.copyTo(output) }
    } ?: throw IllegalStateException("uploadFailed: cannot read $uri")
    if (dest.length() <= 0L) throw IllegalStateException("uploadFailed: empty $uri")

    return LocalFile(
        uri = Uri.fromFile(dest).toString(),
        name = name,
        type = type,
        size = size ?: dest.length(),
    )
}
