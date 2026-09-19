package ru.bleyzos.ai.api.net

import android.content.Context
import org.json.JSONObject
import java.net.HttpCookie
import java.net.HttpURLConnection

/**
 * Хранилище кук сервера. Сервер опознаёт владельца по куке `bleyzos_guest`
 * (её ставит middleware на корень и на пути /api) либо `bleyzos_session` (вошедший).
 * Храним в SharedPreferences, чтобы гостевые диалоги переживали перезапуск.
 */
internal class CookieStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("bleyzos_ai_api", Context.MODE_PRIVATE)
    private val cookies = LinkedHashMap<String, String>()

    init {
        val raw = prefs.getString(KEY, null)
        if (raw != null) {
            try {
                val o = JSONObject(raw)
                val it = o.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    cookies[k] = o.getString(k)
                }
            } catch (_: Exception) {
                // повреждённые данные — начинаем с чистого листа
            }
        }
    }

    @Synchronized
    fun get(name: String): String? = cookies[name]

    @Synchronized
    fun set(name: String, value: String?) {
        if (value.isNullOrEmpty()) cookies.remove(name) else cookies[name] = value
        persist()
    }

    /** Значение заголовка Cookie или null, если кук нет. */
    @Synchronized
    fun header(): String? =
        if (cookies.isEmpty()) null else cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }

    /** Забирает Set-Cookie из ответа. */
    @Synchronized
    fun absorb(conn: HttpURLConnection) {
        var changed = false
        for ((key, values) in conn.headerFields) {
            if (key == null || !key.equals("Set-Cookie", ignoreCase = true)) continue
            for (line in values) {
                val parsed = try {
                    HttpCookie.parse(line)
                } catch (_: Exception) {
                    emptyList<HttpCookie>()
                }
                for (c in parsed) {
                    if (c.maxAge == 0L || c.value.isNullOrEmpty()) {
                        if (cookies.remove(c.name) != null) changed = true
                    } else if (cookies[c.name] != c.value) {
                        cookies[c.name] = c.value
                        changed = true
                    }
                }
            }
        }
        if (changed) persist()
    }

    private fun persist() {
        val o = JSONObject()
        cookies.forEach { (k, v) -> o.put(k, v) }
        prefs.edit().putString(KEY, o.toString()).apply()
    }

    private companion object {
        const val KEY = "cookies"
    }
}
