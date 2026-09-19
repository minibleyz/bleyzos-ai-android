package ru.bleyzos.ai.design.theme

import android.content.Context
import android.util.TypedValue
import android.view.View

/** dp/sp-хелперы. Все размеры в дизайн-модуле задаются в dp, как px в Tailwind-версии. */
fun Context.dp(v: Number): Float = v.toFloat() * resources.displayMetrics.density
fun Context.dpi(v: Number): Int = Math.round(dp(v))
fun View.dp(v: Number): Float = context.dp(v)
fun View.dpi(v: Number): Int = context.dpi(v)
fun Context.sp(v: Number): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v.toFloat(), resources.displayMetrics)

/** Размеры и радиусы из tailwind.config.ts / globals.css. */
object BzDims {
    /** --radius: 0.75rem */
    const val RADIUS_LG = 12
    const val RADIUS_MD = 10
    const val RADIUS_CARD = 18
    const val RADIUS_INPUT = 16
    const val SIDEBAR_WIDTH = 288      // w-72
    const val CONTENT_MAX_WIDTH = 768  // max-w-3xl
    const val WIDE_BREAKPOINT = 768    // md: — сайдбар постоянный
    const val SUGGESTION_2COL = 640    // sm:
    const val HEADER_HEIGHT = 64       // h-16
}
