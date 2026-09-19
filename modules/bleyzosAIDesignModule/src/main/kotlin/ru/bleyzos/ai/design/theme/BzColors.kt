package ru.bleyzos.ai.design.theme

import android.graphics.Color

/**
 * Палитра бренда Bleyzos (globals.css веб-версии).
 * У сайта тёмная тема совпадает со светлой, поэтому вариант один.
 */
object BzColors {
    /** --background / --secondary / --muted / --accent */
    val background = 0xFFEEEBE4.toInt()
    /** --card / --popover / --sidebar-background */
    val card = 0xFFFBFAF7.toInt()
    /** --primary (тёмный) */
    val primary = 0xFF1F1E1C.toInt()
    val primaryForeground = card
    /** --foreground */
    val foreground = 0xFF2B2924.toInt()
    /** --muted-foreground */
    val mutedForeground = 0xFF8A8375.toInt()
    /** --sidebar-foreground */
    val sidebarForeground = 0xFF6B6558.toInt()
    /** hsl(42 10% 60%) — самые приглушённые подписи */
    val faint = 0xFFA29C8E.toInt()
    /** hsl(42 10% 45%) — бейдж «Скоро» */
    val soonText = 0xFF7E7767.toInt()
    /** --border / --sidebar-border */
    val border = 0xFFE4E0D6.toInt()
    /** --input */
    val input = 0xFFDDD7C9.toInt()
    /** --brand / --ring — акцент */
    val brand = 0xFFA9835A.toInt()
    /** --brand-soft */
    val brandSoft = 0xFFB8A988.toInt()
    /** --destructive */
    val destructive = 0xFF865341.toInt()
    /** emerald-700 — успешный инструмент */
    val success = 0xFF047857.toInt()
    /** red-700 — ошибки */
    val error = 0xFFB91C1C.toInt()
    /** hsl(45 30% 94%) — текст в блоках кода */
    val codeText = 0xFFF4F2EB.toInt()

    val scrim = 0x4D000000 // bg-black/30
    val shadowWarm = 0xFF5A3C14.toInt() // rgb(90 60 20)

    fun alpha(color: Int, a: Float): Int =
        Color.argb((a.coerceIn(0f, 1f) * 255f + 0.5f).toInt(), Color.red(color), Color.green(color), Color.blue(color))
}
