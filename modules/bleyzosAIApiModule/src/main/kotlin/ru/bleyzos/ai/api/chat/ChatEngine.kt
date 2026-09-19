package ru.bleyzos.ai.api.chat

import ru.bleyzos.ai.api.BleyzosAIClient
import ru.bleyzos.ai.api.BzApiException
import ru.bleyzos.ai.api.Cancelable
import ru.bleyzos.ai.api.model.Attachment
import ru.bleyzos.ai.api.model.Message
import ru.bleyzos.ai.api.model.MessageVariant
import ru.bleyzos.ai.api.model.Part
import ru.bleyzos.ai.api.model.Role
import ru.bleyzos.ai.api.model.Session
import ru.bleyzos.ai.api.model.StreamEvent
import ru.bleyzos.ai.api.model.ToolCall
import ru.bleyzos.ai.api.model.ToolStatus
import ru.bleyzos.ai.api.model.makeTitle
import ru.bleyzos.ai.api.model.newId
import ru.bleyzos.ai.api.store.StoredChats

/** Снимок состояния чата. Неизменяем — UI сравнивает объекты по ссылке. */
data class ChatState(
    val sessions: List<Session> = emptyList(),
    val activeId: String? = null,
    val streaming: Boolean = false,
) {
    val active: Session? get() = sessions.firstOrNull { it.id == activeId }
}

/**
 * Состояние и логика чата без единого View: порт chat-app.tsx
 * (сессии, отправка, потоковый ответ, редактирование с ветками-вариантами).
 * Все методы вызываются из главного потока.
 */
class ChatEngine(private val client: BleyzosAIClient) {

    fun interface Listener {
        fun onChanged(state: ChatState)
    }

    private val listeners = ArrayList<Listener>()
    private var cancelable: Cancelable? = null
    private var ownerKey: String = client.ownerKey()

    var state: ChatState = ChatState()
        private set

    init {
        val stored = client.store.load(ownerKey)
        state = ChatState(stored.sessions, stored.activeId?.takeIf { id -> stored.sessions.any { it.id == id } })
        // Смена аккаунта: чужие чаты не должны оставаться на экране.
        client.auth.addListener { onOwnerChanged() }
    }

    fun addListener(l: Listener) {
        if (!listeners.contains(l)) listeners.add(l)
    }

    fun removeListener(l: Listener) {
        listeners.remove(l)
    }

    private fun publish(next: ChatState) {
        state = next
        listeners.toList().forEach { it.onChanged(next) }
    }

    private fun persist() {
        client.store.saveAsync(ownerKey, StoredChats(state.sessions, state.activeId))
    }

    private fun onOwnerChanged() {
        val newKey = client.ownerKey()
        if (newKey == ownerKey) return
        val oldKey = ownerKey
        stop()
        // Выход из аккаунта — чистим его историю на устройстве (как в веб-версии).
        if (oldKey != "guest" && client.auth.user == null) client.store.clear(oldKey)
        ownerKey = newKey
        val stored = client.store.load(newKey)
        publish(ChatState(stored.sessions, stored.activeId?.takeIf { id -> stored.sessions.any { it.id == id } }))
    }

    /* ─────────────────────────── навигация ─────────────────────────── */

    fun selectSession(id: String) {
        if (state.sessions.none { it.id == id }) return
        publish(state.copy(activeId = id))
        persist()
    }

    fun newChat() {
        stop()
        publish(state.copy(activeId = null))
        persist()
    }

    fun deleteSession(id: String) {
        if (id == state.activeId) stop()
        val next = state.sessions.filter { it.id != id }
        val active = if (id == state.activeId) next.firstOrNull()?.id else state.activeId
        publish(state.copy(sessions = next, activeId = active))
        persist()
    }

    fun stop() {
        cancelable?.cancel()
    }

    /** Освобождает ресурсы (стрим отменяется). */
    fun shutdown() {
        stop()
        listeners.clear()
    }

    /* ─────────────────────────── отправка ─────────────────────────── */

    private fun updateSession(id: String, fn: (Session) -> Session) {
        publish(state.copy(sessions = state.sessions.map { if (it.id == id) fn(it) else it }))
    }

    /**
     * @param replaceMessageId — id пользовательского сообщения, которое редактируют:
     * прежняя ветка сохраняется в variants, ответ генерируется заново.
     */
    fun send(rawText: String, files: List<UploadFile> = emptyList(), replaceMessageId: String? = null) {
        val text = rawText.trim()
        if (state.streaming) return
        if (text.isEmpty() && files.isEmpty() && replaceMessageId == null) return

        var targetId = state.activeId
        val priorMessages: List<Message>
        var attachments = files.map { Attachment(newId(), it.name, it.size, it.mime) }
        var variantsForNew: Pair<List<MessageVariant>, Int>? = null
        var retitle = true

        val fileNames = files.joinToString(", ") { it.name }

        if (replaceMessageId != null) {
            val source = state.active ?: return
            val idx = source.messages.indexOfFirst { it.id == replaceMessageId }
            if (idx == -1 || source.messages[idx].role != Role.USER) return
            attachments = source.messages[idx].attachments
            if (text.isEmpty() && attachments.isEmpty()) return
            priorMessages = source.messages.subList(0, idx)
            val old = source.messages[idx]
            val list = (old.variants ?: listOf(MessageVariant(old.content, emptyList()))).toMutableList()
            val cur = old.variantIndex ?: 0
            list[cur] = MessageVariant(old.content, source.messages.subList(idx + 1, source.messages.size))
            list.add(MessageVariant(text, emptyList()))
            variantsForNew = list to list.lastIndex
            retitle = idx == 0
            targetId = source.id
        } else if (targetId == null || state.sessions.none { it.id == targetId }) {
            targetId = newId()
            val fresh = Session(
                id = targetId,
                title = makeTitle(text.ifEmpty { fileNames.ifEmpty { "Чат" } }),
                messages = emptyList(),
                updatedAt = System.currentTimeMillis(),
            )
            publish(state.copy(sessions = listOf(fresh) + state.sessions, activeId = targetId))
            priorMessages = emptyList()
            retitle = false
        } else {
            priorMessages = state.sessions.first { it.id == targetId }.messages
            retitle = false
        }

        val titleSource = text.ifEmpty { fileNames.ifEmpty { "Файлы" } }
        val sessionId = targetId

        val userMsg = Message(
            id = newId(),
            role = Role.USER,
            content = text,
            attachments = attachments,
            variants = variantsForNew?.first,
            variantIndex = variantsForNew?.second,
        )
        val assistantId = newId()
        val assistantMsg = Message(id = assistantId, role = Role.ASSISTANT, content = "")

        updateSession(sessionId) { s ->
            s.copy(
                title = if (retitle) makeTitle(titleSource) else s.title,
                messages = priorMessages + userMsg + assistantMsg,
                updatedAt = System.currentTimeMillis(),
            )
        }
        publish(state.copy(streaming = true))
        persist()

        var localAssistant = assistantMsg
        fun patch(fn: (Message) -> Message) {
            localAssistant = fn(localAssistant)
            updateSession(sessionId) { s ->
                s.copy(messages = s.messages.map { if (it.id == assistantId) fn(it) else it })
            }
        }

        val request = ChatRequest(
            messages = (priorMessages + userMsg).map { ChatTurn(it.role.wire, it.content) },
            sessionId = sessionId,
            files = files,
            // при редактировании файлы уже лежат в песочнице чата — передаём только имена
            uploadedNames = if (files.isEmpty()) attachments.map { it.name } else emptyList(),
        )

        cancelable = client.chat.stream(request, object : ChatStreamListener {
            override fun onEvent(event: StreamEvent) {
                when (event) {
                    is StreamEvent.Text -> patch { m ->
                        val parts = m.parts.toMutableList()
                        val last = parts.lastOrNull()
                        if (last is Part.Text) parts[parts.lastIndex] = Part.Text(last.text + event.value)
                        else parts.add(Part.Text(event.value))
                        m.copy(parts = parts, content = m.content + event.value)
                    }
                    is StreamEvent.Tool -> patch { m ->
                        m.copy(
                            parts = m.parts + Part.Tool(
                                ToolCall(event.id, event.name, event.args, ToolStatus.RUNNING)
                            )
                        )
                    }
                    is StreamEvent.ToolResult -> patch { m ->
                        m.copy(parts = m.parts.map { p ->
                            if (p is Part.Tool && p.call.id == event.id) {
                                Part.Tool(
                                    p.call.copy(
                                        status = if (event.ok) ToolStatus.OK else ToolStatus.ERROR,
                                        output = event.output,
                                        ms = event.ms,
                                    )
                                )
                            } else p
                        })
                    }
                    is StreamEvent.ArtifactEvent -> patch { m -> m.copy(artifacts = m.artifacts + event.artifact) }
                    StreamEvent.Done -> Unit
                }
            }

            override fun onError(error: BzApiException) {
                patch { m ->
                    m.copy(
                        content = m.content.ifEmpty {
                            error.message?.takeIf { error.status == 401 }
                                ?: "Не удалось получить ответ. Попробуйте отправить сообщение ещё раз."
                        }
                    )
                }
            }

            override fun onFinished() {
                cancelable = null
                // «Зависшие» инструменты (стрим оборвался) переводим в ошибку.
                patch { m ->
                    m.copy(parts = m.parts.map { p ->
                        if (p is Part.Tool && p.call.status == ToolStatus.RUNNING) {
                            Part.Tool(p.call.copy(status = ToolStatus.ERROR))
                        } else p
                    })
                }
                publish(state.copy(streaming = false))
                persist()
            }
        })
    }

    /** Переключение между версиями отредактированного сообщения (‹ 1 / 2 ›). */
    fun switchVariant(messageId: String, dir: Int) {
        if (state.streaming) return
        val s = state.active ?: return
        val idx = s.messages.indexOfFirst { it.id == messageId }
        if (idx == -1) return
        val m = s.messages[idx]
        val vs = m.variants ?: return
        if (vs.size < 2) return
        val cur = m.variantIndex ?: 0
        val next = cur + dir
        if (next < 0 || next >= vs.size) return

        val variants = vs.toMutableList()
        variants[cur] = MessageVariant(m.content, s.messages.subList(idx + 1, s.messages.size))
        val target = variants[next]
        val switched = m.copy(content = target.content, variants = variants, variantIndex = next)
        val messages = s.messages.subList(0, idx) + switched + target.tail
        updateSession(s.id) { it.copy(messages = messages) }
        persist()
    }
}
