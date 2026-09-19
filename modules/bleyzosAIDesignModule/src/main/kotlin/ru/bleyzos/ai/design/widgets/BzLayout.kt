package ru.bleyzos.ai.design.widgets

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import ru.bleyzos.ai.design.theme.BzColors
import ru.bleyzos.ai.design.theme.BzFonts
import ru.bleyzos.ai.design.theme.dpi

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/** Вертикальный/горизонтальный LinearLayout. */
fun Context.vbox(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
fun Context.hbox(centerVertical: Boolean = true): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    if (centerVertical) gravity = Gravity.CENTER_VERTICAL
}

/** Отступы в dp. */
fun View.pad(l: Number = 0, t: Number = 0, r: Number = 0, b: Number = 0) {
    setPadding(dpi(l), dpi(t), dpi(r), dpi(b))
}

fun View.padSym(h: Number = 0, v: Number = 0) = pad(h, v, h, v)

/** LinearLayout.LayoutParams в dp: размеры (MATCH/WRAP или dp), вес, margin, gravity. */
fun View.linearLp(
    w: Int = WRAP, h: Int = WRAP, weight: Float = 0f,
    ml: Number = 0, mt: Number = 0, mr: Number = 0, mb: Number = 0,
    gravity: Int = -1,
): LinearLayout.LayoutParams = LinearLayout.LayoutParams(w, h, weight).apply {
    setMargins(dpi(ml), dpi(mt), dpi(mr), dpi(mb))
    if (gravity != -1) this.gravity = gravity
}

fun View.frameLp(
    w: Int = WRAP, h: Int = WRAP, gravity: Int = Gravity.NO_GRAVITY,
    ml: Number = 0, mt: Number = 0, mr: Number = 0, mb: Number = 0,
): FrameLayout.LayoutParams = FrameLayout.LayoutParams(w, h, gravity).apply {
    setMargins(dpi(ml), dpi(mt), dpi(mr), dpi(mb))
}

/** Добавить в LinearLayout с параметрами в dp; размеры — dp-числа >= 0 или MATCH/WRAP (<0). */
fun LinearLayout.add(
    v: View, w: Int = WRAP, h: Int = WRAP, weight: Float = 0f,
    ml: Number = 0, mt: Number = 0, mr: Number = 0, mb: Number = 0,
    gravity: Int = -1,
): View {
    addView(v, v.linearLp(w, h, weight, ml, mt, mr, mb, gravity))
    return v
}

/** dp → px для размеров LayoutParams; отрицательные (MATCH/WRAP) остаются как есть. */
fun View.size(dp: Int): Int = if (dp < 0) dp else dpi(dp)

/**
 * Единая фабрика TextView в стиле сайта. [sizeSp] — px из Tailwind-версии.
 * [weight]: 400/500/600/700; [display] — шрифт Unbounded.
 */
fun Context.bzText(
    text: CharSequence = "",
    sizeSp: Float = 14f,
    color: Int = BzColors.foreground,
    weight: Int = 400,
    display: Boolean = false,
    mono: Boolean = false,
    letterSpacingEm: Float = 0f,
    lineHeightMult: Float = 1.0f,
    singleLine: Boolean = false,
): TextView = TextView(this).apply {
    this.text = text
    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sizeSp)
    setTextColor(color)
    typeface = when {
        mono -> BzFonts.mono
        display -> BzFonts.display(context, weight)
        else -> BzFonts.sans(context, weight)
    }
    includeFontPadding = false
    if (letterSpacingEm != 0f) letterSpacing = letterSpacingEm
    if (lineHeightMult != 1.0f) setLineSpacing(0f, lineHeightMult)
    if (singleLine) {
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
}

fun TextView.style(typeface: Typeface) { this.typeface = typeface }
