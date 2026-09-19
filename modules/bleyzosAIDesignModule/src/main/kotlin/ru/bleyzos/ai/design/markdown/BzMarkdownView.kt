package ru.bleyzos.ai.design.markdown

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import ru.bleyzos.ai.design.theme.BzColors
import ru.bleyzos.ai.design.theme.BzDrawables
import ru.bleyzos.ai.design.widgets.MATCH
import ru.bleyzos.ai.design.widgets.WRAP
import ru.bleyzos.ai.design.widgets.add
import ru.bleyzos.ai.design.widgets.bzText
import ru.bleyzos.ai.design.widgets.hbox
import ru.bleyzos.ai.design.widgets.linearLp
import ru.bleyzos.ai.design.widgets.size
import ru.bleyzos.ai.design.widgets.vbox

/**
 * Рендер «markdown-lite» из веб-версии: абзацы, списки `- `, `**жирный**`, `` `код` ``
 * и блоки ```код```. Обновляется инкрементально — при стриминге перерисовывается только
 * то, что изменилось (обычно последняя строка).
 */
class BzMarkdownView(context: Context) : LinearLayout(context) {

    private sealed class Block {
        data class Text(val text: String) : Block()
        data class Code(val lang: String, val code: String) : Block()
    }

    private class Line(val kind: Int, val root: View, val tv: TextView?) { var raw = "" }
    private class TextBlock(val root: LinearLayout) { val lines = ArrayList<Line>() }

    private val views = ArrayList<Any>() // TextBlock | BzCodeBlockView — параллельно блокам
    private var content = ""
    private val wide = context.resources.configuration.screenWidthDp >= 640
    private val bodySp = if (wide) 15f else 14f

    private var caretEnabled = false
    private var caretHost: Line? = null
    private val caret = CaretSpan(context)
    private val handler = Handler(Looper.getMainLooper())
    private val blink = object : Runnable {
        override fun run() {
            caret.visible = !caret.visible
            caretHost?.tv?.invalidate()
            handler.postDelayed(this, 500)
        }
    }

    init {
        orientation = VERTICAL
    }

    fun setContent(text: String) {
        if (text == content && views.isNotEmpty()) return
        content = text
        val blocks = parse(text)
        while (views.size > blocks.size) {
            removeViewAt(views.size - 1)
            views.removeAt(views.size - 1)
        }
        for (i in blocks.indices) {
            when (val b = blocks[i]) {
                is Block.Text -> {
                    val ex = views.getOrNull(i)
                    val tb = if (ex is TextBlock) ex else {
                        val created = TextBlock(context.vbox())
                        put(i, created, created.root, isCode = false)
                        created
                    }
                    updateText(tb, b.text)
                }
                is Block.Code -> {
                    val ex = views.getOrNull(i)
                    val cb = if (ex is BzCodeBlockView) ex else {
                        val created = BzCodeBlockView(context)
                        put(i, created, created, isCode = true)
                        created
                    }
                    cb.setCode(b.lang, b.code)
                }
            }
        }
        refreshCaret()
    }

    /** Включает мигающую каретку в конце последнего текстового блока (пока идёт стрим). */
    fun setCaret(enabled: Boolean) {
        if (caretEnabled == enabled) return
        caretEnabled = enabled
        refreshCaret()
    }

    private fun put(index: Int, holder: Any, view: View, isCode: Boolean) {
        val lp = view.linearLp(
            MATCH, WRAP,
            mt = (if (index > 0) 6 else 0) + (if (isCode) 12 else 0),
            mb = if (isCode) 12 else 0,
        )
        if (index < views.size) {
            removeViewAt(index)
            views[index] = holder
            addView(view, index, lp)
        } else {
            views.add(holder)
            addView(view, lp)
        }
    }

    /* ─────────────────────────── разбор ─────────────────────────── */

    private fun parse(text: String): List<Block> {
        val out = ArrayList<Block>()
        var last = 0
        for (m in FENCE.findAll(text)) {
            val before = text.substring(last, m.range.first)
            if (before.isNotEmpty()) out.add(Block.Text(before))
            out.add(Block.Code(m.groupValues[1], m.groupValues[2].removeSuffix("\n")))
            last = m.range.last + 1
        }
        val rest = text.substring(last)
        val open = rest.indexOf("```")
        if (open >= 0) {
            // Незакрытый блок (идёт стрим) — сразу показываем как код, а не сырые бэктики.
            val before = rest.substring(0, open)
            if (before.isNotEmpty()) out.add(Block.Text(before))
            val tail = rest.substring(open + 3)
            val lang = Regex("^\\w*").find(tail)?.value.orEmpty()
            out.add(Block.Code(lang, tail.substring(lang.length).removePrefix("\n").removeSuffix("\n")))
        } else if (rest.isNotEmpty()) {
            out.add(Block.Text(rest))
        }
        return out
    }

    /* ─────────────────────────── текстовые блоки ─────────────────────────── */

    private fun kindOf(raw: String): Int {
        val t = raw.trim()
        return when {
            t.isEmpty() -> SPACER
            t.startsWith("- ") -> BULLET
            else -> PARA
        }
    }

    private fun updateText(tb: TextBlock, text: String) {
        val lines = text.split("\n")
        while (tb.lines.size > lines.size) {
            val l = tb.lines.removeAt(tb.lines.size - 1)
            tb.root.removeView(l.root)
            if (caretHost === l) caretHost = null
        }
        for (j in lines.indices) {
            val raw = lines[j]
            val kind = kindOf(raw)
            val ex = tb.lines.getOrNull(j)
            if (ex != null && ex.kind == kind) {
                if (ex.raw != raw) {
                    ex.raw = raw
                    bindLine(ex)
                }
                continue
            }
            val line = createLine(kind)
            line.raw = raw
            bindLine(line)
            val lp = line.root.linearLp(MATCH, if (kind == SPACER) line.root.size(8) else WRAP, mt = if (j > 0) 6 else 0)
            if (ex != null) {
                tb.root.removeViewAt(j)
                if (caretHost === ex) caretHost = null
                tb.lines[j] = line
                tb.root.addView(line.root, j, lp)
            } else {
                tb.lines.add(line)
                tb.root.addView(line.root, lp)
            }
        }
    }

    private fun createLine(kind: Int): Line = when (kind) {
        SPACER -> Line(kind, View(context), null)
        BULLET -> {
            val row = context.hbox(centerVertical = false)
            val dot = View(context).apply { background = BzDrawables.circle(BzColors.brand) }
            row.add(dot, size(6), size(6), mt = (bodySp * 0.6f).toInt(), mr = 10)
            val tv = paragraph()
            row.add(tv, weight = 1f, w = 0)
            Line(kind, row, tv)
        }
        else -> {
            val tv = paragraph()
            Line(kind, tv, tv)
        }
    }

    private fun paragraph(): TextView = context.bzText("", bodySp, BzColors.foreground, lineHeightMult = 1.625f)

    private fun bindLine(line: Line) {
        val tv = line.tv ?: return
        val body = if (line.kind == BULLET) line.raw.trim().substring(2) else line.raw
        tv.text = BzInline.render(context, body)
    }

    /* ─────────────────────────── каретка ─────────────────────────── */

    private fun lastTextLine(): Line? {
        val last = views.lastOrNull() as? TextBlock ?: return null
        return last.lines.lastOrNull { it.tv != null && it.raw.isNotBlank() }
    }

    private fun refreshCaret() {
        val old = caretHost
        val target = if (caretEnabled) lastTextLine() else null
        if (old != null && old !== target) {
            old.tv?.let { it.text = BzInline.render(context, if (old.kind == BULLET) old.raw.trim().substring(2) else old.raw) }
        }
        caretHost = target
        if (target != null) {
            val body = if (target.kind == BULLET) target.raw.trim().substring(2) else target.raw
            target.tv?.text = BzInline.render(context, body, caret)
            startBlink()
        } else {
            stopBlink()
        }
    }

    private fun startBlink() {
        handler.removeCallbacks(blink)
        caret.visible = true
        handler.postDelayed(blink, 500)
    }

    private fun stopBlink() = handler.removeCallbacks(blink)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (caretHost != null) startBlink()
    }

    override fun onDetachedFromWindow() {
        stopBlink()
        super.onDetachedFromWindow()
    }

    private companion object {
        val FENCE = Regex("```(\\w*)\\n?([\\s\\S]*?)```")
        const val SPACER = 0
        const val BULLET = 1
        const val PARA = 2
    }

}
