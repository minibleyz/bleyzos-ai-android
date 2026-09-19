package ru.bleyzos.ai.design.widgets

import android.content.Context
import android.view.Gravity
import android.widget.FrameLayout
import ru.bleyzos.ai.design.icons.BzIcon
import ru.bleyzos.ai.design.icons.BzIconView
import ru.bleyzos.ai.design.theme.BzColors
import ru.bleyzos.ai.design.theme.BzDrawables
import ru.bleyzos.ai.design.theme.dpi

/**
 * Квадратная кнопка-иконка (h-9 w-9 rounded-[10px] в веб-версии).
 * По умолчанию — «призрачная»: без фона, с ripple; [filledBg] делает её залитой.
 */
class BzIconButton(
    context: Context,
    icon: BzIcon,
    val sizeDp: Int = 36,
    iconDp: Int = 18,
    tint: Int = BzColors.mutedForeground,
    radiusDp: Int = 10,
    filledBg: Int = 0,
    strokeColor: Int = 0,
) : FrameLayout(context) {

    val iconView = BzIconView(context, icon, iconDp, tint)

    init {
        addView(iconView, FrameLayout.LayoutParams(dpi(iconDp), dpi(iconDp), Gravity.CENTER))
        val base = if (filledBg != 0 || strokeColor != 0)
            BzDrawables.rect(context, filledBg, radiusDp, strokeColor, if (strokeColor != 0) 1 else 0)
        else null
        background = if (filledBg == BzColors.primary) BzDrawables.rippleLight(context, base, radiusDp)
        else BzDrawables.ripple(context, base, radiusDp)
        isClickable = true
        isFocusable = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val s = dpi(sizeDp)
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(s, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(s, MeasureSpec.EXACTLY),
        )
    }

    fun setIcon(icon: BzIcon) { iconView.icon = icon }
    fun setTint(color: Int) { iconView.tint = color }
}
