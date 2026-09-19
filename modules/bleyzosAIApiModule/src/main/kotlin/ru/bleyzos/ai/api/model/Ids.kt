package ru.bleyzos.ai.api.model

import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

/** Заголовок диалога из первого сообщения (как makeTitle в chat-app.tsx). */
fun makeTitle(text: String): String {
    val clean = text.trim().replace(Regex("\\s+"), " ")
    return if (clean.length > 44) clean.take(44) + "…" else clean
}
