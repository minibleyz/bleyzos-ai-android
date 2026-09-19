package ru.bleyzos.ai.api

/** Ошибка API. [status] — HTTP-код, если ответ был получен. */
class BzApiException(
    message: String,
    val status: Int? = null,
    cause: Throwable? = null,
) : Exception(message, cause)
