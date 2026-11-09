package com.vidya.os

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.io.FileOutputStream

/** Free wallpapers (built in, or your own photo), accent colours, light or dark. */
class ThemesActivity : BaseActivity() {

    private val accents = listOf(
        "Indigo" to "#5B5BF0", "Blue" to "#2B6FE0", "Teal" to "#0B8A7E", "Green" to "#23844A",
        "Orange" to "#D9531E", "Pink" to "#D13B73", "Violet" to "#7A4DE0"
    )

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val c = page("Wallpapers and themes")

        c.addView(sectionLabel("Wallpaper"))
        val land = resources.configuration.screenWidthDp >= 700
        val cols = if (land) 4 else 2
        val g = GridLayout(this).apply { columnCount = cols }
        val items = ArrayList<View>()
        for (w in Wallpapers.all) items.add(swatch(w.name, Wallpapers.gradient(w).also { it.cornerRadius = dpf(14f) }, Store.wallpaper == w.id) {
            Store.wallpaper = w.id
            recreate()
        })
        if (Wallpapers.file(this).exists()) {
            val bmp = BitmapFactory.decodeFile(Wallpapers.file(this).path, BitmapFactory.Options().apply { inSampleSize = 4 })
            if (bmp != null) {
                val d = android.graphics.drawable.BitmapDrawable(resources, bmp)
                items.add(swatch("My photo", d, Store.wallpaper == "custom") {
                    Store.wallpaper = "custom"
                    recreate()
                })
            }
        }
        items.forEachIndexed { i, v ->
            val p = GridLayout.LayoutParams(GridLayout.spec(i / cols), GridLayout.spec(i % cols, 1f))
            p.width = 0
            p.setMargins(dp(4), dp(4), dp(4), dp(4))
            p.setGravity(Gravity.FILL_HORIZONTAL)
            g.addView(v, p)
        }
        c.addView(g, lp(MATCH, WRAP))
        c.addView(button("Use my own photo") {
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"),
                REQ_PHOTO
            )
        }, lpm(WRAP, WRAP, t = 10))
        c.addView(tv("Free to use. The photo stays on this tablet.", 13f, Pal.muted), lpm(MATCH, WRAP, t = 6))

        c.addView(sectionLabel("Accent colour"))
        val row = LinearLayout(this)
        for ((name, hex) in accents) {
            val col = Color.parseColor(hex)
            val on = Store.accent == col
            row.addView(View(this).apply {
                background = shape(col, 22, if (on) Pal.fg else Pal.line, if (on) 3 else 1)
                contentDescription = name
                isClickable = true
                setOnClickListener { Store.accent = col; recreate() }
            }, lpm(dp(44), dp(44), r = 10))
        }
        val hs = android.widget.HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        hs.addView(row)
        c.addView(hs, lp(MATCH, WRAP))

        c.addView(sectionLabel("Appearance"))
        val modes = LinearLayout(this)
        listOf("auto" to "Match tablet", "light" to "Light", "dark" to "Dark").forEach { (id, label) ->
            modes.addView(chip(label, Store.mode == id) { Store.mode = id; recreate() }, lpm(WRAP, WRAP, r = 8))
        }
        c.addView(modes, lp(WRAP, WRAP))

        val opts = card()
        opts.addView(switchRow("Show app names", "Names under the home screen icons", Store.labels) { Store.labels = it })
        c.addView(opts, lpm(MATCH, WRAP, t = 16))
    }

    private fun swatch(name: String, d: android.graphics.drawable.Drawable, selected: Boolean, onClick: () -> Unit): View {
        val f = FrameLayout(this).apply {
            background = shape(Color.TRANSPARENT, 16, if (selected) Pal.accent else Pal.line, if (selected) 3 else 1)
            setPadding(dp(3), dp(3), dp(3), dp(3))
            isClickable = true
            contentDescription = name
            setOnClickListener { onClick() }
        }
        f.addView(View(this).apply { background = d }, FrameLayout.LayoutParams(MATCH, dp(92)))
        f.addView(TextView(this).apply {
            text = if (selected) "$name  ✓" else name
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setShadowLayer(4f, 0f, 1f, 0xAA000000.toInt())
            setPadding(dp(10), 0, dp(10), dp(8))
        }, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.START))
        return f
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PHOTO && resultCode == RESULT_OK) data?.data?.let { importPhoto(it) }
    }

    /** Shrinks the picked photo to at most 1600 px so it uses little memory and storage. */
    private fun importPhoto(uri: Uri) {
        Thread {
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                if (bmp == null) {
                    runOnUiThread { toast("Could not read that picture.") }
                    return@Thread
                }
                val scale = minOf(1f, 1600f / maxOf(bmp.width, bmp.height))
                val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
                FileOutputStream(Wallpapers.file(this)).use { out.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                runOnUiThread {
                    Store.wallpaper = "custom"
                    recreate()
                }
            } catch (e: Exception) {
                runOnUiThread { toast("Could not save that picture.") }
            }
        }.start()
    }

    companion object {
        private const val REQ_PHOTO = 7
    }
}
