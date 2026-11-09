package com.vidya.os

import android.content.Context
import android.graphics.Path
import android.graphics.RectF
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/** One pen stroke. Points are stored as x/width, y/width so a note looks the same in portrait and landscape. */
class Stroke(var color: Int, var widthDp: Float, var hl: Boolean) {
    var pts = FloatArray(64)
    var n = 0
    var path: Path? = null
    var pathW = -1
    val bounds = RectF()

    fun add(x: Float, y: Float) {
        if (n + 2 > pts.size) pts = pts.copyOf(pts.size * 2)
        pts[n++] = x
        pts[n++] = y
    }
}

class Note(
    val id: String,
    var title: String = "",
    var text: String = "",
    var updated: Long = System.currentTimeMillis(),
    var screens: Int = 1,        // page length, in screens
    var paper: Int = 1,          // 0 plain, 1 ruled, 2 grid
    var strokes: ArrayList<Stroke> = ArrayList(),
    var inkCount: Int = 0
)

/** Notes live as small JSON files inside the app's private folder. */
object NoteStore {
    private fun dir(c: Context) = File(c.filesDir, "notes").apply { mkdirs() }
    private fun file(c: Context, id: String) = File(dir(c), "$id.json")

    fun newId() = java.util.UUID.randomUUID().toString().take(12)

    fun list(c: Context): List<Note> =
        (dir(c).listFiles() ?: emptyArray())
            .filter { it.name.endsWith(".json") }
            .mapNotNull { read(it, withInk = false) }
            .sortedByDescending { it.updated }

    fun load(c: Context, id: String): Note? = file(c, id).takeIf { it.exists() }?.let { read(it, true) }

    fun delete(c: Context, id: String) {
        file(c, id).delete()
    }

    fun save(c: Context, n: Note) {
        n.updated = System.currentTimeMillis()
        val j = JSONObject()
            .put("id", n.id).put("title", n.title).put("text", n.text).put("updated", n.updated)
            .put("screens", n.screens).put("paper", n.paper)
            .put("inkCount", n.strokes.size).put("ink", encode(n.strokes))
        val tmp = File(dir(c), "${n.id}.tmp")
        tmp.writeText(j.toString())
        tmp.renameTo(file(c, n.id))
    }

    private fun read(f: File, withInk: Boolean): Note? = try {
        val j = JSONObject(f.readText())
        Note(
            id = j.getString("id"),
            title = j.optString("title"),
            text = j.optString("text"),
            updated = j.optLong("updated"),
            screens = j.optInt("screens", 1),
            paper = j.optInt("paper", 1),
            strokes = if (withInk) decode(j.optString("ink")) else ArrayList(),
            inkCount = j.optInt("inkCount")
        )
    } catch (e: Exception) {
        null
    }

    private fun encode(list: List<Stroke>): String {
        val bo = ByteArrayOutputStream()
        val d = DataOutputStream(bo)
        d.writeInt(list.size)
        for (s in list) {
            d.writeInt(s.color)
            d.writeFloat(s.widthDp)
            d.writeBoolean(s.hl)
            d.writeInt(s.n)
            for (i in 0 until s.n) d.writeFloat(s.pts[i])
        }
        d.flush()
        return Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP)
    }

    private fun decode(str: String): ArrayList<Stroke> {
        val out = ArrayList<Stroke>()
        if (str.isEmpty()) return out
        val d = DataInputStream(ByteArrayInputStream(Base64.decode(str, Base64.NO_WRAP)))
        val count = d.readInt()
        repeat(count) {
            val s = Stroke(d.readInt(), d.readFloat(), d.readBoolean())
            val n = d.readInt()
            s.pts = FloatArray(maxOf(n, 2))
            for (i in 0 until n) s.pts[i] = d.readFloat()
            s.n = n
            out.add(s)
        }
        return out
    }
}
