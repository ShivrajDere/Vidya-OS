package com.vidya.os

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/** Colours for the current theme (light, dark or follow the tablet). */
object Pal {
    var dark = false
    var bg = 0
    var surface = 0
    var surface2 = 0
    var line = 0
    var fg = 0
    var muted = 0
    var accent = 0
    var good = 0
    var warn = 0
    var bad = 0
    const val accentInk = Color.WHITE

    private fun c(s: String) = Color.parseColor(s)

    fun refresh(ctx: Context) {
        dark = when (Store.mode) {
            "dark" -> true
            "light" -> false
            else -> (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
        if (dark) {
            bg = c("#0E1118"); surface = c("#171B26"); surface2 = c("#222838"); line = c("#2C3347")
            fg = c("#EEF0F6"); muted = c("#98A0B5"); good = c("#43C279"); warn = c("#F0A53A"); bad = c("#FF6B80")
        } else {
            bg = c("#EEF0F5"); surface = c("#FFFFFF"); surface2 = c("#E6E9F1"); line = c("#D3D8E4")
            fg = c("#141722"); muted = c("#5D6477"); good = c("#1F8A50"); warn = c("#B86E00"); bad = c("#D0283F")
        }
        accent = Store.accent
    }
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density
fun Context.toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

fun Context.shape(fill: Int, radiusDp: Int, strokeColor: Int = 0, strokeDp: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dpf(radiusDp.toFloat())
        if (strokeDp > 0) setStroke(dp(strokeDp), strokeColor)
    }

fun ripple(content: Drawable?, color: Int = 0x33808080): Drawable =
    RippleDrawable(ColorStateList.valueOf(color), content, if (content == null) ColorDrawable(Color.WHITE) else null)

fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)

fun Context.lpm(w: Int, h: Int, weight: Float = 0f, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0) =
    LinearLayout.LayoutParams(w, h, weight).also { it.setMargins(dp(l), dp(t), dp(r), dp(b)) }

fun Context.tv(s: String, sp: Float = 15f, color: Int = Pal.fg, bold: Boolean = false): TextView =
    TextView(this).apply {
        text = s
        textSize = sp
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

fun Context.button(label: String, primary: Boolean = false, danger: Boolean = false, onClick: () -> Unit): TextView =
    TextView(this).apply {
        text = label
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(if (primary) Pal.accentInk else if (danger) Pal.bad else Pal.fg)
        setPadding(dp(16), dp(10), dp(16), dp(10))
        minHeight = dp(44)
        background = ripple(shape(if (primary) Pal.accent else Pal.surface2, 12, Pal.line, if (primary) 0 else 1))
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

fun Context.chip(label: String, selected: Boolean = false, onClick: () -> Unit): TextView =
    TextView(this).apply {
        text = label
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(if (selected) Pal.accentInk else Pal.fg)
        setPadding(dp(14), dp(8), dp(14), dp(8))
        background = ripple(shape(if (selected) Pal.accent else Pal.surface2, 20, Pal.line, if (selected) 0 else 1))
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

fun Context.card(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(16), dp(14), dp(16), dp(14))
    background = shape(Pal.surface, 18, Pal.line, 1)
}

fun Context.sectionLabel(s: String): TextView = tv(s.uppercase(), 12f, Pal.muted, true).apply {
    letterSpacing = 0.1f
    setPadding(dp(2), dp(20), 0, dp(8))
}

fun Context.switchRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit): View {
    val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(8), 0, dp(8))
    }
    val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    col.addView(tv(title, 15f, Pal.fg, true))
    if (sub != null) col.addView(tv(sub, 13f, Pal.muted))
    row.addView(col, lp(0, WRAP, 1f))
    val on = intArrayOf(android.R.attr.state_checked)
    val off = intArrayOf()
    val sw = Switch(this).apply {
        isChecked = checked
        thumbTintList = ColorStateList(arrayOf(on, off), intArrayOf(Pal.accent, Pal.muted))
        trackTintList = ColorStateList(arrayOf(on, off), intArrayOf((Pal.accent and 0x00FFFFFF) or 0x66000000, Pal.line))
        setOnCheckedChangeListener { _, v -> onChange(v) }
    }
    row.addView(sw, lpm(WRAP, WRAP, l = 12))
    return row
}

/** A line with a title, optional detail and optional action button. */
fun Context.listRow(title: String, sub: String? = null, action: String? = null, danger: Boolean = false, onAction: (() -> Unit)? = null): View {
    val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(6), 0, dp(6))
    }
    val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    col.addView(tv(title, 15f, Pal.fg, true))
    if (sub != null) col.addView(tv(sub, 13f, Pal.muted))
    row.addView(col, lp(0, WRAP, 1f))
    if (action != null && onAction != null) row.addView(button(action, danger = danger) { onAction() }, lpm(WRAP, WRAP, l = 10))
    return row
}

fun fmtMs(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(s / 60, s % 60)
}

fun fmtHm(ms: Long): String {
    val m = ms / 60_000
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
}

fun Activity.hideKeyboard() {
    val v = currentFocus ?: return
    (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0)
}

fun Activity.askPin(title: String = "Parent PIN", onCancel: () -> Unit = {}, onOk: () -> Unit) {
    val input = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        filters = arrayOf(InputFilter.LengthFilter(4))
        gravity = Gravity.CENTER
        textSize = 24f
        hint = "4 digits"
    }
    val wrap = FrameLayout(this).apply {
        setPadding(dp(24), dp(8), dp(24), 0)
        addView(input, FrameLayout.LayoutParams(MATCH, WRAP))
    }
    val d = AlertDialog.Builder(this)
        .setTitle(title)
        .setView(wrap)
        .setPositiveButton("Unlock", null)
        .setNegativeButton("Cancel") { _, _ -> onCancel() }
        .setOnCancelListener { onCancel() }
        .create()
    d.setOnShowListener {
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (input.text.toString() == Store.pin) {
                d.dismiss()
                onOk()
            } else {
                input.error = "Wrong PIN"
                input.setText("")
            }
        }
        input.requestFocus()
        d.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }
    d.show()
}

fun Activity.promptText(title: String, hint: String, initial: String = "", onOk: (String) -> Unit) {
    val input = EditText(this).apply {
        setText(initial)
        this.hint = hint
        setSingleLine(true)
    }
    val wrap = FrameLayout(this).apply {
        setPadding(dp(24), dp(8), dp(24), 0)
        addView(input, FrameLayout.LayoutParams(MATCH, WRAP))
    }
    AlertDialog.Builder(this)
        .setTitle(title)
        .setView(wrap)
        .setPositiveButton("Save") { _, _ ->
            val t = input.text.toString().trim()
            if (t.isNotEmpty()) onOk(t)
        }
        .setNegativeButton("Cancel", null)
        .show()
}

fun Activity.confirm(title: String, message: String, yes: String = "Yes", onYes: () -> Unit) {
    AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(yes) { _, _ -> onYes() }
        .setNegativeButton("Cancel", null)
        .show()
}

/** Shared screen frame: back arrow, title, optional action buttons, scrolling body. */
open class BaseActivity : Activity() {

    protected fun page(
        title: String,
        scroll: Boolean = true,
        narrow: Boolean = true,
        padded: Boolean = true,
        actions: List<View> = emptyList()
    ): LinearLayout {
        Pal.refresh(this)
        window.statusBarColor = Pal.bg
        window.navigationBarColor = Pal.bg
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            if (Pal.dark) 0 else (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Pal.bg)
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(12), dp(6))
        }
        val back = TextView(this).apply {
            text = "←"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Pal.fg)
            contentDescription = "Back"
            background = ripple(null)
            isClickable = true
            setOnClickListener { onBackPressed() }
        }
        bar.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)))
        bar.addView(
            tv(title, 20f, Pal.fg, true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END },
            lpm(0, WRAP, 1f, l = 6)
        )
        for (a in actions) bar.addView(a, lpm(WRAP, WRAP, l = 8))
        root.addView(bar, LinearLayout.LayoutParams(MATCH, WRAP))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            if (padded) setPadding(dp(16), dp(8), dp(16), dp(24))
        }
        val holder: View = if (narrow) {
            FrameLayout(this).also {
                it.addView(content, FrameLayout.LayoutParams(minOf(dp(820), resources.displayMetrics.widthPixels), WRAP, Gravity.CENTER_HORIZONTAL))
            }
        } else content

        if (scroll) {
            val sv = ScrollView(this).apply { isFillViewport = true }
            sv.addView(holder, ViewGroup.LayoutParams(MATCH, WRAP))
            root.addView(sv, lp(MATCH, 0, 1f))
        } else {
            root.addView(holder, lp(MATCH, 0, 1f))
        }
        setContentView(root)
        return content
    }
}
