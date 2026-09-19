package ru.bleyzos.ai.design.markdown

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import ru.bleyzos.ai.design.icons.BzIcon
import ru.bleyzos.ai.design.icons.BzIconView
import ru.bleyzos.ai.design.theme.BzColors
import ru.bleyzos.ai.design.theme.BzDrawables
import ru.bleyzos.ai.design.widgets.MATCH
import ru.bleyzos.ai.design.widgets.WRAP
import ru.bleyzos.ai.design.widgets.add
import ru.bleyzos.ai.design.widgets.bzText
import ru.bleyzos.ai.design.widgets.hbox
import ru.bleyzos.ai.design.widgets.pad
import ru.bleyzos.ai.design.widgets.padSym
import ru.bleyzos.ai.design.widgets.size

/** Тёмный блок кода: язык, «Копировать», горизонтальная прокрутка. */
class BzCodeBlockView(context: Context) : LinearLayout(context) {
    private val langView: TextView
    private val codeView: TextView
    private val copyIcon: BzIconView
    private val copyLabel: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var code = ""
    private var copied = false
    private val resetCopied = Runnable { setCopied(false) }

    init {
        orientation = VERTICAL
        background = BzDrawables.rect(context, BzColors.primary, 14)
        clipToOutline = true

        val header = context.hbox().apply { padSym(12, 6) }
        langView = context.bzText("code", 12f, BzColors.alpha(Color.WHITE, 0.40f), mono = true)
        header.add(langView, weight = 1f, w = 0)

        val copyBtn = context.hbox().apply {
            padSym(8, 4)
            background = BzDrawables.rippleLight(context, null, 6)
            isClickable = true
            isFocusable = true
            contentDescription = "Скопировать код"
            setOnClickListener { copy() }
        }
        copyIcon = BzIconView(context, BzIcon.COPY, 14, BzColors.alpha(Color.WHITE, 0.60f))
        copyLabel = context.bzText("Копировать", 12f, BzColors.alpha(Color.WHITE, 0.60f))
        copyBtn.add(copyIcon, size(14), size(14), mr = 4)
        copyBtn.add(copyLabel)
        header.add(copyBtn)
        add(header, MATCH)

        add(View(context).apply { setBackgroundColor(BzColors.alpha(Color.WHITE, 0.10f)) }, MATCH, 1)

        codeView = context.bzText("", 13f, BzColors.codeText, mono = true, lineHeightMult = 1.625f)
        codeView.setHorizontallyScrolling(true)
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            pad(16, 16, 16, 16)
            clipToPadding = false
        }
        scroll.addView(codeView, LayoutParams(WRAP, WRAP))
        add(scroll, MATCH)
    }

    fun setCode(lang: String, code: String) {
        this.code = code
        val l = lang.ifEmpty { "code" }
        if (langView.text.toString() != l) langView.text = l
        if (codeView.text.toString() != code) codeView.text = code
    }

    private fun copy() {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("code", code))
            setCopied(true)
            handler.removeCallbacks(resetCopied)
            handler.postDelayed(resetCopied, 1500)
        } catch (_: Exception) {
            // буфер обмена недоступен — игнорируем, как в веб-версии
        }
    }

    private fun setCopied(v: Boolean) {
        copied = v
        copyIcon.icon = if (v) BzIcon.CHECK else BzIcon.COPY
        copyLabel.text = if (v) "Скопировано" else "Копировать"
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(resetCopied)
        super.onDetachedFromWindow()
    }


}
