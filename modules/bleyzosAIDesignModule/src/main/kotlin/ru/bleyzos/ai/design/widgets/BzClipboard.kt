package ru.bleyzos.ai.design.widgets

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

object BzClipboard {
    /** @return true, если текст скопирован. */
    fun copy(context: Context, text: String): Boolean = try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Bleyzos AI", text))
        true
    } catch (_: Exception) {
        false
    }
}
