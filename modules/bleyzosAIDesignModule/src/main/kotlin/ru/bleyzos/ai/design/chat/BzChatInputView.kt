package ru.bleyzos.ai.design.chat

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import ru.bleyzos.ai.api.model.Attachments
import ru.bleyzos.ai.api.chat.UploadFile
import ru.bleyzos.ai.design.icons.BzIcon
import ru.bleyzos.ai.design.theme.BzColors
import ru.bleyzos.ai.design.theme.BzDrawables
import ru.bleyzos.ai.design.theme.BzFonts
import ru.bleyzos.ai.design.theme.dpi
import ru.bleyzos.ai.design.widgets.BzAttachmentChip
import ru.bleyzos.ai.design.widgets.BzFlowLayout
import ru.bleyzos.ai.design.widgets.BzIconButton
import ru.bleyzos.ai.design.widgets.BzMaxWidthLayout
import ru.bleyzos.ai.design.widgets.MATCH
import ru.bleyzos.ai.design.widgets.WRAP
import ru.bleyzos.ai.design.widgets.add
import ru.bleyzos.ai.design.widgets.bzText
import ru.bleyzos.ai.design.widgets.hbox
import ru.bleyzos.ai.design.widgets.pad
import ru.bleyzos.ai.design.widgets.pressAlpha
import ru.bleyzos.ai.design.widgets.size

/** Поле ввода: скрепка, многострочный текст, «отправить»/«стоп», чипы файлов — chat-input.tsx. */
class BzChatInputView(context: Context) : LinearLayout(context) {

    var onSend: ((text: String, files: List<UploadFile>) -> Unit)? = null
    var onStop: (() -> Unit)? = null
    var onPickFiles: (() -> Unit)? = null

    private val edit: EditText
    private val chips = BzFlowLayout(context, 6)
    private val rejected = context.bzText("", 12f, BzColors.error)
    private val chipsBox: LinearLayout
    private val sendBtn: BzIconButton
    private val stopBtn: BzIconButton
    private var files = ArrayList<UploadFile>()
    private var streaming = false
    private val bottomBase = 16

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val wide = context.resources.configuration.screenWidthDp >= 640
        val side = if (wide) 24 else 12
        pad(side, 0, side, bottomBase)

        val inner = LinearLayout(context).apply { orientation = VERTICAL }

        chipsBox = LinearLayout(context).apply { orientation = VERTICAL; visibility = GONE }
        chipsBox.add(chips, MATCH)
        rejected.visibility = GONE
        chipsBox.add(rejected, mt = 6)
        inner.add(chipsBox, MATCH, mb = 8)

        val box = context.hbox(centerVertical = false).apply {
            gravity = Gravity.BOTTOM
            background = BzDrawables.rect(context, BzColors.card, 16, BzColors.border, 1)
            pad(8, 8, 8, 8)
            elevation = context.resources.displayMetrics.density * 6
            outlineAmbientShadowColor = BzColors.alpha(BzColors.shadowWarm, 0.10f)
            outlineSpotShadowColor = BzColors.alpha(BzColors.shadowWarm, 0.22f)
        }
        val attach = BzIconButton(context, BzIcon.PAPERCLIP, 36, 18)
        attach.contentDescription = "Прикрепить файл"
        attach.setOnClickListener { onPickFiles?.invoke() }
        box.add(attach, size(36), size(36))

        edit = EditText(context).apply {
            background = null
            hint = "Спросите что-нибудь у Bleyzos AI…"
            setHintTextColor(BzColors.mutedForeground)
            setTextColor(BzColors.foreground)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, if (wide) 15f else 14f)
            typeface = BzFonts.sans(context, 400)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            maxHeight = dpi(200)
            // Раньше высота однострочного поля (текст + паддинги, ~33dp) не совпадала
            // с высотой кнопок (36dp), а компенсировалось это подобранным «на глаз»
            // отступом у кнопок (mb = 2) — не точно, поэтому текст всё равно сидел чуть
            // мимо центра. minimumHeight жёстко приравнивает однострочное поле к высоте
            // кнопок, а gravity CENTER_VERTICAL центрует текст внутри этой высоты — при
            // росте в несколько строк поле по-прежнему тянется вверх (gravity BOTTOM
            // контейнера), кнопки остаются внизу вровень с последней строкой.
            minimumHeight = dpi(36)
            setPadding(dpi(4), dpi(8), dpi(4), dpi(8))
            gravity = Gravity.CENTER_VERTICAL
            // Без этого Android добавляет «воздух» сверху/снизу текста (font ascent/descent
            // с запасом), и плейсхолдер визуально едет вниз относительно кнопок — текст
            // выглядит «криво» посаженным в строке ввода.
            includeFontPadding = false
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) = refresh()
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            })
            // Аппаратная клавиатура: Enter — отправить, Shift+Enter — новая строка.
            setOnKeyListener { _, code, ev ->
                if (code == KeyEvent.KEYCODE_ENTER && ev.action == KeyEvent.ACTION_DOWN && !ev.isShiftPressed && ev.source == android.view.InputDevice.SOURCE_KEYBOARD) {
                    submit(); true
                } else false
            }
        }
        box.add(edit, weight = 1f, w = 0, ml = 4, mr = 4)

        val slot = FrameLayout(context)
        sendBtn = BzIconButton(context, BzIcon.ARROW_UP, 36, 16, tint = BzColors.primaryForeground, filledBg = BzColors.primary)
        sendBtn.contentDescription = "Отправить сообщение"
        sendBtn.pressAlpha()
        sendBtn.setOnClickListener { submit() }
        stopBtn = BzIconButton(context, BzIcon.SQUARE, 36, 16, tint = BzColors.primary, filledBg = BzColors.background)
        stopBtn.iconView.filled = true
        stopBtn.contentDescription = "Остановить генерацию"
        stopBtn.setOnClickListener { onStop?.invoke() }
        slot.addView(sendBtn, FrameLayout.LayoutParams(dpi(36), dpi(36)))
        slot.addView(stopBtn, FrameLayout.LayoutParams(dpi(36), dpi(36)))
        box.add(slot, size(36), size(36))
        inner.add(box, MATCH)

        val note = context.bzText("Bleyzos AI может ошибаться — проверяйте важную информацию.", 12f, BzColors.faint)
        note.gravity = Gravity.CENTER
        inner.add(note, MATCH, mt = 8)

        add(BzMaxWidthLayout(context, maxDp = 768, fill = true).apply { addView(inner, LayoutParams(MATCH, WRAP)) }, WRAP)
        refresh()
    }

    fun setStreaming(value: Boolean) {
        if (streaming == value) return
        streaming = value
        refresh()
    }

    /** Отступ снизу от системной панели/клавиатуры (px). */
    fun setBottomInset(px: Int) {
        setPadding(paddingLeft, paddingTop, paddingRight, dpi(bottomBase) + px)
    }

    fun addFiles(list: List<UploadFile>) {
        if (list.isEmpty()) return
        val ok = ArrayList<UploadFile>()
        val big = ArrayList<String>()
        for (f in list) if (f.size > Attachments.MAX_FILE_SIZE) big.add(f.name) else ok.add(f)
        if (big.isNotEmpty()) {
            rejected.text = "Не прикреплены (больше 20 МБ): ${big.joinToString(", ")}"
            rejected.visibility = VISIBLE
        } else rejected.visibility = GONE
        files = ArrayList((files + ok).take(Attachments.MAX_FILES))
        renderChips()
        refresh()
    }

    fun setText(text: String) { edit.setText(text) }

    private fun renderChips() {
        chips.removeAllViews()
        files.forEachIndexed { i, f ->
            chips.addView(BzAttachmentChip(context, f.name, f.size, f.mime) {
                files = ArrayList(files.filterIndexed { idx, _ -> idx != i })
                renderChips(); refresh()
            })
        }
        chipsBox.visibility = if (files.isNotEmpty() || rejected.visibility == VISIBLE) VISIBLE else GONE
        chips.visibility = if (files.isEmpty()) GONE else VISIBLE
    }

    private fun canSend() = (edit.text.toString().trim().isNotEmpty() || files.isNotEmpty()) && !streaming

    private fun refresh() {
        sendBtn.visibility = if (streaming) GONE else VISIBLE
        stopBtn.visibility = if (streaming) VISIBLE else GONE
        sendBtn.isEnabled = canSend()
        sendBtn.alpha = if (canSend()) 1f else 0.4f
    }

    private fun submit() {
        if (!canSend()) return
        val text = edit.text.toString()
        val f = files.toList()
        edit.setText("")
        files = ArrayList()
        rejected.visibility = GONE
        renderChips()
        refresh()
        onSend?.invoke(text, f)
    }
}
