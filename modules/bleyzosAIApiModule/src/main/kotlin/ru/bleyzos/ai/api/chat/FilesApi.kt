package ru.bleyzos.ai.api.chat

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import ru.bleyzos.ai.api.BzApiException
import ru.bleyzos.ai.api.Cancelable
import ru.bleyzos.ai.api.MainThread
import ru.bleyzos.ai.api.net.BzHttp
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/** Файлы песочницы: GET /api/files/?s=..&p=.. (артефакты, ZIP, картинки). */
class FilesApi internal constructor(
    private val context: Context,
    private val http: BzHttp,
) {
    /** Загрузка байтов (картинки, логотип, артефакты). Куки шлём только на свой домен. */
    fun fetchBytes(
        pathOrUrl: String,
        onResult: (bytes: ByteArray?, error: BzApiException?) -> Unit,
    ): Cancelable {
        val cancelled = AtomicBoolean(false)
        MainThread.io.execute {
            var bytes: ByteArray? = null
            var err: BzApiException? = null
            try {
                val url = http.resolve(pathOrUrl)
                if (http.isOwn(url)) http.ensureOwner()
                val conn = http.open("GET", pathOrUrl)
                try {
                    val code = conn.responseCode
                    http.absorb(conn)
                    if (code !in 200..299) throw BzApiException("Не удалось загрузить файл ($code)", code)
                    bytes = conn.inputStream.use { http.readAll(it) }
                } finally {
                    conn.disconnect()
                }
            } catch (e: BzApiException) {
                err = e
            } catch (e: IOException) {
                err = BzApiException("Нет соединения с сервером", cause = e)
            } catch (e: Exception) {
                err = BzApiException(e.message ?: "Ошибка загрузки", cause = e)
            }
            MainThread.post { if (!cancelled.get()) onResult(bytes, err) }
        }
        return Cancelable { cancelled.set(true) }
    }

    /**
     * Сохраняет файл в общую папку «Загрузки» (MediaStore, без разрешений на Android 10+).
     * В колбэк приходит Uri сохранённого файла.
     */
    fun saveToDownloads(
        pathOrUrl: String,
        fileName: String,
        onResult: (uri: Uri?, error: BzApiException?) -> Unit,
    ) {
        MainThread.io.execute {
            var saved: Uri? = null
            var err: BzApiException? = null
            try {
                val url = http.resolve(pathOrUrl)
                if (http.isOwn(url)) http.ensureOwner()
                val conn = http.open("GET", pathOrUrl, 120_000)
                try {
                    val code = conn.responseCode
                    http.absorb(conn)
                    if (code !in 200..299) throw BzApiException("Не удалось скачать файл ($code)", code)
                    val safeName = fileName.substringAfterLast('/').ifBlank { "bleyzos-file" }
                    val ext = safeName.substringAfterLast('.', "").lowercase()
                    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
                    val resolver = context.contentResolver
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                        put(MediaStore.Downloads.MIME_TYPE, mime)
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Bleyzos AI")
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val target = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: throw BzApiException("Не удалось создать файл в «Загрузках»")
                    try {
                        resolver.openOutputStream(target)!!.use { out ->
                            conn.inputStream.use { input ->
                                val buf = ByteArray(16 * 1024)
                                while (true) {
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    out.write(buf, 0, n)
                                }
                            }
                        }
                        val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                        resolver.update(target, done, null, null)
                        saved = target
                    } catch (e: Exception) {
                        resolver.delete(target, null, null)
                        throw e
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (e: BzApiException) {
                err = e
            } catch (e: IOException) {
                err = BzApiException("Не удалось сохранить файл", cause = e)
            } catch (e: Exception) {
                err = BzApiException(e.message ?: "Ошибка сохранения", cause = e)
            }
            MainThread.post { onResult(saved, err) }
        }
    }
}
