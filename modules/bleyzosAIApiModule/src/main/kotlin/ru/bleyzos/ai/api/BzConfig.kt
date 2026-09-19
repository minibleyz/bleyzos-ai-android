package ru.bleyzos.ai.api

/**
 * Настройки API Bleyzos AI. Всё, что зашито в веб-версии (домен, лимиты),
 * лежит здесь в одном месте.
 */
object BzConfig {
    /** Продакшен-домен (в веб-версии зашит в /api/auth/login и device-flow). */
    const val ORIGIN = "https://ai.bleyzos.ru"

    /** Логотип бренда в сайдбаре. */
    const val BRAND_IMAGE_URL = "https://cdn.bleyzos.ru/brand.png"

    /** Гостевые диалоги живут 14 дней (TTL считает сервер). */
    const val GUEST_TTL_DAYS = 14

    /** Лимиты вложений — как в chat-input.tsx / lib/attachments.ts. */
    const val MAX_FILE_SIZE: Long = 20L * 1024 * 1024
    const val MAX_FILES = 8

    /** Агент упёрся в лимит инструментов — UI показывает кнопку «Continue». */
    const val TOOL_LIMIT_MARK = "[REACHED_TOOL_LIMIT]"

    const val CONNECT_TIMEOUT_MS = 15_000
    /** Стрим может долго молчать, пока агент гоняет инструменты. */
    const val STREAM_READ_TIMEOUT_MS = 300_000
    const val REQUEST_READ_TIMEOUT_MS = 30_000

    /** Максимум, который скачиваем в память (картинки/артефакты). */
    const val MAX_DOWNLOAD_BYTES = 64L * 1024 * 1024
}
