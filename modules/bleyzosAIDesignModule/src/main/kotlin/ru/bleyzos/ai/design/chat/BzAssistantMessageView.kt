package ru.bleyzos.ai.design.chat

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import ru.bleyzos.ai.api.model.Artifact
import ru.bleyzos.ai.api.model.ArtifactKind
import ru.bleyzos.ai.api.model.Message
import ru.bleyzos.ai.api.model.Part
import ru.bleyzos.ai.design.icons.BzIcon
import ru.bleyzos.ai.design.icons.BzIconView
import ru.bleyzos.ai.design.markdown.BzMarkdownView
import ru.bleyzos.ai.design.theme.BzColors
import ru.bleyzos.ai.design.theme.BzDrawables
import ru.bleyzos.ai.design.widgets.BzClipboard
import ru.bleyzos.ai.design.widgets.BzMark
import ru.bleyzos.ai.design.widgets.BzMaxWidthLayout
import ru.bleyzos.ai.design.widgets.BzThinkingDots
import ru.bleyzos.ai.design.widgets.MATCH
import ru.bleyzos.ai.design.widgets.WRAP
import ru.bleyzos.ai.design.widgets.add
import ru.bleyzos.ai.design.widgets.bzText
import ru.bleyzos.ai.design.widgets.hbox
import ru.bleyzos.ai.design.widgets.linearLp
import ru.bleyzos.ai.design.widgets.pad
import ru.bleyzos.ai.design.widgets.padSym
import ru.bleyzos.ai.design.widgets.size
import ru.bleyzos.ai.design.widgets.vbox

internal fun artifactIcon(kind: ArtifactKind): BzIcon = when (kind) {
    ArtifactKind.CODE -> BzIcon.FILE_CODE
    ArtifactKind.HTML -> BzIcon.GLOBE
    ArtifactKind.MARKDOWN -> BzIcon.FILE_TEXT
    ArtifactKind.IMAGE -> BzIcon.IMAGE
    ArtifactKind.ZIP -> BzIcon.FILE_ARCHIVE
}

internal fun artifactLabel(kind: ArtifactKind): String = when (kind) {
    ArtifactKind.CODE -> "Код"
    ArtifactKind.HTML -> "HTML-предпросмотр"
    ArtifactKind.MARKDOWN -> "Markdown"
    ArtifactKind.IMAGE -> "Изображение"
    ArtifactKind.ZIP -> "ZIP-архив"
}

/** Ответ ассистента: знак, имя, текст (markdown), вызовы инструментов, карточки артефактов. */
class BzAssistantMessageView(context: Context, private val cb: BzMessageCallbacks) : LinearLayout(context) {

    private val typing: TextView
    private val partsBox: LinearLayout
    private val artifactsBox: LinearLayout
    private val copyRow: LinearLayout
    private val copyIcon: BzIconView
    private val copyLabel: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var bound: Message? = null
    private var boundStreaming: Boolean? = null
    private var lastArtifacts: List<Artifact>? = null
    private var messageId = ""
    private var content = ""

    init {
        orientation = HORIZONTAL
        add(BzMark(context, 32, 14f), size(32), size(32), mt = 2, mr = 12)

        val col = context.vbox()
        val head = context.hbox()
        head.add(context.bzText("Bleyzos AI", 13f, BzColors.primary, 700, display = true))
        typing = context.bzText("печатает…", 12f, BzColors.brand)
        typing.visibility = GONE
        head.add(typing, ml = 8)
        col.add(head, mb = 4)

        partsBox = context.vbox()
        col.add(partsBox, MATCH)
        artifactsBox = context.vbox()
        col.add(artifactsBox, MATCH, mt = 12)

        copyRow = context.hbox().apply {
            padSym(8, 4)
            background = BzDrawables.ripple(context, null, 6)
            isClickable = true
            contentDescription = "Скопировать ответ"
            visibility = GONE
            setOnClickListener {
                if (BzClipboard.copy(context, content)) {
                    copyIcon.icon = BzIcon.CHECK; copyLabel.text = "Скопировано"
                    handler.postDelayed({ copyIcon.icon = BzIcon.COPY; copyLabel.text = "Копировать" }, 1500)
                }
            }
        }
        copyIcon = BzIconView(context, BzIcon.COPY, 14, BzColors.mutedForeground)
        copyLabel = context.bzText("Копировать", 12f, BzColors.mutedForeground)
        copyRow.add(copyIcon, size(14), size(14), mr = 6)
        copyRow.add(copyLabel)
        col.add(copyRow, mt = 8)

        add(col, weight = 1f, w = 0)
    }

    fun bind(m: Message, streaming: Boolean) {
        if (bound === m && boundStreaming == streaming) return
        bound = m
        boundStreaming = streaming
        messageId = m.id
        content = m.content

        typing.visibility = if (streaming) VISIBLE else GONE
        bindParts(m, streaming)

        if (lastArtifacts !== m.artifacts) {
            lastArtifacts = m.artifacts
            artifactsBox.removeAllViews()
            m.artifacts.forEachIndexed { idx, a -> artifactsBox.add(artifactCard(a, idx), MATCH, mt = if (idx > 0) 8 else 0) }
            artifactsBox.visibility = if (m.artifacts.isEmpty()) GONE else VISIBLE
        }
        copyRow.visibility = if (!streaming && m.content.isNotEmpty()) VISIBLE else GONE
    }

    private fun bindParts(m: Message, streaming: Boolean) {
        val parts = m.parts
        if (parts.isEmpty()) {
            // запасной путь веба: нет parts → показываем content целиком
            while (partsBox.childCount > 1) partsBox.removeViewAt(partsBox.childCount - 1)
            if (m.content.isEmpty()) { partsBox.removeAllViews(); return }
            val md = (partsBox.getChildAt(0) as? BzMarkdownView) ?: BzMarkdownView(context).also {
                partsBox.removeAllViews(); partsBox.addView(it, it.linearLp(MATCH, WRAP))
            }
            md.setContent(m.content)
            md.setCaret(streaming)
            return
        }
        val lastTextIdx = parts.indexOfLast { it is Part.Text }
        while (partsBox.childCount > parts.size) partsBox.removeViewAt(partsBox.childCount - 1)
        parts.forEachIndexed { i, part ->
            val ex = partsBox.getChildAt(i)
            val lp = { v: View -> v.linearLp(MATCH, WRAP, mt = if (i > 0) 10 else 0) }
            when (part) {
                is Part.Text -> {
                    val md = if (ex is BzMarkdownView) ex else BzMarkdownView(context).also {
                        if (ex != null) partsBox.removeViewAt(i)
                        partsBox.addView(it, i, lp(it))
                    }
                    md.setContent(part.text)
                    md.setCaret(streaming && i == lastTextIdx && part.text.isNotEmpty())
                }
                is Part.Tool -> {
                    val row = if (ex is BzToolRow && ex.tag == part.call.id) ex else BzToolRow(context).also {
                        if (ex != null) partsBox.removeViewAt(i)
                        it.tag = part.call.id
                        partsBox.addView(it, i, lp(it))
                    }
                    row.bind(part.call)
                }
            }
        }
    }

    private fun artifactCard(a: Artifact, idx: Int): View {
        val card = context.hbox().apply {
            background = BzDrawables.ripple(context, BzDrawables.rect(context, BzColors.card, 14, BzColors.border, 1), 14)
            padSym(14, 12)
            isClickable = true
            setOnClickListener { cb.onOpenArtifact(messageId, idx) }
        }
        val box = context.hbox().apply {
            gravity = Gravity.CENTER
            background = BzDrawables.rect(context, BzColors.background, 10, BzColors.input, 1)
        }
        box.addView(BzIconView(context, artifactIcon(a.kind), 18, BzColors.brand), LayoutParams(size(18), size(18)))
        card.add(box, size(36), size(36), mr = 12)
        val text = context.vbox()
        text.add(context.bzText(a.name, 13.5f, BzColors.primary, 600, singleLine = true))
        text.add(context.bzText("${artifactLabel(a.kind)} · открыть в панели", 11.5f, BzColors.mutedForeground))
        card.add(text, weight = 1f, w = 0)
        card.add(BzIconView(context, BzIcon.EXTERNAL_LINK, 16, BzColors.mutedForeground), size(16), size(16), ml = 12)
        return BzMaxWidthLayout(context, maxDp = 448).apply { addView(card, LayoutParams(MATCH, WRAP)) }
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }
}

/** «Ассистент думает»: знак и три прыгающие точки, пока нет первого куска ответа. */
class BzThinkingBubble(context: Context) : LinearLayout(context) {
    init {
        orientation = HORIZONTAL
        add(BzMark(context, 32, 14f), size(32), size(32), mt = 2, mr = 12)
        val bubble = context.hbox().apply {
            background = BzDrawables.corners(context, BzColors.card, 6, 16, 16, 16, BzColors.border, 1)
            pad(16, 14, 16, 14)
        }
        bubble.add(BzThinkingDots(context))
        add(bubble)
    }
}
