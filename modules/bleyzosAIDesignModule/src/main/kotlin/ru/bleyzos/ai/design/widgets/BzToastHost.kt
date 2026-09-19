package ru.bleyzos.ai.design.widgets

import android.content.Context
import android.view.Gravity
import android.widget.FrameLayout
import ru.bleyzos.ai.design.theme.BzColors
import ru.bleyzos.ai.design.theme.BzDrawables
import ru.bleyzos.ai.design.theme.dpi

/** Оверлей для коротких уведомлений («Сохранено в Загрузки»). Не перехватывает касания. */
class BzToastHost(context: Context) : FrameLayout(context) {
    private val hide = Runnable { pill?.animate()?.alpha(0f)?.setDuration(200)?.withEndAction { removePill() }?.start() }
    private var pill: android.view.View? = null

    init {
        isClickable = false
        isFocusable = false
    }

    override fun onInterceptTouchEvent(ev: android.view.MotionEvent?) = false
    override fun onTouchEvent(event: android.view.MotionEvent?) = false

    fun show(text: String) {
        removeCallbacks(hide)
        removePill()
        val v = context.bzText(text, 13.5f, BzColors.primaryForeground, 500).apply {
            background = BzDrawables.rect(context, BzColors.primary, 12)
            padSym(16, 10)
            alpha = 0f
            translationY = dp(8)
            elevation = dp(6)
        }
        pill = v
        addView(v, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dpi(96)
            leftMargin = dpi(24); rightMargin = dpi(24)
        })
        v.animate().alpha(1f).translationY(0f).setDuration(200).start()
        postDelayed(hide, 2400)
    }

    private fun removePill() {
        pill?.let { removeView(it) }
        pill = null
    }

    private fun android.view.View.dp(v: Number) = context.resources.displayMetrics.density * v.toFloat()
}
