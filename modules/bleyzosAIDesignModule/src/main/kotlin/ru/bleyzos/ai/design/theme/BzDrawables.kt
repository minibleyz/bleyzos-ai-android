package ru.bleyzos.ai.design.theme

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View

/** Фабрики фонов: скруглённые прямоугольники, обводки, ripple, «тёплая» тень. */
object BzDrawables {

    fun rect(
        context: Context,
        fill: Int,
        radiusDp: Number = 0,
        strokeColor: Int = 0,
        strokeDp: Number = 0,
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = context.dp(radiusDp)
        if (strokeColor != 0 && strokeDp.toFloat() > 0f) setStroke(context.dpi(strokeDp).coerceAtLeast(1), strokeColor)
    }

    /** Разные радиусы углов: tl, tr, br, bl (dp). */
    fun corners(
        context: Context,
        fill: Int,
        tl: Number, tr: Number, br: Number, bl: Number,
        strokeColor: Int = 0,
        strokeDp: Number = 0,
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        val a = context.dp(tl); val b = context.dp(tr); val c = context.dp(br); val d = context.dp(bl)
        cornerRadii = floatArrayOf(a, a, b, b, c, c, d, d)
        if (strokeColor != 0 && strokeDp.toFloat() > 0f) setStroke(context.dpi(strokeDp).coerceAtLeast(1), strokeColor)
    }

    fun circle(fill: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fill)
    }

    /** Ripple в фирменном тёплом тоне вокруг [content] (может быть null — тогда «безфоновая» кнопка). */
    fun ripple(context: Context, content: Drawable?, radiusDp: Number, tint: Int = BzColors.alpha(BzColors.primary, 0.10f)): Drawable {
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.WHITE)
            cornerRadius = context.dp(radiusDp)
        }
        return RippleDrawable(ColorStateList.valueOf(tint), content, mask)
    }

    /** Ripple поверх светлого фона (для тёмных кнопок и блоков кода). */
    fun rippleLight(context: Context, content: Drawable?, radiusDp: Number): Drawable =
        ripple(context, content, radiusDp, BzColors.alpha(Color.WHITE, 0.14f))

    /**
     * shadow-warm: `0 1px 3px rgb(0 0 0/.04), 0 20px 50px rgb(90 60 20/.08)`.
     * На Android — elevation с тёплым цветом тени.
     */
    fun warmShadow(view: View, elevationDp: Number = 8) {
        view.elevation = view.dp(elevationDp)
        view.outlineAmbientShadowColor = BzColors.alpha(BzColors.shadowWarm, 0.10f)
        view.outlineSpotShadowColor = BzColors.alpha(BzColors.shadowWarm, 0.26f)
    }
}
