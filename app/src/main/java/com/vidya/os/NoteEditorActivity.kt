package com.vidya.os

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * One note page: type on it, write on it with a pen or finger, or both.
 * Switch between Type and Draw at the top. The page grows with "+ Page".
 */
class NoteEditorActivity : BaseActivity() {

    private lateinit var note: Note
    private lateinit var titleEt: EditText
    private lateinit var edit: EditText
    private lateinit var paper: PaperView
    private lateinit var ink: InkView
    private lateinit var sv: ScrollView
    private lateinit var tools: LinearLayout
    private var drawing = false
    private var penOnly = false
    private var finished = false

    private val inkColors = listOf("#1B1F2A", "#D0283F", "#E8780A", "#1F8A50", "#2B6FE0", "#7A4DE0")
    private var colorIdx = 0
    private var widthIdx = 1
    private val widths = listOf(2f, 3.5f, 6f)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val id = intent.getStringExtra("id") ?: NoteStore.newId()
        note = NoteStore.load(this, id) ?: Note(id)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val c = page("", scroll = false, narrow = false, padded = false)

        titleEt = EditText(this).apply {
            hint = "Title"
            setText(note.title)
            setSingleLine(true)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Pal.fg)
            setHintTextColor(Pal.muted)
            background = null
            setPadding(dp(18), dp(4), dp(18), dp(4))
        }
        c.addView(titleEt, lp(MATCH, WRAP))

        val hs = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        tools = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(8))
        }
        hs.addView(tools)
        c.addView(hs, lp(MATCH, WRAP))

        // the page
        sv = ScrollView(this)
        val page = FrameLayout(this)
        paper = PaperView(this).apply { style = note.paper }
        edit = EditText(this).apply {
            setText(note.text)
            hint = "Start typing, or switch to Draw"
            textSize = 18f
            setTextColor(Color.parseColor("#1B1F2A"))
            setHintTextColor(Color.parseColor("#9AA2B4"))
            background = null
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setPadding(dp(18), dp(12), dp(18), dp(12))
            lineHeight = dp(32)
        }
        ink = InkView(this).apply { strokes = note.strokes }
        page.addView(paper, FrameLayout.LayoutParams(MATCH, MATCH))
        page.addView(edit, FrameLayout.LayoutParams(MATCH, WRAP))
        page.addView(ink, FrameLayout.LayoutParams(MATCH, MATCH))
        sv.addView(page, FrameLayout.LayoutParams(MATCH, WRAP))
        c.addView(sv, lp(MATCH, 0, 1f))
        sv.post { applyPageHeight() }

        buildTools()
        applyMode()
    }

    private fun applyPageHeight() {
        edit.minHeight = (sv.height * note.screens).coerceAtLeast(dp(400))
    }

    // ---------- toolbar ----------
    private fun buildTools() {
        tools.removeAllViews()
        fun add(v: View) = tools.addView(v, lpm(WRAP, WRAP, r = 8))

        add(chip("Type", !drawing) { setDrawing(false) })
        add(chip("Draw", drawing) { setDrawing(true) })
        tools.addView(divider())

        if (drawing) {
            add(chip("Pen", ink.tool == 0) { ink.tool = 0; buildTools() })
            add(chip("Marker", ink.tool == 1) { ink.tool = 1; buildTools() })
            add(chip("Eraser", ink.tool == 2) { ink.tool = 2; buildTools() })
            inkColors.forEachIndexed { i, hex ->
                val dot = View(this).apply {
                    background = shape(Color.parseColor(hex), 16, if (i == colorIdx && ink.tool != 2) Pal.fg else Pal.line, if (i == colorIdx && ink.tool != 2) 3 else 1)
                    isClickable = true
                    contentDescription = "Colour ${i + 1}"
                    setOnClickListener { colorIdx = i; ink.color = Color.parseColor(hex); if (ink.tool == 2) ink.tool = 0; buildTools() }
                }
                tools.addView(dot, lpm(dp(32), dp(32), l = 0, r = 8).also { it.gravity = Gravity.CENTER_VERTICAL })
            }
            add(chip(listOf("Thin", "Medium", "Thick")[widthIdx]) {
                widthIdx = (widthIdx + 1) % widths.size
                ink.widthDp = widths[widthIdx]
                buildTools()
            })
            add(chip("Undo") { ink.undo() }.apply { alpha = if (ink.canUndo) 1f else 0.4f })
            add(chip("Redo") { ink.redo() }.apply { alpha = if (ink.canRedo) 1f else 0.4f })
            add(chip("Pen only", penOnly) {
                penOnly = !penOnly
                ink.penOnly = penOnly
                toast(if (penOnly) "Only the pen draws. Fingers scroll the page." else "Fingers and pen both draw.")
                buildTools()
            })
        } else {
            add(chip("- List") { insertAtLineStart("• ") })
            add(chip("To-do") { insertAtLineStart("☐ ") })
        }
        tools.addView(divider())
        add(chip(listOf("Plain", "Ruled", "Grid")[note.paper]) {
            note.paper = (note.paper + 1) % 3
            paper.style = note.paper
            buildTools()
        })
        add(chip("+ Page") {
            note.screens += 1
            applyPageHeight()
            toast("Page is now ${note.screens} screens long.")
        })
        ink.onEdit = { if (drawing) buildTools() }
        ink.color = Color.parseColor(inkColors[colorIdx])
        ink.widthDp = widths[widthIdx]
        ink.penOnly = penOnly
    }

    private fun divider(): View = View(this).apply {
        setBackgroundColor(Pal.line)
    }.also { it.layoutParams = lpm(dp(1), dp(24), r = 8) }

    private fun setDrawing(on: Boolean) {
        drawing = on
        applyMode()
        buildTools()
    }

    private fun applyMode() {
        ink.drawEnabled = drawing
        edit.isEnabled = !drawing
        if (drawing) {
            hideKeyboard()
            edit.clearFocus()
        }
    }

    /** Adds a bullet or checkbox at the start of the current line. */
    private fun insertAtLineStart(prefix: String) {
        val t = edit.text
        val pos = edit.selectionStart.coerceAtLeast(0)
        val lineStart = t.lastIndexOf('\n', pos - 1) + 1
        t.insert(lineStart, prefix)
        edit.requestFocus()
    }

    // ---------- saving ----------
    private fun saveNow() {
        if (finished) return
        note.title = titleEt.text.toString().trim()
        note.text = edit.text.toString()
        val empty = note.title.isEmpty() && note.text.isBlank() && note.strokes.isEmpty()
        if (empty) NoteStore.delete(this, note.id) else NoteStore.save(this, note)
    }

    override fun onPause() {
        saveNow()
        super.onPause()
    }
}
