package ru.bleyzos.ai.design.widgets

import android.content.Context
import android.view.Gravity
import android.widget.TextView
import ru.bleyzos.ai.design.theme.BzColors
import ru.bleyzos.ai.design.theme.BzDrawables
import ru.bleyzos.ai.design.theme.BzFonts
import ru.bleyzos.ai.design.theme.dpi

/** Знак «B» — bleyzos-mark.tsx: тёмный скруглённый квадрат, Unbounded Black. */
class BzMark(context: Context, private val sizeDp: Int = 32, textSp: Float = 14f) : TextView(context) {
    init {
        text = "B"
        gravity = Gravity.CENTER
        includeFontPadding = false
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, textSp)
        setTextColor(BzColors.primaryForeground)
        typeface = BzFonts.display(context, 900)
        background = BzDrawables.rect(context, BzColors.primary, 10)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val s = dpi(sizeDp)
        super.onMeasure(
            android.view.View.MeasureSpec.makeMeasureSpec(s, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(s, android.view.View.MeasureSpec.EXACTLY),
        )
    }
}
