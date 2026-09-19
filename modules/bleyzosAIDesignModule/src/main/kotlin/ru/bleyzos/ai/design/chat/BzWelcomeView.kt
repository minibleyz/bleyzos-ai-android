package ru.bleyzos.ai.design.chat

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.TypefaceSpan
import android.view.Gravity
import android.widget.LinearLayout
import ru.bleyzos.ai.design.icons.BzIcon
import ru.bleyzos.ai.design.icons.BzIconView
import ru.bleyzos.ai.design.theme.BzColors
import ru.bleyzos.ai.design.theme.BzDrawables
import ru.bleyzos.ai.design.theme.BzFonts
import ru.bleyzos.ai.design.widgets.BzMaxWidthLayout
import ru.bleyzos.ai.design.widgets.MATCH
import ru.bleyzos.ai.design.widgets.add
import ru.bleyzos.ai.design.widgets.bzText
import ru.bleyzos.ai.design.widgets.hbox
import ru.bleyzos.ai.design.widgets.pad
import ru.bleyzos.ai.design.widgets.size
import ru.bleyzos.ai.design.widgets.vbox

/** Стартовый экран: заголовок «Ваш умный ассистент» и 4 карточки-подсказки — welcome.tsx. */
class BzWelcomeView(context: Context, private val onPick: (String) -> Unit) : LinearLayout(context) {

    private class Suggestion(val icon: BzIcon, val title: String, val subtitle: String, val prompt: String)

    private val suggestions = listOf(
        Suggestion(BzIcon.CODE, "Напиши код", "Менеджер задач на Python — с проверкой в терминале", "Напиши скрипт на Python — менеджер задач для терминала"),
        Suggestion(BzIcon.GLOBE, "Собери сайт", "Лендинг с живым HTML-предпросмотром", "Собери одностраничный сайт"),
        Suggestion(BzIcon.FILE_ARCHIVE, "Собери ZIP", "Полный архив песочницы, включая скрытые файлы", "Собери zip-архив проекта со скрытыми файлами"),
        Suggestion(BzIcon.LIGHTBULB, "Объясни сложное", "Что такое квантовая запутанность", "Объясни, что такое квантовая запутанность, простыми словами"),
    )

    private val grid: LinearLayout
    private var columns = 0

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        pad(16, 40, 16, 40)
        val wide = context.resources.configuration.screenWidthDp >= 640

        val title = SpannableString("Ваш умный ассистент").apply {
            val s = indexOf("ассистент")
            setSpan(TypefaceSpan(BzFonts.display(context, 700)), s, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(BzColors.brand), s, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val h1 = context.bzText(title, if (wide) 42f else 32f, BzColors.primary, 600, letterSpacingEm = if (wide) -0.03f else -0.02f, lineHeightMult = 1.15f)
        h1.gravity = Gravity.CENTER
        h1.typeface = BzFonts.sans(context, 600)
        add(BzMaxWidthLayout(context, maxDp = 576).apply { addView(h1, LayoutParams(MATCH, -2)) }, mt = 16)

        val sub = context.bzText(
            "Задайте вопрос — помогу с текстами, кодом, планами и сложными темами",
            if (wide) 16f else 15f, BzColors.mutedForeground, lineHeightMult = 1.6f,
        )
        sub.gravity = Gravity.CENTER
        add(BzMaxWidthLayout(context, maxDp = 448).apply { addView(sub, LayoutParams(MATCH, -2)) }, mt = 12)

        grid = context.vbox()
        add(BzMaxWidthLayout(context, maxDp = 672, fill = true).apply { addView(grid, LayoutParams(MATCH, -2)) }, MATCH, mt = 32)
        setColumns(if (wide) 2 else 1)
    }

    /** 1 колонка на телефоне, 2 — от 640dp (sm:grid-cols-2). */
    fun setColumns(n: Int) {
        if (n == columns) return
        columns = n
        grid.removeAllViews()
        suggestions.chunked(n).forEachIndexed { rowIdx, chunk ->
            val row = context.hbox(centerVertical = false)
            chunk.forEachIndexed { i, s -> row.add(card(s), weight = 1f, w = 0, ml = if (i > 0) 12 else 0) }
            if (chunk.size < n) repeat(n - chunk.size) { row.add(android.view.View(context), weight = 1f, w = 0, ml = 12) }
            grid.add(row, MATCH, mt = if (rowIdx > 0) 12 else 0)
        }
    }

    private fun card(s: Suggestion): LinearLayout {
        val c = context.hbox(centerVertical = false).apply {
            background = BzDrawables.ripple(context, BzDrawables.rect(context, BzColors.card, 18, BzColors.border, 1), 18)
            pad(16, 16, 16, 16)
            isClickable = true
            setOnClickListener { onPick(s.prompt) }
        }
        val box = context.hbox().apply {
            gravity = Gravity.CENTER
            background = BzDrawables.rect(context, BzColors.background, 10, BzColors.input, 1)
        }
        box.addView(BzIconView(context, s.icon, 18, BzColors.alpha(BzColors.foreground, 0.6f)), LayoutParams(size(18), size(18)))
        c.add(box, size(40), size(40), mr = 12)
        val text = context.vbox()
        text.add(context.bzText(s.title, 15f, BzColors.primary, 600, singleLine = true))
        text.add(context.bzText(s.subtitle, 13f, BzColors.mutedForeground, singleLine = true), mt = 2)
        c.add(text, weight = 1f, w = 0)
        return c
    }

}
