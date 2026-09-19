package ru.bleyzos.ai.design.markdown

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.ReplacementSpan
import android.text.style.TypefaceSpan
import ru.bleyzos.ai.design.theme.BzColors
import ru.bleyzos.ai.design.theme.BzFonts
import ru.bleyzos.ai.design.theme.dp

/** `код` в тексте: скруглённая «таблетка» bg-muted, mono, 0.85em (markdown-lite.tsx). */
internal class InlineCodeSpan(private val context: Context) : ReplacementSpan() {
    private val padPx = context.dp(6)
    private val radiusPx = context.dp(6)
    private val rect = RectF()

    private fun codePaint(base: Paint): Paint = Paint(base).apply {
        typeface = BzFonts.mono
        textSize = base.textSize * 0.85f
        color = BzColors.foreground
    }

    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int =
        (codePaint(paint).measureText(text, start, end) + padPx * 2).toInt()

    override fun draw(
        canvas: Canvas, text: CharSequence, start: Int, end: Int,
        x: Float, top: Int, y: Int, bottom: Int, paint: Paint,
    ) {
        val cp = codePaint(paint)
        val fm = paint.fontMetrics
        val w = cp.measureText(text, start, end) + padPx * 2
        rect.set(x, y + fm.ascent - context.dp(1), x + w, y + fm.descent + context.dp(1))
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BzColors.background }
        canvas.drawRoundRect(rect, radiusPx, radiusPx, bg)
        canvas.drawText(text, start, end, x + padPx, y.toFloat(), cp)
    }
}

/** Каретка стриминга (caret-blink): 2dp-полоска brand-цвета, мигает шагом 1 с. */
internal class CaretSpan(private val context: Context) : ReplacementSpan() {
    var visible = true
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BzColors.brand }

    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int =
        context.dp(4).toInt()

    override fun draw(
        canvas: Canvas, text: CharSequence, start: Int, end: Int,
        x: Float, top: Int, y: Int, bottom: Int, paint: Paint,
    ) {
        if (!visible) return
        val fm = paint.fontMetrics
        canvas.drawRect(x + context.dp(2), y + fm.ascent, x + context.dp(4), y + fm.descent, this.paint)
    }
}

/** Разбор инлайн-разметки `**жирный**` и `` `код` `` — то же, что renderInline() в вебе. */
internal object BzInline {
    private val TOKEN = Regex("(\\*\\*[^*]+\\*\\*|`[^`]+`)")

    fun render(context: Context, text: String, caret: CaretSpan? = null): SpannableStringBuilder {
        val out = SpannableStringBuilder()
        var last = 0
        for (m in TOKEN.findAll(text)) {
            if (m.range.first > last) out.append(text, last, m.range.first)
            val token = m.value
            if (token.startsWith("**")) {
                val inner = token.substring(2, token.length - 2)
                val s = out.length
                out.append(inner)
                out.setSpan(TypefaceSpan(BzFonts.sans(context, 700)), s, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else {
                val inner = token.substring(1, token.length - 1)
                val s = out.length
                out.append(inner)
                if (inner.length <= 36) {
                    out.setSpan(InlineCodeSpan(context), s, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                } else {
                    // длинный код должен переноситься по строкам — ReplacementSpan этого не умеет
                    out.setSpan(TypefaceSpan(BzFonts.mono), s, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    out.setSpan(BackgroundColorSpan(BzColors.background), s, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    out.setSpan(ForegroundColorSpan(BzColors.foreground), s, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            last = m.range.last + 1
        }
        if (last < text.length) out.append(text, last, text.length)
        if (caret != null) {
            val s = out.length
            out.append('\u200B')
            out.setSpan(caret, s, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return out
    }
}
