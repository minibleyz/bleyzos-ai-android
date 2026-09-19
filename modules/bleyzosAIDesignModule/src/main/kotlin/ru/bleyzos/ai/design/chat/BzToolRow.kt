package ru.bleyzos.ai.design.chat

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import ru.bleyzos.ai.api.model.ToolCall
import ru.bleyzos.ai.api.model.ToolStatus
import ru.bleyzos.ai.design.icons.BzIcon
import ru.bleyzos.ai.design.icons.BzIconView
import ru.bleyzos.ai.design.theme.BzColors
import ru.bleyzos.ai.design.theme.BzDrawables
import ru.bleyzos.ai.design.theme.dpi
import ru.bleyzos.ai.design.widgets.MATCH
import ru.bleyzos.ai.design.widgets.WRAP
import ru.bleyzos.ai.design.widgets.add
import ru.bleyzos.ai.design.widgets.bzText
import ru.bleyzos.ai.design.widgets.hbox
import ru.bleyzos.ai.design.widgets.pad
import ru.bleyzos.ai.design.widgets.padSym
import ru.bleyzos.ai.design.widgets.size

/** Скролл с потолком высоты (max-h-60 для вывода инструмента). */
private class MaxHeightScroll(context: Context, private val maxDp: Int) : ScrollView(context) {
    override fun onMeasure(w: Int, h: Int) {
        super.onMeasure(w, MeasureSpec.makeMeasureSpec(dpi(maxDp), MeasureSpec.AT_MOST))
    }
}

/** Раскрывающаяся строка вызова инструмента — tool-row.tsx. */
class BzToolRow(context: Context) : LinearLayout(context) {
    private val iconBox: LinearLayout
    private val icon: BzIconView
    private val title: TextView
    private val summary: TextView
    private val time: TextView
    private val status: BzIconView
    private val chevron: BzIconView
    private val outputScroll: MaxHeightScroll
    private val outputText: TextView
    private var open = false
    private var lastName = ""

    private class Meta(val label: String, val icon: BzIcon, val arg: (Map<String, String>) -> String)

    private val metas = mapOf(
        "shell" to Meta("Терминал", BzIcon.TERMINAL) { it["command"].orEmpty() },
        "write_file" to Meta("Создание файла", BzIcon.FILE_PLUS) { it["file"].orEmpty() },
        "edit_file" to Meta("Редактирование", BzIcon.FILE_PEN) { "${it["file"].orEmpty()} · ${it["action"].orEmpty()}" },
        "read_file" to Meta("Чтение файла", BzIcon.FILE_TEXT) { it["file"].orEmpty() },
        "list_files" to Meta("Файлы проекта", BzIcon.FOLDER_TREE) { "обзор песочницы" },
        "delete_file" to Meta("Удаление", BzIcon.TRASH) { it["file"].orEmpty() },
        "build_zip" to Meta("Сборка ZIP", BzIcon.FILE_ARCHIVE) { if (it["include_hidden"] == "true") "со скрытыми файлами" else "" },
        "present_file" to Meta("Предпросмотр", BzIcon.EYE) { it["file"].orEmpty() },
    )

    init {
        orientation = VERTICAL
        background = BzDrawables.rect(context, BzColors.alpha(BzColors.card, 0.8f), 12, BzColors.border, 1)
        clipToOutline = true

        val head = context.hbox().apply {
            padSym(12, 10)
            background = BzDrawables.ripple(context, null, 12)
            isClickable = true
            setOnClickListener { toggle() }
        }
        iconBox = context.hbox().apply {
            gravity = Gravity.CENTER
            background = BzDrawables.rect(context, BzColors.background, 8, BzColors.input, 1)
        }
        icon = BzIconView(context, BzIcon.TERMINAL, 15, BzColors.mutedForeground)
        iconBox.addView(icon, LayoutParams(dpi(15), dpi(15)))
        head.add(iconBox, size(28), size(28), mr = 10)

        title = context.bzText("", 13f, BzColors.primary, 600)
        head.add(title)
        summary = context.bzText("", 12f, BzColors.mutedForeground, mono = true, singleLine = true)
        head.add(summary, weight = 1f, w = 0, ml = 10)
        time = context.bzText("", 11f, BzColors.faint)
        head.add(time, ml = 8)
        status = BzIconView(context, BzIcon.LOADER, 16, BzColors.brand)
        head.add(status, size(16), size(16), ml = 8)
        chevron = BzIconView(context, BzIcon.CHEVRON_DOWN, 16, BzColors.mutedForeground)
        head.add(chevron, size(16), size(16), ml = 8)
        add(head, MATCH)

        outputText = context.bzText("…", 12f, BzColors.alpha(BzColors.foreground, 0.8f), mono = true, lineHeightMult = 1.6f)
        outputText.pad(12, 10, 12, 10)
        outputScroll = MaxHeightScroll(context, 240).apply {
            visibility = GONE
            setBackgroundColor(BzColors.alpha(BzColors.background, 0.5f))
        }
        outputScroll.addView(outputText, LayoutParams(MATCH, WRAP))
        add(View(context).apply { setBackgroundColor(BzColors.border); visibility = GONE; tag = "divider" }, MATCH, 1)
        add(outputScroll, MATCH)
    }

    fun bind(call: ToolCall) {
        val meta = metas[call.name] ?: Meta(call.name, BzIcon.TERMINAL) { it.values.firstOrNull().orEmpty() }
        if (lastName != call.name) {
            lastName = call.name
            icon.icon = meta.icon
            title.text = meta.label
        }
        summary.text = meta.arg(call.args)
        val running = call.status == ToolStatus.RUNNING
        icon.tint = when {
            running -> BzColors.brand
            call.status == ToolStatus.ERROR -> BzColors.error
            else -> BzColors.alpha(BzColors.foreground, 0.6f)
        }
        time.visibility = if (call.ms != null && !running) VISIBLE else GONE
        call.ms?.let { time.text = String.format(java.util.Locale.US, "%.1f с", it / 1000.0) }
        when (call.status) {
            ToolStatus.RUNNING -> { status.icon = BzIcon.LOADER; status.tint = BzColors.brand; status.spinning = true }
            ToolStatus.OK -> { status.spinning = false; status.icon = BzIcon.CHECK; status.tint = BzColors.success }
            ToolStatus.ERROR -> { status.spinning = false; status.icon = BzIcon.X; status.tint = BzColors.error }
        }
        val out = call.output?.takeIf { it.isNotEmpty() } ?: "…"
        if (outputText.text.toString() != out) outputText.text = out
    }

    private fun toggle() {
        open = !open
        chevron.animate().rotation(if (open) 180f else 0f).setDuration(150).start()
        outputScroll.visibility = if (open) VISIBLE else GONE
        findViewWithTag<View>("divider")?.visibility = if (open) VISIBLE else GONE
    }
}
