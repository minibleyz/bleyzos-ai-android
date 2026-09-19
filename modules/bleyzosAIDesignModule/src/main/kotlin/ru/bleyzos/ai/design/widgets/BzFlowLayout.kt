package ru.bleyzos.ai.design.widgets

import android.content.Context
import android.view.View
import android.view.ViewGroup
import ru.bleyzos.ai.design.theme.dpi

/** Раскладка «flex-wrap»: дети идут в строку и переносятся; [endAligned] — прижать к правому краю. */
class BzFlowLayout(context: Context, private val gapDp: Int = 6, private val endAligned: Boolean = false) : ViewGroup(context) {

    private class Row(val start: Int, var end: Int, var width: Int, var height: Int)
    private val rows = ArrayList<Row>()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxW = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val gap = dpi(gapDp)
        rows.clear()
        var rowStart = 0; var rowW = 0; var rowH = 0
        var contentW = 0; var totalH = 0
        val childSpecW = MeasureSpec.makeMeasureSpec(maxW.coerceAtLeast(0), MeasureSpec.AT_MOST)
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == View.GONE) continue
            c.measure(childSpecW, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            val cw = c.measuredWidth; val ch = c.measuredHeight
            val need = if (rowW == 0) cw else rowW + gap + cw
            if (need > maxW && rowW > 0) {
                rows.add(Row(rowStart, i, rowW, rowH))
                totalH += rowH + gap
                contentW = maxOf(contentW, rowW)
                rowStart = i; rowW = cw; rowH = ch
            } else {
                rowW = need; rowH = maxOf(rowH, ch)
            }
        }
        if (childCount > 0 && rowW > 0) {
            rows.add(Row(rowStart, childCount, rowW, rowH))
            totalH += rowH
            contentW = maxOf(contentW, rowW)
        }
        val w = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) MeasureSpec.getSize(widthMeasureSpec)
        else contentW + paddingLeft + paddingRight
        setMeasuredDimension(w, totalH + paddingTop + paddingBottom)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val gap = dpi(gapDp)
        var y = paddingTop
        val inner = r - l - paddingLeft - paddingRight
        for (row in rows) {
            var x = if (endAligned) paddingLeft + inner - row.width else paddingLeft
            for (i in row.start until row.end) {
                val c = getChildAt(i)
                if (c.visibility == View.GONE) continue
                val top = y + (row.height - c.measuredHeight) / 2
                c.layout(x, top, x + c.measuredWidth, top + c.measuredHeight)
                x += c.measuredWidth + gap
            }
            y += row.height + gap
        }
    }
}
