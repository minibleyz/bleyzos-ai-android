package ru.bleyzos.ai.design.chat

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import ru.bleyzos.ai.api.model.Message
import ru.bleyzos.ai.design.icons.BzIcon
import ru.bleyzos.ai.design.icons.BzIconView
import ru.bleyzos.ai.design.theme.BzColors
import ru.bleyzos.ai.design.theme.BzDrawables
import ru.bleyzos.ai.design.theme.dpi
import ru.bleyzos.ai.design.widgets.BzAttachmentChip
import ru.bleyzos.ai.design.widgets.BzButton
import ru.bleyzos.ai.design.widgets.BzButtonStyle
import ru.bleyzos.ai.design.widgets.BzClipboard
import ru.bleyzos.ai.design.widgets.BzFlowLayout
import ru.bleyzos.ai.design.widgets.BzIconButton
import ru.bleyzos.ai.design.widgets.BzMaxWidthLayout
import ru.bleyzos.ai.design.widgets.MATCH
import ru.bleyzos.ai.design.widgets.WRAP
import ru.bleyzos.ai.design.widgets.add
import ru.bleyzos.ai.design.widgets.bzText
import ru.bleyzos.ai.design.widgets.hbox
import ru.bleyzos.ai.design.widgets.pad
import ru.bleyzos.ai.design.widgets.padSym
import ru.bleyzos.ai.design.widgets.size

/** Сообщение пользователя: вложения, тёмный пузырь, копирование/редактирование, переключатель версий. */
class BzUserMessageView(context: Context, private val cb: BzMessageCallbacks) : LinearLayout(context) {

    private val chipsWrap = BzMaxWidthLayout(context, 0.85f, fill = true)
    private val chips = BzFlowLayout(context, 6, endAligned = true)
    private val rowWrap = BzMaxWidthLayout(context, 0.85f)
    private val bubble: TextView
    private val copyBtn: BzIconButton
    private val editBtn: BzIconButton
    private val editWrap = BzMaxWidthLayout(context, 0.85f, fill = true)
    private val editText: EditText
    private val sendBtn: BzButton
    private val variantsRow: LinearLayout
    private val variantLabel: TextView
    private val prevBtn: BzIconButton
    private val nextBtn: BzIconButton
    private val handler = Handler(Looper.getMainLooper())

    private var message: Message? = null
    private var canEdit = true
    private var editing = false
    private var bound: Message? = null
    private var boundCanEdit = true

    init {
        orientation = VERTICAL
        gravity = Gravity.END

        chipsWrap.addView(chips, LayoutParams(MATCH, WRAP))
        add(chipsWrap, mb = 6, gravity = Gravity.END)

        val row = context.hbox(centerVertical = false).apply { gravity = Gravity.BOTTOM }
        val actions = context.hbox().apply { }
        copyBtn = BzIconButton(context, BzIcon.COPY, sizeDp = 28, iconDp = 14, radiusDp = 8)
        copyBtn.contentDescription = "Скопировать сообщение"
        copyBtn.setOnClickListener {
            message?.let { m ->
                if (BzClipboard.copy(context, m.content)) {
                    copyBtn.setIcon(BzIcon.CHECK)
                    handler.postDelayed({ copyBtn.setIcon(BzIcon.COPY) }, 1500)
                }
            }
        }
        editBtn = BzIconButton(context, BzIcon.PENCIL, sizeDp = 28, iconDp = 14, radiusDp = 8)
        editBtn.contentDescription = "Редактировать сообщение"
        editBtn.setOnClickListener { startEdit() }
        actions.add(copyBtn, size(28), size(28))
        actions.add(editBtn, size(28), size(28))
        row.add(actions, mb = 4, mr = 4)

        bubble = context.bzText("", 14.5f, BzColors.primaryForeground, lineHeightMult = 1.6f)
        bubble.background = BzDrawables.corners(context, BzColors.primary, 16, 16, 6, 16)
        bubble.padSym(16, 10)
        row.add(bubble)
        rowWrap.addView(row, LayoutParams(WRAP, WRAP))
        add(rowWrap, gravity = Gravity.END)

        // редактор
        val box = LinearLayout(context).apply {
            orientation = VERTICAL
            background = BzDrawables.rect(context, BzColors.card, 16, BzColors.alpha(BzColors.brand, 0.4f), 1)
            pad(12, 12, 12, 12)
            elevation = dpi(4).toFloat()
        }
        editText = EditText(context).apply {
            background = null
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14.5f)
            setTextColor(BzColors.foreground)
            typeface = ru.bleyzos.ai.design.theme.BzFonts.sans(context, 400)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            maxHeight = dpi(220)
            minLines = 2
            gravity = Gravity.TOP
            setPadding(dpi(6), dpi(4), dpi(6), dpi(4))
            contentDescription = "Редактировать сообщение"
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) = updateSave()
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            })
        }
        box.add(editText, MATCH)
        val btns = context.hbox().apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        val cancel = BzButton(context, "Отмена", BzButtonStyle.GHOST, BzIcon.X, textSp = 12f, radiusDp = 9, hPad = 10, vPad = 6)
        cancel.setOnClickListener { cancelEdit() }
        sendBtn = BzButton(context, "Отправить", BzButtonStyle.PRIMARY, BzIcon.CHECK, textSp = 12f, radiusDp = 9, hPad = 12, vPad = 6)
        sendBtn.setOnClickListener { commitEdit() }
        btns.add(cancel)
        btns.add(sendBtn, ml = 8)
        box.add(btns, MATCH, mt = 8)
        editWrap.addView(box, LayoutParams(MATCH, WRAP))
        editWrap.visibility = GONE
        add(editWrap, gravity = Gravity.END)

        // ‹ 1 / 2 ›
        variantsRow = context.hbox().apply { visibility = GONE }
        prevBtn = BzIconButton(context, BzIcon.CHEVRON_LEFT, sizeDp = 24, iconDp = 14, radiusDp = 6)
        prevBtn.contentDescription = "Предыдущая версия"
        prevBtn.setOnClickListener { message?.let { cb.onSwitchVariant(it.id, -1) } }
        nextBtn = BzIconButton(context, BzIcon.CHEVRON_RIGHT, sizeDp = 24, iconDp = 14, radiusDp = 6)
        nextBtn.contentDescription = "Следующая версия"
        nextBtn.setOnClickListener { message?.let { cb.onSwitchVariant(it.id, 1) } }
        variantLabel = context.bzText("", 12f, BzColors.mutedForeground)
        variantLabel.gravity = Gravity.CENTER
        variantLabel.minWidth = dpi(40)
        variantsRow.add(prevBtn, size(24), size(24))
        variantsRow.add(variantLabel)
        variantsRow.add(nextBtn, size(24), size(24))
        add(variantsRow, mt = 4, gravity = Gravity.END)
    }

    fun bind(m: Message, canEdit: Boolean) {
        if (bound === m && boundCanEdit == canEdit) return
        bound = m
        boundCanEdit = canEdit
        message = m
        this.canEdit = canEdit

        if (!canEdit && editing) cancelEdit()

        // вложения
        chips.removeAllViews()
        m.attachments.forEach { chips.addView(BzAttachmentChip(context, it.name, it.size, it.mime)) }
        chipsWrap.visibility = if (m.attachments.isEmpty()) GONE else VISIBLE

        bubble.text = m.content
        bubble.visibility = if (m.content.isEmpty()) GONE else VISIBLE
        editBtn.isEnabled = canEdit
        editBtn.alpha = if (canEdit) 1f else 0.4f

        val total = m.variants?.size ?: 1
        val current = (m.variantIndex ?: 0) + 1
        variantsRow.visibility = if (total > 1 && !editing) VISIBLE else GONE
        variantLabel.text = "$current / $total"
        prevBtn.isEnabled = canEdit && current > 1
        prevBtn.alpha = if (prevBtn.isEnabled) 1f else 0.3f
        nextBtn.isEnabled = canEdit && current < total
        nextBtn.alpha = if (nextBtn.isEnabled) 1f else 0.3f
    }

    private fun startEdit() {
        val m = message ?: return
        if (!canEdit) return
        editing = true
        editText.setText(m.content)
        editText.setSelection(editText.text.length)
        rowWrap.visibility = GONE
        variantsRow.visibility = GONE
        editWrap.visibility = VISIBLE
        updateSave()
        editText.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(editText, 0)
    }

    private fun cancelEdit() {
        editing = false
        editWrap.visibility = GONE
        rowWrap.visibility = VISIBLE
        bound = null
        message?.let { bind(it, canEdit) }
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(editText.windowToken, 0)
    }

    private fun updateSave() {
        val m = message ?: return
        val draft = editText.text.toString().trim()
        val unchanged = draft == m.content.trim()
        sendBtn.isEnabled = !unchanged && (draft.isNotEmpty() || m.attachments.isNotEmpty())
    }

    private fun commitEdit() {
        val m = message ?: return
        val next = editText.text.toString().trim()
        if (next == m.content.trim()) return cancelEdit()
        if (next.isEmpty() && m.attachments.isEmpty()) return cancelEdit()
        editing = false
        editWrap.visibility = GONE
        rowWrap.visibility = VISIBLE
        cb.onEditMessage(m.id, next)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }
}
