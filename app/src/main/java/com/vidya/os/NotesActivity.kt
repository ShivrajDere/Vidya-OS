package com.vidya.os

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateUtils
import android.widget.EditText
import android.widget.LinearLayout

/** List of notes. Tap to open, long-press to delete. */
class NotesActivity : BaseActivity() {

    private var query = ""
    private lateinit var listBox: LinearLayout

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    private fun build() {
        val c = page("Notes", actions = listOf(button("+ New note", primary = true) { openNote(NoteStore.newId()) }))
        val search = EditText(this).apply {
            hint = "Search notes"
            setSingleLine(true)
            setText(query)
            setTextColor(Pal.fg)
            setHintTextColor(Pal.muted)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = shape(Pal.surface, 22, Pal.line, 1)
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString() ?: ""
                    fill()
                }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        c.addView(search, lp(MATCH, WRAP))
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        c.addView(listBox, lpm(MATCH, WRAP, t = 12))
        fill()
    }

    private fun fill() {
        listBox.removeAllViews()
        val q = query.trim().lowercase()
        val notes = NoteStore.list(this).filter {
            q.isEmpty() || it.title.lowercase().contains(q) || it.text.lowercase().contains(q)
        }
        if (notes.isEmpty()) {
            listBox.addView(
                tv(if (q.isEmpty()) "No notes yet. Tap New note to type or draw." else "No note matches that search.", 15f, Pal.muted),
                lpm(MATCH, WRAP, t = 24)
            )
            return
        }
        for (n in notes) {
            val row = card().apply {
                background = ripple(shape(Pal.surface, 18, Pal.line, 1))
                isClickable = true
                setOnClickListener { openNote(n.id) }
                setOnLongClickListener {
                    confirm("Delete this note?", "This cannot be undone.", "Delete") {
                        NoteStore.delete(this@NotesActivity, n.id)
                        fill()
                    }
                    true
                }
            }
            val title = n.title.ifBlank { n.text.lineSequence().firstOrNull { it.isNotBlank() }?.take(60) ?: "Drawing" }
            row.addView(tv(title, 17f, Pal.fg, true).apply { maxLines = 1 })
            val preview = n.text.trim().replace('\n', ' ').take(120)
            if (preview.isNotEmpty() && preview != title) row.addView(tv(preview, 14f, Pal.muted).apply { maxLines = 2 }, lpm(MATCH, WRAP, t = 2))
            val meta = DateUtils.getRelativeTimeSpanString(n.updated, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString() +
                if (n.inkCount > 0) "  -  has handwriting" else ""
            row.addView(tv(meta, 12f, Pal.muted), lpm(MATCH, WRAP, t = 6))
            listBox.addView(row, lpm(MATCH, WRAP, b = 10))
        }
    }

    private fun openNote(id: String) {
        startActivity(Intent(this, NoteEditorActivity::class.java).putExtra("id", id))
    }
}
