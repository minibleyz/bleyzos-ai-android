package ru.bleyzos.ai.design.chat

/** События, которые сообщения отдают наверх (экран чата связывает их с ChatEngine). */
interface BzMessageCallbacks {
    fun onEditMessage(messageId: String, newText: String)
    fun onSwitchVariant(messageId: String, dir: Int)
    fun onOpenArtifact(messageId: String, index: Int)
}
