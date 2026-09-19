package ru.bleyzos.ai.design.chat

import android.content.Context
import android.graphics.BitmapFactory
import android.text.TextUtils
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import ru.bleyzos.ai.api.BleyzosAIClient
import ru.bleyzos.ai.api.BzConfig
import ru.bleyzos.ai.api.model.Session
import ru.bleyzos.ai.design.icons.BzIcon
import ru.bleyzos.ai.design.icons.BzIconView
import ru.bleyzos.ai.design.theme.BzColors
import ru.bleyzos.ai.design.theme.BzDrawables
import ru.bleyzos.ai.design.theme.dpi
import ru.bleyzos.ai.design.widgets.BzButton
import ru.bleyzos.ai.design.widgets.BzButtonStyle
import ru.bleyzos.ai.design.widgets.BzIconButton
import ru.bleyzos.ai.design.widgets.BzMark
import ru.bleyzos.ai.design.widgets.MATCH
import ru.bleyzos.ai.design.widgets.WRAP
import ru.bleyzos.ai.design.widgets.add
import ru.bleyzos.ai.design.widgets.bzText
import ru.bleyzos.ai.design.widgets.hbox
import ru.bleyzos.ai.design.widgets.pad
import ru.bleyzos.ai.design.widgets.size
import ru.bleyzos.ai.design.widgets.vbox

interface BzSidebarCallbacks {
    fun onSelect(id: String)
    fun onNewChat()
    fun onDelete(id: String, title: String)
}

/** Левая панель: бренд, «Новый чат», история диалогов, вход/выход — sidebar.tsx. */
class BzSidebarView(context: Context, client: BleyzosAIClient, private val cb: BzSidebarCallbacks) : LinearLayout(context) {

    private val brandBox = context.hbox()
    private val list = context.vbox()
    private val scroll = ScrollView(context)
    private val footer: LinearLayout
    private val top: View
    private var signature = ""

    init {
        orientation = VERTICAL
        setBackgroundColor(BzColors.card)

        top = View(context)
        add(top, MATCH, 0)

        brandBox.pad(16, 20, 16, 8)
        add(brandBox, MATCH)
        showFallbackBrand()
        loadBrand(client)

        val newChat = BzButton(context, "Новый чат", BzButtonStyle.PRIMARY, BzIcon.PLUS, textSp = 14f, radiusDp = 12, hPad = 12, vPad = 10)
        newChat.setOnClickListener { cb.onNewChat() }
        add(newChat, MATCH, ml = 12, mt = 16, mr = 12, mb = 12)

        val label = context.bzText("ИСТОРИЯ", 11f, BzColors.faint, 500, letterSpacingEm = 0.09f)
        add(label, ml = 16, mb = 4)

        list.pad(8, 0, 8, 8)
        scroll.addView(list, LayoutParams(MATCH, WRAP))
        scroll.isVerticalScrollBarEnabled = false
        add(scroll, MATCH, 0, weight = 1f)

        add(View(context).apply { setBackgroundColor(BzColors.border) }, MATCH, 1)
        footer = context.vbox().apply { pad(16, 12, 16, 12) }
        footer.add(BzAuthPanel(context, client), MATCH)
        footer.add(context.bzText("© 2026 Bleyzos · ai.bleyzos.ru", 11f, BzColors.faint), mt = 12)
        add(footer, MATCH)
    }

    fun setInsets(topPx: Int, bottomPx: Int) {
        top.layoutParams = top.layoutParams.apply { height = topPx }
        footer.setPadding(dpi(16), dpi(12), dpi(16), dpi(12) + bottomPx)
    }

    fun bind(sessions: List<Session>, activeId: String?) {
        val sig = activeId.orEmpty() + "|" + sessions.joinToString("\u0001") { it.id + "\u0002" + it.title }
        if (sig == signature) return
        signature = sig
        list.removeAllViews()
        if (sessions.isEmpty()) {
            list.add(context.bzText("Здесь появятся ваши диалоги", 12f, BzColors.mutedForeground), mt = 12, ml = 8, mb = 12)
            return
        }
        sessions.forEachIndexed { i, s -> list.add(row(s, s.id == activeId), MATCH, mt = if (i > 0) 4 else 0) }
    }

    private fun row(s: Session, active: Boolean): View {
        val fill = if (active) BzDrawables.rect(context, BzColors.background, 10, BzColors.input, 1)
        else BzDrawables.rect(context, 0, 10, 0, 0)
        val row = context.hbox().apply {
            background = BzDrawables.ripple(context, fill, 10)
            setPadding(0, 0, dpi(4), 0)
        }
        val main = context.hbox().apply {
            padding(10, 8)
            isClickable = true
            setOnClickListener { cb.onSelect(s.id) }
        }
        main.add(BzIconView(context, BzIcon.MESSAGE_SQUARE, 14, BzColors.mutedForeground), size(14), size(14), mr = 8)
        val title = context.bzText(s.title, 13f, BzColors.sidebarForeground, singleLine = true)
        title.ellipsize = TextUtils.TruncateAt.END
        main.add(title, weight = 1f, w = 0)
        row.add(main, weight = 1f, w = 0)

        val del = BzIconButton(context, BzIcon.TRASH, sizeDp = 32, iconDp = 14, radiusDp = 8)
        del.contentDescription = "Удалить чат «${s.title}»"
        del.setOnClickListener { cb.onDelete(s.id, s.title) }
        row.add(del, size(32), size(32))
        return row
    }

    private fun LinearLayout.padding(h: Int, v: Int) = setPadding(dpi(h), dpi(v), dpi(h), dpi(v))

    private fun showFallbackBrand() {
        brandBox.removeAllViews()
        brandBox.add(BzMark(context, 36, 16f), size(36), size(36), mr = 10)
        brandBox.add(context.bzText("Bleyzos AI", 16f, BzColors.primary, 700, display = true))
    }

    private fun loadBrand(client: BleyzosAIClient) {
        client.files.fetchBytes(BzConfig.BRAND_IMAGE_URL) { bytes, _ ->
            val bmp = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } ?: return@fetchBytes
            val iv = ImageView(context).apply {
                setImageBitmap(bmp)
                scaleType = ImageView.ScaleType.FIT_START
                adjustViewBounds = true
                maxWidth = dpi(140)
                contentDescription = "Bleyzos"
            }
            brandBox.removeAllViews()
            brandBox.addView(iv, LayoutParams(WRAP, dpi(36)))
        }
    }

}
