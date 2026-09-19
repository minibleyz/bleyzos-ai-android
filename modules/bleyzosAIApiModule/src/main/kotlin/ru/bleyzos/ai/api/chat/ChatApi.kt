package ru.bleyzos.ai.api.chat

import org.json.JSONArray
import org.json.JSONObject
import ru.bleyzos.ai.api.BzApiException
import ru.bleyzos.ai.api.BzConfig
import ru.bleyzos.ai.api.Cancelable
import ru.bleyzos.ai.api.MainThread
import ru.bleyzos.ai.api.model.Artifact
import ru.bleyzos.ai.api.model.ArtifactKind
import ru.bleyzos.ai.api.model.StreamEvent
import ru.bleyzos.ai.api.net.BzHttp
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Реплика истории для сервера: `{role, content}`. */
data class ChatTurn(val role: String, val content: String)

/** Файл для загрузки. [open] вызывается один раз в фоновом потоке. */
class UploadFile(
    val name: String,
    val mime: String,
    val size: Long,
    val open: () -> InputStream,
)

class ChatRequest(
    val messages: List<ChatTurn>,
    /** Id диалога; сервер сам привяжет его к владельцу (куке). */
    val sessionId: String,
    /** Новые файлы — уйдут multipart-ом. */
    val files: List<UploadFile> = emptyList(),
    /** Имена уже загруженных файлов (при повторной отправке/редактировании). */
    val uploadedNames: List<String> = emptyList(),
)

interface ChatStreamListener {
    fun onEvent(event: StreamEvent)
    /** Не вызывается при отмене пользователем. */
    fun onError(error: BzApiException)
    /** Вызывается последним ВСЕГДА (аналог finally). */
    fun onFinished()
}

/** POST /api/chat/ → NDJSON-стрим событий агента. */
class ChatApi internal constructor(private val http: BzHttp) {

    fun stream(request: ChatRequest, listener: ChatStreamListener): Cancelable {
        val cancelled = AtomicBoolean(false)
        val connRef = arrayOfNulls<HttpURLConnection>(1)

        MainThread.io.execute {
            var error: BzApiException? = null
            try {
                var attempt = 0
                while (true) {
                    if (cancelled.get()) break
                    http.ensureOwner()
                    val conn = http.open("POST", "/api/chat/", BzConfig.STREAM_READ_TIMEOUT_MS)
                    connRef[0] = conn
                    if (cancelled.get()) {
                        conn.disconnect()
                        break
                    }
                    try {
                        writeRequest(conn, request)
                        val code = conn.responseCode
                        http.absorb(conn)
                        if (code == 401 && http.cookies.get(BzHttp.SESSION) != null) {
                            throw BzApiException("Сессия истекла — войдите снова", 401)
                        }
                        if (code == 401 && attempt == 0) {
                            // Кука гостя протухла/потеряна: сбрасываем и получаем новую.
                            attempt++
                            http.cookies.set(BzHttp.GUEST, null)
                            conn.disconnect()
                            continue
                        }
                        if (code !in 200..299) {
                            throw BzApiException(http.errorMessage(conn, "Ошибка сервера ($code)"), code)
                        }
                        readEvents(conn, cancelled, listener)
                    } finally {
                        conn.disconnect()
                    }
                    break
                }
            } catch (e: BzApiException) {
                if (!cancelled.get()) error = e
            } catch (e: IOException) {
                if (!cancelled.get()) error = BzApiException("Нет соединения с сервером", cause = e)
            } catch (e: Exception) {
                if (!cancelled.get()) error = BzApiException(e.message ?: "Ошибка запроса", cause = e)
            }
            MainThread.post {
                error?.let { listener.onError(it) }
                listener.onFinished()
            }
        }

        return Cancelable {
            cancelled.set(true)
            // disconnect() из другого потока прерывает блокирующее чтение
            MainThread.io.execute { connRef[0]?.disconnect() }
        }
    }

    /* ─────────────────────────── запрос ─────────────────────────── */

    private fun writeRequest(conn: HttpURLConnection, req: ChatRequest) {
        conn.doOutput = true
        val messagesJson = JSONArray().also { arr ->
            req.messages.forEach { arr.put(JSONObject().put("role", it.role).put("content", it.content)) }
        }
        if (req.files.isEmpty()) {
            val body = JSONObject()
                .put("messages", messagesJson)
                .put("session", req.sessionId)
                .put("uploaded", JSONArray(req.uploadedNames))
                .toString()
                .toByteArray(Charsets.UTF_8)
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setFixedLengthStreamingMode(body.size)
            conn.outputStream.use { it.write(body) }
        } else {
            val boundary = "----BleyzosAI" + UUID.randomUUID().toString().replace("-", "")
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.setChunkedStreamingMode(0)
            BufferedOutputStream(conn.outputStream).use { out ->
                writeField(out, boundary, "messages", messagesJson.toString())
                writeField(out, boundary, "session", req.sessionId)
                for (f in req.files) {
                    val head = "--$boundary\r\n" +
                        "Content-Disposition: form-data; name=\"files\"; filename=\"${escapeHeader(f.name)}\"\r\n" +
                        "Content-Type: ${f.mime.ifEmpty { "application/octet-stream" }}\r\n\r\n"
                    out.write(head.toByteArray(Charsets.UTF_8))
                    f.open().use { input ->
                        val buf = ByteArray(16 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                        }
                    }
                    out.write("\r\n".toByteArray())
                }
                out.write("--$boundary--\r\n".toByteArray())
            }
        }
    }

    private fun writeField(out: OutputStream, boundary: String, name: String, value: String) {
        out.write(
            ("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n").toByteArray(Charsets.UTF_8)
        )
        out.write(value.toByteArray(Charsets.UTF_8))
        out.write("\r\n".toByteArray())
    }

    private fun escapeHeader(s: String): String =
        s.replace("\"", "%22").replace("\r", "").replace("\n", "")

    /* ─────────────────────────── ответ ─────────────────────────── */

    private fun readEvents(conn: HttpURLConnection, cancelled: AtomicBoolean, listener: ChatStreamListener) {
        conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            while (!cancelled.get()) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue
                val ev = parseEvent(line) ?: continue // неполная/битая строка — пропускаем, как в вебе
                MainThread.post { if (!cancelled.get()) listener.onEvent(ev) }
                if (ev is StreamEvent.Done) break
            }
        }
    }

    internal fun parseEvent(line: String): StreamEvent? = try {
        val o = JSONObject(line)
        when (o.optString("t")) {
            "text" -> StreamEvent.Text(o.optString("v"))
            "tool" -> StreamEvent.Tool(o.optString("id"), o.optString("name"), argsOf(o.optJSONObject("args")))
            "toolres" -> StreamEvent.ToolResult(
                id = o.optString("id"),
                ok = o.optBoolean("ok"),
                output = o.optString("output"),
                ms = o.optLong("ms"),
            )
            "artifact" -> StreamEvent.ArtifactEvent(
                Artifact(
                    name = o.optString("name"),
                    kind = ArtifactKind.from(o.optString("kind")),
                    content = if (o.has("content") && !o.isNull("content")) o.getString("content") else null,
                    url = if (o.has("url") && !o.isNull("url")) o.getString("url") else null,
                )
            )
            "done" -> StreamEvent.Done
            else -> null
        }
    } catch (_: Exception) {
        null
    }

    private fun argsOf(o: JSONObject?): Map<String, String> {
        if (o == null) return emptyMap()
        val m = LinkedHashMap<String, String>()
        val it = o.keys()
        while (it.hasNext()) {
            val k = it.next()
            m[k] = o.opt(k)?.toString().orEmpty()
        }
        return m
    }
}
