package ru.bleyzos.ai.design.widgets

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import ru.bleyzos.ai.design.theme.BzColors
import ru.bleyzos.ai.design.theme.BzDrawables
import ru.bleyzos.ai.design.theme.dpi

/**
 * Выезжающая слева панель со скримом — мобильный сайдбар (md:hidden в вебе).
 * Занимает весь родитель; когда закрыта — не перехватывает касания.
 */
class BzDrawer(context: Context, private val panel: View, private val panelWidthDp: Int) : FrameLayout(context) {

    private val scrim = View(context).apply { setBackgroundColor(Color.BLACK); alpha = 0f }
    private var progress = 0f // 0 закрыта … 1 открыта
    private var animator: ValueAnimator? = null

    var onClosed: (() -> Unit)? = null

    val isOpen: Boolean get() = progress > 0f && targetOpen
    private var targetOpen = false

    init {
        visibility = GONE
        addView(scrim, LayoutParams(MATCH, MATCH))
        scrim.setOnClickListener { close() }
        addView(panel, LayoutParams(dpi(panelWidthDp), MATCH))
        BzDrawables.warmShadow(panel, 16)
        applyProgress()
    }

    private fun applyProgress() {
        scrim.alpha = 0.30f * progress
        panel.translationX = -dpi(panelWidthDp) * (1f - progress)
    }

    fun open() = animateTo(true)
    fun close() = animateTo(false)

    private fun animateTo(open: Boolean) {
        targetOpen = open
        animator?.cancel()
        if (open) visibility = VISIBLE
        animator = ValueAnimator.ofFloat(progress, if (open) 1f else 0f).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
            addUpdateListener { progress = it.animatedValue as Float; applyProgress() }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) {
                    if (!targetOpen) {
                        visibility = GONE
                        onClosed?.invoke()
                    }
                }
            })
            start()
        }
    }

    /** Мгновенно закрыть (при переходе на широкий экран). */
    fun closeImmediately() {
        animator?.cancel()
        targetOpen = false
        progress = 0f
        applyProgress()
        visibility = GONE
    }

}
