package ru.bleyzos.ai.design.widgets

import android.view.MotionEvent
import android.view.View

/** hover:opacity-85 из веба → лёгкое затемнение при нажатии. */
fun View.pressAlpha(pressed: Float = 0.85f) {
    setOnTouchListener { v, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> v.alpha = if (v.isEnabled) pressed else v.alpha
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.alpha = if (v.isEnabled) 1f else v.alpha
        }
        false
    }
}
