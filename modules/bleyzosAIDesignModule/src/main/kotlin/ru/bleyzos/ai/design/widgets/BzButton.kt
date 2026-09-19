package ru.bleyzos.ai.design.widgets

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import ru.bleyzos.ai.design.icons.BzIcon
import ru.bleyzos.ai.design.icons.BzIconView
import ru.bleyzos.ai.design.theme.BzColors
import ru.bleyzos.ai.design.theme.BzDrawables

enum class BzButtonStyle { PRIMARY, OUTLINE, GHOST, DESTRUCTIVE, SOFT_BRAND }

/** Текстовая кнопка с необязательной иконкой слева — все варианты кнопок из сайдбара/диалогов. */
class BzButton(
    context: Context,
    label: String,
    private val style: BzButtonStyle = BzButtonStyle.PRIMARY,
    icon: BzIcon? = null,
    textSp: Float = 14f,
    radiusDp: Int = 12,
    hPad: Int = 12,
    vPad: Int = 10,
) : LinearLayout(context) {

    private val labelView: TextView
    private val iconView: BzIconView?

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        val (bg, fg, stroke) = when (style) {
            BzButtonStyle.PRIMARY -> Triple(BzColors.primary, BzColors.primaryForeground, 0)
            BzButtonStyle.DESTRUCTIVE -> Triple(BzColors.destructive, BzColors.primaryForeground, 0)
            BzButtonStyle.OUTLINE -> Triple(0, BzColors.mutedForeground, BzColors.input)
            BzButtonStyle.GHOST -> Triple(0, BzColors.mutedForeground, 0)
            BzButtonStyle.SOFT_BRAND -> Triple(BzColors.alpha(BzColors.brand, 0.10f), BzColors.brand, 0)
        }
        val base = if (bg != 0 || stroke != 0) BzDrawables.rect(context, bg, radiusDp, stroke, if (stroke != 0) 1 else 0) else null
        background = if (style == BzButtonStyle.PRIMARY || style == BzButtonStyle.DESTRUCTIVE)
            BzDrawables.rippleLight(context, base, radiusDp) else BzDrawables.ripple(context, base, radiusDp)
        padSym(hPad, vPad)
        isClickable = true
        isFocusable = true

        iconView = icon?.let { BzIconView(context, it, 16, fg) }
        iconView?.let { add(it, size(16), size(16), mr = 8) }
        labelView = context.bzText(label, textSp, fg, 500)
        add(labelView)
    }

    fun setLabel(text: String) { labelView.text = text }

    /** Отключённая кнопка выглядит как opacity-40. */
    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1f else 0.4f
    }
}
