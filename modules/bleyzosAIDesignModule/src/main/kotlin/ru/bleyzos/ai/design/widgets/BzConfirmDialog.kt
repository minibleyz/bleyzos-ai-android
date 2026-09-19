package ru.bleyzos.ai.design.widgets

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import ru.bleyzos.ai.design.theme.BzColors
import ru.bleyzos.ai.design.theme.BzDrawables
import ru.bleyzos.ai.design.theme.dpi

/** Подтверждение опасного действия в стиле сайта (AlertDialog из shadcn/ui). */
object BzConfirmDialog {
    fun show(
        context: Context,
        title: String,
        message: String,
        confirmLabel: String,
        cancelLabel: String = "Отмена",
        destructive: Boolean = true,
        onConfirm: () -> Unit,
    ) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val card = context.vbox().apply {
            background = BzDrawables.rect(context, BzColors.card, 18, BzColors.border, 1)
            pad(20, 20, 20, 16)
        }
        card.add(context.bzText(title, 16f, BzColors.primary, 600, lineHeightMult = 1.2f))
        card.add(context.bzText(message, 14f, BzColors.mutedForeground, lineHeightMult = 1.4f), mt = 8)

        val buttons = context.hbox().apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        val cancel = BzButton(context, cancelLabel, BzButtonStyle.OUTLINE, textSp = 13.5f, radiusDp = 10, hPad = 16, vPad = 9)
        cancel.setOnClickListener { dialog.dismiss() }
        val ok = BzButton(
            context, confirmLabel,
            if (destructive) BzButtonStyle.DESTRUCTIVE else BzButtonStyle.PRIMARY,
            textSp = 13.5f, radiusDp = 10, hPad = 16, vPad = 9,
        )
        ok.setOnClickListener { dialog.dismiss(); onConfirm() }
        buttons.add(cancel)
        buttons.add(ok, ml = 8)
        card.add(buttons, MATCH, mt = 20)

        dialog.setContentView(card)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val w = minOf((context.resources.displayMetrics.widthPixels * 0.9f).toInt(), context.dpi(380))
            setLayout(w, WindowManager.LayoutParams.WRAP_CONTENT)
            setDimAmount(0.3f)
        }
        dialog.show()
    }
}
