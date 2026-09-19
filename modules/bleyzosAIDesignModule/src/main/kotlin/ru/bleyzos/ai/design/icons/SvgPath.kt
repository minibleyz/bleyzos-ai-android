package ru.bleyzos.ai.design.icons

import android.graphics.Path
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Минимальный парсер SVG-path (d="...") в android.graphics.Path — нужен, чтобы рисовать
 * иконки Lucide без androidx и без VectorDrawable-ресурсов. Поддержаны M L H V C S Q T A Z
 * (абсолютные и относительные).
 */
internal object SvgPath {

    fun parse(d: String): Path {
        val path = Path()
        val t = Tokenizer(d)
        var cx = 0f; var cy = 0f          // текущая точка
        var sx = 0f; var sy = 0f          // начало подпути
        var lcx = 0f; var lcy = 0f        // последняя контрольная точка (для S/T)
        var prev = ' '
        var cmd = ' '

        while (true) {
            t.skipSeparators()
            if (t.atEnd()) break
            if (t.peekIsCommand()) cmd = t.readCommand()
            else if (cmd == ' ') break // числа без команды — битые данные
            val rel = cmd.isLowerCase()
            when (cmd.uppercaseChar()) {
                'M' -> {
                    var x = t.number(); var y = t.number()
                    if (rel) { x += cx; y += cy }
                    path.moveTo(x, y); cx = x; cy = y; sx = x; sy = y
                    cmd = if (rel) 'l' else 'L' // последующие пары — линии
                    prev = 'M'
                    continue
                }
                'L' -> {
                    var x = t.number(); var y = t.number()
                    if (rel) { x += cx; y += cy }
                    path.lineTo(x, y); cx = x; cy = y
                }
                'H' -> { var x = t.number(); if (rel) x += cx; path.lineTo(x, cy); cx = x }
                'V' -> { var y = t.number(); if (rel) y += cy; path.lineTo(cx, y); cy = y }
                'C' -> {
                    var x1 = t.number(); var y1 = t.number()
                    var x2 = t.number(); var y2 = t.number()
                    var x = t.number(); var y = t.number()
                    if (rel) { x1 += cx; y1 += cy; x2 += cx; y2 += cy; x += cx; y += cy }
                    path.cubicTo(x1, y1, x2, y2, x, y)
                    lcx = x2; lcy = y2; cx = x; cy = y
                }
                'S' -> {
                    var x2 = t.number(); var y2 = t.number()
                    var x = t.number(); var y = t.number()
                    if (rel) { x2 += cx; y2 += cy; x += cx; y += cy }
                    val x1 = if (prev == 'C' || prev == 'S') 2 * cx - lcx else cx
                    val y1 = if (prev == 'C' || prev == 'S') 2 * cy - lcy else cy
                    path.cubicTo(x1, y1, x2, y2, x, y)
                    lcx = x2; lcy = y2; cx = x; cy = y
                }
                'Q' -> {
                    var x1 = t.number(); var y1 = t.number()
                    var x = t.number(); var y = t.number()
                    if (rel) { x1 += cx; y1 += cy; x += cx; y += cy }
                    path.quadTo(x1, y1, x, y)
                    lcx = x1; lcy = y1; cx = x; cy = y
                }
                'T' -> {
                    var x = t.number(); var y = t.number()
                    if (rel) { x += cx; y += cy }
                    val x1 = if (prev == 'Q' || prev == 'T') 2 * cx - lcx else cx
                    val y1 = if (prev == 'Q' || prev == 'T') 2 * cy - lcy else cy
                    path.quadTo(x1, y1, x, y)
                    lcx = x1; lcy = y1; cx = x; cy = y
                }
                'A' -> {
                    val rx = t.number(); val ry = t.number(); val rot = t.number()
                    val large = t.flag(); val sweep = t.flag()
                    var x = t.number(); var y = t.number()
                    if (rel) { x += cx; y += cy }
                    arc(path, cx, cy, rx, ry, rot, large, sweep, x, y)
                    cx = x; cy = y
                }
                'Z' -> { path.close(); cx = sx; cy = sy }
                else -> break
            }
            prev = cmd.uppercaseChar()
        }
        return path
    }

    /** Дуга SVG (endpoint → center parameterization, SVG 1.1 F.6.5) → кубические Безье. */
    private fun arc(
        path: Path, x0: Float, y0: Float, rxIn: Float, ryIn: Float, rotDeg: Float,
        large: Boolean, sweep: Boolean, x: Float, y: Float,
    ) {
        var rx = abs(rxIn); var ry = abs(ryIn)
        if (rx == 0f || ry == 0f || (x0 == x && y0 == y)) { path.lineTo(x, y); return }
        val phi = Math.toRadians(rotDeg.toDouble())
        val cosP = cos(phi); val sinP = sin(phi)
        val dx2 = (x0 - x) / 2.0; val dy2 = (y0 - y) / 2.0
        val x1p = cosP * dx2 + sinP * dy2
        val y1p = -sinP * dx2 + cosP * dy2
        val lambda = (x1p * x1p) / (rx.toDouble() * rx) + (y1p * y1p) / (ry.toDouble() * ry)
        if (lambda > 1) { val s = sqrt(lambda); rx = (rx * s).toFloat(); ry = (ry * s).toFloat() }
        val rx2 = rx.toDouble() * rx; val ry2 = ry.toDouble() * ry
        val num = rx2 * ry2 - rx2 * y1p * y1p - ry2 * x1p * x1p
        val den = rx2 * y1p * y1p + ry2 * x1p * x1p
        val coef = (if (large == sweep) -1.0 else 1.0) * sqrt(max(0.0, num / den))
        val cxp = coef * (rx * y1p / ry)
        val cyp = coef * (-ry * x1p / rx)
        val cx = cosP * cxp - sinP * cyp + (x0 + x) / 2.0
        val cy = sinP * cxp + cosP * cyp + (y0 + y) / 2.0

        fun angle(ux: Double, uy: Double, vx: Double, vy: Double): Double {
            val dot = ux * vx + uy * vy
            val len = sqrt(ux * ux + uy * uy) * sqrt(vx * vx + vy * vy)
            var a = acos((dot / len).coerceIn(-1.0, 1.0))
            if (ux * vy - uy * vx < 0) a = -a
            return a
        }
        val theta1 = angle(1.0, 0.0, (x1p - cxp) / rx, (y1p - cyp) / ry)
        var dTheta = angle((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
        if (!sweep && dTheta > 0) dTheta -= 2 * Math.PI
        if (sweep && dTheta < 0) dTheta += 2 * Math.PI

        val segs = max(1, Math.ceil(abs(dTheta) / (Math.PI / 2) - 1e-9).toInt())
        val delta = dTheta / segs
        val k = 4.0 / 3.0 * tan(delta / 4.0)
        var th = theta1
        for (i in 0 until segs) {
            val c1 = cos(th); val s1 = sin(th)
            val c2 = cos(th + delta); val s2 = sin(th + delta)
            // точки на единичной окружности → эллипс → поворот → сдвиг
            fun px(ux: Double, uy: Double) = (cosP * rx * ux - sinP * ry * uy + cx).toFloat()
            fun py(ux: Double, uy: Double) = (sinP * rx * ux + cosP * ry * uy + cy).toFloat()
            path.cubicTo(
                px(c1 - k * s1, s1 + k * c1), py(c1 - k * s1, s1 + k * c1),
                px(c2 + k * s2, s2 - k * c2), py(c2 + k * s2, s2 - k * c2),
                px(c2, s2), py(c2, s2),
            )
            th += delta
        }
    }

    private class Tokenizer(private val s: String) {
        private var i = 0

        fun atEnd() = i >= s.length

        fun skipSeparators() {
            while (i < s.length && (s[i] == ' ' || s[i] == ',' || s[i] == '\n' || s[i] == '\t' || s[i] == '\r')) i++
        }

        fun peekIsCommand(): Boolean = i < s.length && s[i].isLetter() && s[i] != 'e' && s[i] != 'E'

        fun readCommand(): Char = s[i++]

        /** Флаги дуги бывают слитными («a2 2 0 011 1») — читаем один символ 0/1. */
        fun flag(): Boolean {
            skipSeparators()
            val c = s[i++]
            return c == '1'
        }

        fun number(): Float {
            skipSeparators()
            val start = i
            if (i < s.length && (s[i] == '-' || s[i] == '+')) i++
            var dot = false
            while (i < s.length) {
                val c = s[i]
                if (c.isDigit()) i++
                else if (c == '.' && !dot) { dot = true; i++ }
                else if ((c == 'e' || c == 'E') && i + 1 < s.length && (s[i + 1].isDigit() || s[i + 1] == '-' || s[i + 1] == '+')) {
                    i += 2
                } else break
            }
            return s.substring(start, i).toFloat()
        }
    }
}
