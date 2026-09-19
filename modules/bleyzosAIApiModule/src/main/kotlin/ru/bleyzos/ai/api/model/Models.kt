package ru.bleyzos.ai.api.model

/* Модели повторяют src/components/chat/types.ts веб-версии. Всё неизменяемое:
 * состояние обновляется копированием, поэтому UI может сравнивать по ссылке. */

enum class Role(val wire: String) {
    USER("user"), ASSISTANT("assistant");

    companion object {
        fun from(s: String?): Role = if (s == "assistant") ASSISTANT else USER
    }
}

data class Attachment(
    val id: String,
    val name: String,
    val size: Long,
    val mime: String,
)

enum class ToolStatus(val wire: String) {
    RUNNING("running"), OK("ok"), ERROR("error");

    companion object {
        fun from(s: String?): ToolStatus = when (s) {
            "ok" -> OK
            "error" -> ERROR
            else -> RUNNING
        }
    }
}

/**
 * Вызов инструмента агента. [name]: shell, write_file, edit_file, read_file,
 * list_files, delete_file, build_zip, present_file (для неизвестных UI покажет имя как есть).
 */
data class ToolCall(
    val id: String,
    val name: String,
    val args: Map<String, String>,
    val status: ToolStatus,
    val output: String? = null,
    val ms: Long? = null,
)

sealed class Part {
    data class Text(val text: String) : Part()
    data class Tool(val call: ToolCall) : Part()
}

enum class ArtifactKind(val wire: String) {
    CODE("code"), HTML("html"), MARKDOWN("markdown"), IMAGE("image"), ZIP("zip");

    companion object {
        fun from(s: String?): ArtifactKind = values().firstOrNull { it.wire == s } ?: CODE
    }
}

data class Artifact(
    val name: String,
    val kind: ArtifactKind,
    val content: String? = null,
    /** Относительный (`/api/files/?s=..&p=..`) или абсолютный адрес. */
    val url: String? = null,
)

data class MessageVariant(
    val content: String,
    val tail: List<Message>,
)

data class Message(
    val id: String,
    val role: Role,
    val content: String,
    val attachments: List<Attachment> = emptyList(),
    val parts: List<Part> = emptyList(),
    val artifacts: List<Artifact> = emptyList(),
    val variants: List<MessageVariant>? = null,
    val variantIndex: Int? = null,
)

data class Session(
    val id: String,
    val title: String,
    val messages: List<Message>,
    val updatedAt: Long,
)

/** Пользователь Bleyzos (OAuth userinfo). */
data class AuthUser(
    val id: String,
    val email: String,
    val name: String,
)

/** События NDJSON-стрима /api/chat/ (см. src/app/api/chat/route.ts). */
sealed class StreamEvent {
    data class Text(val value: String) : StreamEvent()
    data class Tool(val id: String, val name: String, val args: Map<String, String>) : StreamEvent()
    data class ToolResult(val id: String, val ok: Boolean, val output: String, val ms: Long) : StreamEvent()
    data class ArtifactEvent(val artifact: Artifact) : StreamEvent()
    object Done : StreamEvent()
}
