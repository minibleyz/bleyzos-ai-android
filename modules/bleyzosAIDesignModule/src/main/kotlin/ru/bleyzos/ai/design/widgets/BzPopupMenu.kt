package ru.bleyzos.ai.design.widgets

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupWindow
import ru.bleyzos.ai.design.theme.BzColors
import ru.bleyzos.ai.design.theme.BzDrawables
import ru.bleyzos.ai.design.theme.dp
import ru.bleyzos.ai.design.theme.dpi

class BzPopupItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    /** Бейдж справа от названия (например «Скоро»). */
    val badge: String? = null,
    val disabled: Boolean = false,
)

/** Выпадающее меню в стиле сайта (DropdownMenu): подпись, разделитель, пункты с подсказками. */
class BzPopupMenu(
    private val context: Context,
    private val label: String,
    private val items: List<BzPopupItem>,
    private val widthDp: Int = 256,
) {
    private var popup: PopupWindow? = null

    fun show(anchor: View, selectedId: String?, onSelect: (BzPopupItem) -> Unit) {
        dismiss()
        val root = context.vbox().apply {
            background = BzDrawables.rect(context, BzColors.card, 14, BzColors.border, 1)
            clipToOutline = true
            pad(4, 4, 4, 4)
        }
        root.add(context.bzText(label, 12f, BzColors.mutedForeground, 600), ml = 8, mt = 6, mb = 6, mr = 8)
        root.add(View(context).apply { setBackgroundColor(BzColors.border) }, MATCH, 1, mb = 4)

        val pw = PopupWindow(root, context.dpi(widthDp), LinearLayout.LayoutParams.WRAP_CONTENT, true)
        for (item in items) {
            val selected = item.id == selectedId
            val row = context.vbox().apply {
                padSym(8, 8)
                background = BzDrawables.ripple(
                    context,
                    if (selected) BzDrawables.rect(context, BzColors.background, 8) else null,
                    8,
                )
                alpha = if (item.disabled) 0.55f else 1f
                isEnabled = !item.disabled
                isClickable = !item.disabled
                setOnClickListener {
                    pw.dismiss()
                    onSelect(item)
                }
            }
            val head = context.hbox()
            head.add(context.bzText(item.title, 14f, BzColors.foreground, 500), weight = 1f, w = 0)
            if (item.badge != null) {
                val badge = context.bzText(item.badge, 10f, BzColors.soonText, 500, letterSpacingEm = 0.05f).apply {
                    text = item.badge.uppercase()
                    background = BzDrawables.rect(context, BzColors.background, 100, BzColors.input, 1)
                    padSym(8, 2)
                }
                head.add(badge, ml = 8)
            }
            row.add(head, MATCH)
            if (item.subtitle != null) row.add(context.bzText(item.subtitle, 12f, BzColors.mutedForeground), mt = 2)
            root.add(row, MATCH)
        }

        pw.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        pw.isOutsideTouchable = true
        pw.elevation = context.dp(12)
        pw.setOnDismissListener { popup = null }
        popup = pw
        pw.showAsDropDown(anchor, 0, context.dpi(4), Gravity.START)
    }

    fun dismiss() {
        popup?.dismiss()
        popup = null
    }

    val isShowing: Boolean get() = popup?.isShowing == true
}
