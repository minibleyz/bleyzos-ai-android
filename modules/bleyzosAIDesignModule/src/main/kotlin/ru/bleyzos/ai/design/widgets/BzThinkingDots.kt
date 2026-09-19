package ru.bleyzos.ai.design.widgets

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.view.animation.LinearInterpolator
import ru.bleyzos.ai.design.theme.BzColors
import ru.bleyzos.ai.design.theme.dp
import ru.bleyzos.ai.design.theme.dpi

/**
 * Три прыгающие точки (animate-dot: 1.2s, сдвиг −4px, прозрачность 0.4→1, задержки 0/.15/.3 s).
 */
class BzThinkingDots(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BzColors.brand }
    private var t = 0f // 0..1 — фаза цикла
    private var animator: ValueAnimator? = null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(dpi(8 * 3 + 6 * 2), dpi(8 + 4))
    }

    override fun onDraw(canvas: Canvas) {
        val r = dp(4)
        for (i in 0 until 3) {
            val phase = ((t - i * 0.15f / 1.2f) % 1f + 1f) % 1f
            // keyframes: 0%,60%,100% → покой; 30% → пик
            val k = when {
                phase < 0.30f -> phase / 0.30f
                phase < 0.60f -> 1f - (phase - 0.30f) / 0.30f
                else -> 0f
            }
            paint.alpha = ((0.4f + 0.6f * k) * 255).toInt()
            val cx = r + i * (dp(8) + dp(6))
            val cy = height - r - dp(0) - k * dp(4)
            canvas.drawCircle(cx, cy, r, paint)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1200
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { t = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }
}
