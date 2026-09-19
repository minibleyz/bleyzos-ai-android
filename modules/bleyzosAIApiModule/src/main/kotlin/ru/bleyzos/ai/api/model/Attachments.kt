package ru.bleyzos.ai.api.model

import ru.bleyzos.ai.api.BzConfig

/** Тип вложения для иконки. Порт src/lib/attachments.ts. */
enum class AttachmentKind { IMAGE, AUDIO, VIDEO, ARCHIVE, CODE, PDF, TEXT, FILE }

object Attachments {
    const val MAX_FILE_SIZE: Long = BzConfig.MAX_FILE_SIZE
    const val MAX_FILES: Int = BzConfig.MAX_FILES

    private val TEXT_EXTS = setOf("txt", "md", "csv", "log", "rtf")
    private val CODE_EXTS = setOf(
        "js", "ts", "tsx", "jsx", "py", "json", "html", "css", "sql", "sh",
        "yml", "yaml", "xml", "go", "rs", "java", "c", "cpp", "php", "rb", "kt",
    )

    fun kind(mime: String, name: String): AttachmentKind {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            mime.startsWith("image/") -> AttachmentKind.IMAGE
            mime.startsWith("audio/") -> AttachmentKind.AUDIO
            mime.startsWith("video/") -> AttachmentKind.VIDEO
            mime == "application/zip" || mime == "application/x-zip-compressed" || ext == "zip" -> AttachmentKind.ARCHIVE
            mime == "application/pdf" || ext == "pdf" -> AttachmentKind.PDF
            ext in TEXT_EXTS || mime.startsWith("text/") -> AttachmentKind.TEXT
            ext in CODE_EXTS -> AttachmentKind.CODE
            else -> AttachmentKind.FILE
        }
    }

    fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes Б"
        bytes < 1024 * 1024 -> "${Math.round(bytes / 1024.0)} КБ"
        else -> String.format(java.util.Locale.US, "%.1f МБ", bytes / (1024.0 * 1024.0))
    }
}
