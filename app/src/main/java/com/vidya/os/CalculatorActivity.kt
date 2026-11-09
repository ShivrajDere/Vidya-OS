package com.vidya.os

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.math.BigDecimal
import java.math.MathContext

class CalculatorActivity : BaseActivity() {

    private var cur = "0"
    private var acc: Double? = null
    private var op: String? = null
    private var fresh = true
    private lateinit var disp: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val c = page("Calculator")
        val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        disp = tv("0", 44f, Pal.fg, true).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            maxLines = 1
            setPadding(dp(18), dp(14), dp(18), dp(14))
            background = shape(Pal.surface, 18, Pal.line, 1)
        }
        c.addView(disp, lp(MATCH, WRAP))

        val keys = listOf(
            "C", "±", "%", "÷",
            "7", "8", "9", "×",
            "4", "5", "6", "−",
            "1", "2", "3", "+",
            "⌫", "0", ".", "="
        )
        val g = GridLayout(this).apply { columnCount = 4 }
        keys.forEachIndexed { i, k ->
            val isOp = k in listOf("÷", "×", "−", "+")
            val isEq = k == "="
            val btn = TextView(this).apply {
                text = k
                textSize = 22f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(if (isEq) Pal.accentInk else Pal.fg)
                background = ripple(
                    shape(
                        if (isEq) Pal.accent else if (isOp) (Pal.accent and 0x00FFFFFF) or 0x33000000 else Pal.surface,
                        16, Pal.line, if (isEq) 0 else 1
                    )
                )
                isClickable = true
                setOnClickListener { press(k) }
            }
            val p = GridLayout.LayoutParams(GridLayout.spec(i / 4), GridLayout.spec(i % 4, 1f))
            p.width = 0
            p.height = dp(if (land) 52 else 66)
            p.setMargins(dp(4), dp(4), dp(4), dp(4))
            p.setGravity(Gravity.FILL)
            g.addView(btn, p)
        }
        c.addView(g, lpm(MATCH, WRAP, t = 10))
    }

    private fun press(k: String) {
        if (cur == "Error" && k != "C") cur = "0"
        when {
            k.length == 1 && k[0].isDigit() -> {
                cur = if (fresh || cur == "0") k else if (cur.length < 14) cur + k else cur
                fresh = false
            }
            k == "." -> {
                if (fresh) { cur = "0."; fresh = false } else if (!cur.contains('.')) cur += "."
            }
            k == "C" -> { cur = "0"; acc = null; op = null; fresh = true }
            k == "⌫" -> {
                if (!fresh) cur = if (cur.length > 1 && !(cur.length == 2 && cur[0] == '-')) cur.dropLast(1) else "0"
            }
            k == "±" -> if (cur != "0") cur = if (cur.startsWith("-")) cur.drop(1) else "-$cur"
            k == "%" -> { cur = fmt((cur.toDoubleOrNull() ?: 0.0) / 100.0); fresh = true }
            k == "=" -> {
                val a = acc
                val o = op
                if (a != null && o != null) {
                    cur = fmt(calc(a, o, cur.toDoubleOrNull() ?: 0.0))
                    acc = null
                    op = null
                    fresh = true
                }
            }
            else -> {   // + - x /
                val a = acc
                val o = op
                if (a != null && o != null && !fresh) cur = fmt(calc(a, o, cur.toDoubleOrNull() ?: 0.0))
                acc = cur.toDoubleOrNull()
                op = k
                fresh = true
            }
        }
        if (cur == "Error") { acc = null; op = null; fresh = true }
        disp.text = cur
    }

    private fun calc(a: Double, o: String, b: Double): Double = when (o) {
        "+" -> a + b
        "−" -> a - b
        "×" -> a * b
        else -> if (b == 0.0) Double.NaN else a / b
    }

    private fun fmt(d: Double): String {
        if (d.isNaN() || d.isInfinite()) return "Error"
        return BigDecimal(d).round(MathContext(12)).stripTrailingZeros().toPlainString()
    }
}
