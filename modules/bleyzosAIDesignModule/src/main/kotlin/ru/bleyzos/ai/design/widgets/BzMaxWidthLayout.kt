package ru.bleyzos.ai.design.widgets

import android.content.Context
import android.widget.FrameLayout

/**
 * Ограничение ширины содержимого: долей от родителя ([fraction], как max-w-[85%])
 * и/или в dp ([maxDp], как max-w-3xl). [fill] — растянуть до максимума (w-full).
 */
class BzMaxWidthLayout(
    context: Context,
    private val fraction: Float = 1f,
    private val maxDp: Int = Int.MAX_VALUE,
    private val fill: Boolean = false,
) : FrameLayout(context) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val mode = MeasureSpec.getMode(widthMeasureSpec)
        val avail = MeasureSpec.getSize(widthMeasureSpec)
        if (mode == MeasureSpec.UNSPECIFIED) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        val byDp = if (maxDp == Int.MAX_VALUE) Int.MAX_VALUE else (maxDp * resources.displayMetrics.density).toInt()
        val limit = minOf((avail * fraction).toInt(), byDp, avail)
        val spec = MeasureSpec.makeMeasureSpec(limit, if (fill) MeasureSpec.EXACTLY else MeasureSpec.AT_MOST)
        super.onMeasure(spec, heightMeasureSpec)
    }
}
