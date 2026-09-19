package ru.bleyzos.ai.api.store

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import ru.bleyzos.ai.api.MainThread
import ru.bleyzos.ai.api.model.Session
import ru.bleyzos.ai.api.model.sessionFromJson
import ru.bleyzos.ai.api.model.toJson
import java.io.File
import java.util.concurrent.Executors

/** Снимок сохранённых диалогов одного владельца. */
data class StoredChats(val sessions: List<Session>, val activeId: String?)

/**
 * Локальная история диалогов на устройстве (аналог localStorage в веб-версии).
 * У сервера нет REST-эндпоинтов для истории (она ходит через server actions Next.js,
 * из Android недоступных), поэтому история хранится в файлах приложения —
 * отдельно для каждого владельца: `guest` или id вошедшего пользователя.
 */
class DialogStore(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "bleyzos-chat-v1").apply { mkdirs() }
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "bleyzos-store").apply { isDaemon = true } }

    private fun fileFor(ownerKey: String) = File(dir, sanitize(ownerKey) + ".json")

    fun load(ownerKey: String): StoredChats {
        val f = fileFor(ownerKey)
        if (!f.exists()) return StoredChats(emptyList(), null)
        return try {
            val o = JSONObject(f.readText(Charsets.UTF_8))
            val arr = o.optJSONArray("sessions") ?: JSONArray()
            val sessions = (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(::sessionFromJson) }
            val active = if (o.has("activeId") && !o.isNull("activeId")) o.getString("activeId") else null
            StoredChats(sessions, active)
        } catch (_: Exception) {
            StoredChats(emptyList(), null) // повреждённый файл — начинаем с чистого листа
        }
    }

    /** Сохранение в фоне. Состояние неизменяемо, поэтому снимок безопасен. */
    fun saveAsync(ownerKey: String, chats: StoredChats) {
        writer.execute {
            try {
                val o = JSONObject()
                    .put("sessions", JSONArray().also { a -> chats.sessions.forEach { a.put(it.toJson()) } })
                    .put("activeId", chats.activeId ?: JSONObject.NULL)
                val target = fileFor(ownerKey)
                val tmp = File(dir, target.name + ".tmp")
                tmp.writeText(o.toString(), Charsets.UTF_8)
                if (!tmp.renameTo(target)) {
                    target.delete()
                    tmp.renameTo(target)
                }
            } catch (_: Exception) {
                // диск недоступен — продолжаем без сохранения
            }
        }
    }

    /** Удаляет историю владельца (при выходе из аккаунта — чтобы чужие чаты не оставались на устройстве). */
    fun clear(ownerKey: String) {
        writer.execute { fileFor(ownerKey).delete() }
    }

    private fun sanitize(key: String) = key.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
}
