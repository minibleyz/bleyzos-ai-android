package ru.bleyzos.ai.api

import android.content.Context
import ru.bleyzos.ai.api.auth.AuthManager
import ru.bleyzos.ai.api.chat.ChatApi
import ru.bleyzos.ai.api.chat.ChatEngine
import ru.bleyzos.ai.api.chat.FilesApi
import ru.bleyzos.ai.api.net.BzHttp
import ru.bleyzos.ai.api.store.DialogStore

/** Точка входа в API-модуль: сеть, авторизация, файлы, локальная история. */
class BleyzosAIClient(context: Context, origin: String = BzConfig.ORIGIN) {
    private val app = context.applicationContext
    internal val http = BzHttp(app, origin)

    val auth = AuthManager(app, http)
    val chat = ChatApi(http)
    val files = FilesApi(app, http)
    val store = DialogStore(app)

    /** Ключ владельца локальной истории: id пользователя или «guest». */
    fun ownerKey(): String = auth.user?.id ?: "guest"
}

/** Общий на процесс клиент и движок чата — переживают пересоздание Activity. */
object BleyzosAI {
    @Volatile private var client: BleyzosAIClient? = null
    @Volatile private var engine: ChatEngine? = null

    fun client(context: Context): BleyzosAIClient =
        client ?: synchronized(this) {
            client ?: BleyzosAIClient(context.applicationContext).also { client = it }
        }

    fun engine(context: Context): ChatEngine =
        engine ?: synchronized(this) {
            engine ?: ChatEngine(client(context)).also { engine = it }
        }
}
