package com.vidya.os

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/** The notebook paper behind the text: plain, ruled or grid. Always light, like real paper. */
class PaperView(ctx: Context) : View(ctx) {
    var style = 1
        set(v) { field = v; invalidate() }
    private val p = Paint()

    override fun onDraw(c: Canvas) {
        c.drawColor(Color.parseColor("#FBFBF7"))
        p.color = Color.parseColor("#DCE2EE")
        p.strokeWidth = context.dpf(1f)
        val w = width.toFloat()
        val h = height.toFloat()
        if (style == 1) {
            val step = context.dpf(32f)
            var y = context.dpf(12f) + step - context.dpf(7f)
            while (y < h) {
                c.drawLine(0f, y, w, y, p)
                y += step
            }
        } else if (style == 2) {
            val step = context.dpf(28f)
            var y = step
            while (y < h) {
                c.drawLine(0f, y, w, y, p)
                y += step
            }
            var x = step
            while (x < w) {
                c.drawLine(x, 0f, x, h, p)
                x += step
            }
        }
    }
}

/** Handwriting layer: pen, marker, eraser, undo and redo. Stroke-based, so it stays sharp and light on memory. */
class InkView(ctx: Context) : View(ctx) {

    private class Act(val added: Stroke?, val removed: List<Pair<Int, Stroke>>)

    var strokes: ArrayList<Stroke> = ArrayList()
    var drawEnabled = false
    var penOnly = false
    var tool = 0                 // 0 pen, 1 marker, 2 eraser
    var color = Color.parseColor("#1B1F2A")
    var widthDp = 3f
    var onEdit: (() -> Unit)? = null

    private val undoStack = ArrayList<Act>()
    private val redoStack = ArrayList<Act>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private var cur: Stroke? = null
    private var erased = ArrayList<Pair<Int, Stroke>>()
    private val tmp = RectF()

    val canUndo get() = undoStack.isNotEmpty()
    val canRedo get() = redoStack.isNotEmpty()

    fun undo() {
        if (undoStack.isEmpty()) return
        val a = undoStack.removeAt(undoStack.size - 1)
        a.added?.let { strokes.remove(it) }
        for ((i, s) in a.removed.sortedBy { it.first }) strokes.add(minOf(i, strokes.size), s)
        redoStack.add(a)
        invalidate()
        onEdit?.invoke()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val a = redoStack.removeAt(redoStack.size - 1)
        a.added?.let { strokes.add(it) }
        for ((_, s) in a.removed) strokes.remove(s)
        undoStack.add(a)
        invalidate()
        onEdit?.invoke()
    }

    // ---------- touch ----------
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!drawEnabled) return false
        val type = e.getToolType(0)
        if (penOnly && type != MotionEvent.TOOL_TYPE_STYLUS && type != MotionEvent.TOOL_TYPE_ERASER) return false
        val w = width.toFloat().coerceAtLeast(1f)
        val erasing = tool == 2 || type == MotionEvent.TOOL_TYPE_ERASER

        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                if (erasing) {
                    erased = ArrayList()
                    eraseAt(e.x, e.y)
                } else {
                    val s = Stroke(color, widthDp, tool == 1)
                    s.add(e.x / w, e.y / w)
                    strokes.add(s)
                    cur = s
                    s.path = null
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (erasing) {
                    for (k in 0 until e.historySize) eraseAt(e.getHistoricalX(k), e.getHistoricalY(k))
                    eraseAt(e.x, e.y)
                } else {
                    cur?.let { s ->
                        for (k in 0 until e.historySize) s.add(e.getHistoricalX(k) / w, e.getHistoricalY(k) / w)
                        s.add(e.x / w, e.y / w)
                        s.path = null
                    }
                }
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (erasing) {
                    if (erased.isNotEmpty()) {
                        undoStack.add(Act(null, erased))
                        redoStack.clear()
                        onEdit?.invoke()
                    }
                    erased = ArrayList()
                } else {
                    cur?.let {
                        undoStack.add(Act(it, emptyList()))
                        redoStack.clear()
                        onEdit?.invoke()
                    }
                    cur = null
                }
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                cur?.let { strokes.remove(it) }
                cur = null
                if (erased.isNotEmpty()) {
                    for ((i, s) in erased.sortedBy { it.first }) strokes.add(minOf(i, strokes.size), s)
                    erased = ArrayList()
                }
                invalidate()
            }
        }
        return true
    }

    /** Object eraser: removes any stroke the eraser touches. */
    private fun eraseAt(x: Float, y: Float) {
        val r = context.dpf(12f)
        val w = width.toFloat().coerceAtLeast(1f)
        var i = strokes.size - 1
        while (i >= 0) {
            val s = strokes[i]
            pathFor(s)
            tmp.set(s.bounds)
            tmp.inset(-r, -r)
            if (tmp.contains(x, y)) {
                var hit = false
                var k = 0
                while (k + 1 < s.n) {
                    val dx = s.pts[k] * w - x
                    val dy = s.pts[k + 1] * w - y
                    if (dx * dx + dy * dy <= r * r) {
                        hit = true
                        break
                    }
                    k += 2
                }
                if (hit) {
                    strokes.removeAt(i)
                    erased.add(Pair(i, s))
                }
            }
            i--
        }
    }

    // ---------- drawing ----------
    private fun buildPath(s: Stroke): Path {
        val p = Path()
        val w = width.toFloat()
        if (s.n < 2) return p
        var x0 = s.pts[0] * w
        var y0 = s.pts[1] * w
        p.moveTo(x0, y0)
        if (s.n == 2) p.lineTo(x0 + 0.01f, y0)
        var i = 2
        while (i + 1 < s.n) {
            val x1 = s.pts[i] * w
            val y1 = s.pts[i + 1] * w
            p.quadTo(x0, y0, (x0 + x1) / 2f, (y0 + y1) / 2f)
            x0 = x1
            y0 = y1
            i += 2
        }
        p.lineTo(x0, y0)
        return p
    }

    private fun strokeWidthPx(s: Stroke) = context.dpf(s.widthDp * (if (s.hl) 3f else 1f))

    private fun pathFor(s: Stroke): Path {
        var p = s.path
        if (p == null || s.pathW != width) {
            p = buildPath(s)
            s.path = p
            s.pathW = width
            p.computeBounds(s.bounds, true)
            val half = strokeWidthPx(s) / 2f + 2f
            s.bounds.inset(-half, -half)
        }
        return p
    }

    override fun onDraw(c: Canvas) {
        val clip = c.clipBounds
        for (s in strokes) {
            val p = pathFor(s)
            val b = s.bounds
            if (b.right < clip.left || b.left > clip.right || b.bottom < clip.top || b.top > clip.bottom) continue
            paint.strokeWidth = strokeWidthPx(s)
            paint.color = if (s.hl) (s.color and 0x00FFFFFF) or 0x66000000 else s.color
            c.drawPath(p, paint)
        }
    }
}
