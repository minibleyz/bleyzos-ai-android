package ru.bleyzos.ai.design.theme

import android.content.Context
import android.graphics.Typeface

/**
 * Шрифты бренда: Onest (текст) и Unbounded (заголовки/лого «B»), обе — вариативные
 * TTF из assets/fonts (лицензия OFL). Если файла нет — падаем на системный sans-serif.
 */
object BzFonts {
    private const val SANS_ASSET = "fonts/Onest-Variable.ttf"
    private const val DISPLAY_ASSET = "fonts/Unbounded-Variable.ttf"

    private val cache = HashMap<String, Typeface>()

    /** Onest нужного веса (400/500/600/700). */
    fun sans(context: Context, weight: Int = 400): Typeface = load(context, SANS_ASSET, weight, Typeface.SANS_SERIF)

    /** Unbounded нужного веса (700/900). */
    fun display(context: Context, weight: Int = 700): Typeface = load(context, DISPLAY_ASSET, weight, Typeface.SANS_SERIF)

    val mono: Typeface get() = Typeface.MONOSPACE

    private fun load(context: Context, asset: String, weight: Int, fallback: Typeface): Typeface {
        val key = "$asset@$weight"
        cache[key]?.let { return it }
        val tf = try {
            Typeface.Builder(context.applicationContext.assets, asset)
                .setFontVariationSettings("'wght' $weight")
                .setWeight(weight)
                .build()
        } catch (_: Exception) {
            null
        } ?: Typeface.create(fallback, weight, false)
        cache[key] = tf
        return tf
    }
}
