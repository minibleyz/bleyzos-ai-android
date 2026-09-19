package ru.bleyzos.ai.design.icons

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import android.view.animation.LinearInterpolator
import ru.bleyzos.ai.design.theme.BzColors
import ru.bleyzos.ai.design.theme.dpi

/** Иконка Lucide (сетка 24×24, обводка 2, скруглённые концы), рисуется прямо на Canvas. */
class BzIconView(context: Context, icon: BzIcon? = null, sizeDp: Number = 16, tint: Int = BzColors.foreground) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 2f
    }
    private var paths: List<Path> = emptyList()
    private var spinner: ObjectAnimator? = null
    private val sizePx = dpi(sizeDp)

    var icon: BzIcon? = null
        set(value) {
            field = value
            paths = if (value == null) emptyList() else pathsFor(value)
            invalidate()
        }

    var tint: Int = tint
        set(value) {
            field = value
            paint.color = value
            invalidate()
        }

    /** Заливка (для «стоп»-квадрата: fill-current). */
    var filled: Boolean = false
        set(value) {
            field = value
            paint.style = if (value) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
            invalidate()
        }

    /** Бесконечное вращение — для loader (animate-spin). */
    var spinning: Boolean = false
        set(value) {
            field = value
            if (value) startSpin() else stopSpin()
        }

    init {
        paint.color = tint
        this.icon = icon
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(resolveSize(sizePx, widthMeasureSpec), resolveSize(sizePx, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val s = minOf(width, height) / 24f
        canvas.save()
        canvas.translate((width - 24f * s) / 2f, (height - 24f * s) / 2f)
        canvas.scale(s, s)
        for (p in paths) canvas.drawPath(p, paint)
        canvas.restore()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (spinning) startSpin()
    }

    override fun onDetachedFromWindow() {
        stopSpin()
        super.onDetachedFromWindow()
    }

    private fun startSpin() {
        if (spinner != null || !isAttachedToWindow) return
        spinner = ObjectAnimator.ofFloat(this, ROTATION, 0f, 360f).apply {
            duration = 1000
            interpolator = LinearInterpolator()
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }

    private fun stopSpin() {
        spinner?.cancel()
        spinner = null
        rotation = 0f
    }

    private companion object {
        private val cache = HashMap<String, List<Path>>()

        fun pathsFor(icon: BzIcon): List<Path> = cache.getOrPut(icon.key) {
            (BzIconData.paths[icon.key] ?: emptyArray()).map { SvgPath.parse(it) }
        }
    }
}
