package ru.bleyzos.ai.api.net

import android.content.Context
import ru.bleyzos.ai.api.BzApiException
import ru.bleyzos.ai.api.BzConfig
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Тонкая обёртка над HttpURLConnection: базовый адрес, куки, авторизация.
 * Пути к своему серверу ВСЕГДА со слешем на конце (`/api/chat/`) — сервер работает
 * с trailingSlash, а редирект POST HttpURLConnection не переживает.
 */
internal class BzHttp(context: Context, val origin: String) {
    val cookies = CookieStore(context)
    private val originHost: String = URL(origin).host

    @Volatile
    var bearer: String? = null

    fun resolve(pathOrUrl: String): URL =
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) URL(pathOrUrl)
        else URL(origin.trimEnd('/') + "/" + pathOrUrl.trimStart('/'))

    fun isOwn(url: URL): Boolean = url.host.equals(originHost, ignoreCase = true)

    fun open(
        method: String,
        pathOrUrl: String,
        readTimeoutMs: Int = BzConfig.REQUEST_READ_TIMEOUT_MS,
    ): HttpURLConnection {
        val url = resolve(pathOrUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = BzConfig.CONNECT_TIMEOUT_MS
        conn.readTimeout = readTimeoutMs
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("Accept-Encoding", "identity") // не даём прокси буферизовать стрим gzip-ом
        conn.setRequestProperty("User-Agent", "BleyzosAI-Android/1.0")
        if (isOwn(url)) {
            cookies.header()?.let { conn.setRequestProperty("Cookie", it) }
            bearer?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
        }
        return conn
    }

    /** Сохраняет куки из ответа (только от своего сервера). */
    fun absorb(conn: HttpURLConnection) {
        if (isOwn(conn.url)) cookies.absorb(conn)
    }

    /**
     * Сервер выдаёт куку гостя ответом на любой запрос к корню или к путям /api, но в самом
     * первом запросе куки ещё нет — а /api/chat/ без неё отвечает 401. Поэтому
     * перед первым чатом делаем «прогревочный» GET.
     */
    fun ensureOwner() {
        if (cookies.get(GUEST) != null || cookies.get(SESSION) != null) return
        val conn = open("GET", "/api/sandbox/status/")
        try {
            conn.responseCode
            absorb(conn)
        } finally {
            conn.disconnect()
        }
    }

    fun readAll(input: InputStream, limit: Long = BzConfig.MAX_DOWNLOAD_BYTES): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > limit) throw BzApiException("Файл слишком большой")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    /** Достаёт человекочитаемую ошибку из тела ответа ({"error": "..."}), если есть. */
    fun errorMessage(conn: HttpURLConnection, fallback: String): String {
        return try {
            val text = (conn.errorStream ?: return fallback).use { String(readAll(it, 64 * 1024), Charsets.UTF_8) }
            val err = org.json.JSONObject(text).optString("error")
            if (err.isNotEmpty()) err else fallback
        } catch (_: Exception) {
            fallback
        }
    }

    companion object {
        const val GUEST = "bleyzos_guest"
        const val SESSION = "bleyzos_session"
    }
}
