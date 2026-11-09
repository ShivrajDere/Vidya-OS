package com.vidya.os

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RingView(ctx: Context) : View(ctx) {
    var frac = 1f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val r = RectF()

    override fun onDraw(c: Canvas) {
        val sw = context.dpf(7f)
        p.strokeWidth = sw
        r.set(sw, sw, width - sw, height - sw)
        p.color = Color.argb(60, 255, 255, 255)
        c.drawArc(r, 0f, 360f, false, p)
        p.color = Color.WHITE
        if (frac > 0f) c.drawArc(r, -90f, 360f * frac, false, p)
    }
}

class LauncherActivity : BaseActivity() {

    private class Builtin(val name: String, val icon: Int, val c1: String, val c2: String, val target: Class<*>)

    private val builtins = listOf(
        Builtin("Videos", R.drawable.ic_video, "#FF5D73", "#C2185B", VideosActivity::class.java),
        Builtin("Study Browser", R.drawable.ic_book, "#4CC38A", "#13805A", BrowserActivity::class.java),
        Builtin("Notes", R.drawable.ic_notes, "#FFD166", "#E09F1F", NotesActivity::class.java),
        Builtin("Clock", R.drawable.ic_clock, "#2DD4BF", "#0B7A8A", ClockActivity::class.java),
        Builtin("Calculator", R.drawable.ic_calc, "#6B7280", "#2B2F3A", CalculatorActivity::class.java),
        Builtin("Wallpapers", R.drawable.ic_image, "#FB7185", "#7C3AED", ThemesActivity::class.java),
        Builtin("Parent", R.drawable.ic_shield, "#60A5FA", "#1D4ED8", ParentActivity::class.java)
    )

    private val h = Handler(Looper.getMainLooper())
    private val white = Color.WHITE
    private val dim = Color.argb(205, 255, 255, 255)
    private var sig = ""
    private var query = ""
    private var allApps: List<AppInfo> = emptyList()

    private lateinit var scroll: ScrollView
    private lateinit var appsBox: LinearLayout
    private lateinit var dock: LinearLayout
    private lateinit var search: EditText
    private lateinit var clockTv: TextView
    private lateinit var dateTv: TextView
    private lateinit var funChip: TextView
    private lateinit var guardChip: TextView
    private lateinit var ring: RingView
    private lateinit var funTv: TextView
    private lateinit var funSub: TextView
    private lateinit var studyTv: TextView
    private lateinit var examTv: TextView

    private val ticker = object : Runnable {
        override fun run() {
            refreshLive()
            h.postDelayed(this, 15_000)
        }
    }

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            Policy.clearCache()
            loadApps()
        }
    }

    private fun signature() = "${Store.wallpaper}|${Store.accent}|${Store.mode}|${Store.labels}|${Wallpapers.stamp(this)}"

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Pal.refresh(this)
        buildUi()
        registerReceiver(pkgReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })
        AppsRepo.cache?.let { allApps = it; renderApps(); renderDock() }
        loadApps()
    }

    private fun glassChip(): TextView = TextView(this).apply {
        textSize = 13f
        setTextColor(white)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setPadding(dp(12), dp(5), dp(12), dp(5))
        background = shape(0x33FFFFFF, 20, 0x44FFFFFF, 1)
    }

    private fun glassCard(): LinearLayout = LinearLayout(this).apply {
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = ripple(shape(0x33FFFFFF, 24, 0x44FFFFFF, 1), 0x33FFFFFF)
        isClickable = true
    }

    private fun buildUi() {
        sig = signature()
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULL_SCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        val frame = FrameLayout(this)
        val wall = ImageView(this)
        Wallpapers.show(this, wall)
        frame.addView(wall, FrameLayout.LayoutParams(MATCH, MATCH))
        frame.addView(View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x66000000, 0x1A000000, Color.argb(140, 0, 0, 0))
            )
        }, FrameLayout.LayoutParams(MATCH, MATCH))

        val main = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        @Suppress("DEPRECATION")
        main.setOnApplyWindowInsetsListener { v, ins ->
            v.setPadding(ins.systemWindowInsetLeft, ins.systemWindowInsetTop, ins.systemWindowInsetRight, ins.systemWindowInsetBottom)
            ins
        }
        frame.addView(main, FrameLayout.LayoutParams(MATCH, MATCH))

        // top bar
        val top = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(2))
        }
        top.addView(tv("Vidya OS", 16f, white, true), lp(0, WRAP, 1f))
        guardChip = glassChip().apply {
            text = "Guard off"
            setOnClickListener {
                askPin {
                    Store.settingsUnlockedUntil = System.currentTimeMillis() + 10 * 60_000
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }
        }
        funChip = glassChip().apply { setOnClickListener { open(VideosActivity::class.java) } }
        top.addView(guardChip, lpm(WRAP, WRAP, r = 8))
        top.addView(funChip, lpm(WRAP, WRAP))
        main.addView(top, lp(MATCH, WRAP))

        // scrolling area
        scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(16))
        }
        scroll.addView(col)
        main.addView(scroll, lp(MATCH, 0, 1f))

        val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        clockTv = tv("", 64f, white, true).apply {
            letterSpacing = -0.02f
            setShadowLayer(8f, 0f, 2f, 0x66000000)
        }
        dateTv = tv("", 17f, dim)
        val greet = tv("Ready to learn something?", 20f, white, true).apply { setPadding(0, dp(10), 0, 0) }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        left.addView(clockTv)
        left.addView(dateTv)
        left.addView(greet)

        ring = RingView(this)
        funTv = tv("", 26f, white, true)
        funSub = tv("", 13f, dim)
        val funCard = glassCard().apply {
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { open(VideosActivity::class.java) }
        }
        funCard.addView(ring, LinearLayout.LayoutParams(dp(60), dp(60)))
        val funCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        funCol.addView(tv("FUN TIME LEFT", 11f, dim, true).apply { letterSpacing = 0.1f })
        funCol.addView(funTv)
        funCol.addView(funSub)
        funCard.addView(funCol, lpm(WRAP, WRAP, l = 14))

        studyTv = tv("", 26f, white, true)
        examTv = tv("", 13f, dim)
        val studyCard = glassCard().apply {
            orientation = LinearLayout.VERTICAL
            setOnClickListener {
                if (Store.examDaysLeft() == null) toast("A parent can set the exam date in Parent controls.")
            }
        }
        studyCard.addView(tv("STUDIED TODAY", 11f, dim, true).apply { letterSpacing = 0.1f })
        studyCard.addView(studyTv)
        studyCard.addView(examTv)

        val hero = LinearLayout(this).apply {
            orientation = if (land) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = if (land) Gravity.BOTTOM else Gravity.START
        }
        hero.addView(left, if (land) lp(0, WRAP, 1f) else lp(MATCH, WRAP))
        if (land) {
            val cards = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            cards.addView(funCard, lp(MATCH, WRAP))
            cards.addView(studyCard, lpm(MATCH, WRAP, t = 10))
            hero.addView(cards, LinearLayout.LayoutParams(dp(340), WRAP))
        } else {
            val cards = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            cards.addView(funCard, lpm(0, MATCH, 1f, r = 5))
            cards.addView(studyCard, lpm(0, MATCH, 1f, l = 5))
            hero.addView(cards, lpm(MATCH, WRAP, t = 16))
        }
        col.addView(hero, lp(MATCH, WRAP))

        search = EditText(this).apply {
            hint = "Search apps"
            setHintTextColor(dim)
            setTextColor(white)
            textSize = 15f
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(dp(18), dp(10), dp(18), dp(10))
            background = shape(0x33FFFFFF, 26, 0x44FFFFFF, 1)
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString() ?: ""
                    renderApps()
                }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        col.addView(search, lpm(MATCH, WRAP, t = 22, b = 4))

        appsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(appsBox, lp(MATCH, WRAP))

        dock = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = shape(0x33FFFFFF, 30, 0x44FFFFFF, 1)
        }
        val dockWrap = FrameLayout(this).apply { setPadding(dp(16), dp(4), dp(16), dp(12)) }
        dockWrap.addView(dock, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER_HORIZONTAL))
        main.addView(dockWrap, lp(MATCH, WRAP))

        setContentView(frame)
    }

    // ---------- live widgets ----------
    private fun refreshLive() {
        val now = Date()
        clockTv.text = DateFormat.getTimeFormat(this).format(now)
        dateTv.text = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now)

        if (Store.isFunday()) {
            funChip.text = "Funday"
            funTv.text = "No limit"
            funSub.text = "Sunday is Funday"
            ring.frac = 1f
        } else if (Store.learnOnly) {
            funChip.text = "Learning only"
            funTv.text = "Off"
            funSub.text = "Fun videos are paused"
            ring.frac = 0f
        } else {
            val left = Store.funLeftMs()
            funChip.text = "Fun " + fmtMs(left) + " left"
            funTv.text = fmtMs(left)
            funSub.text = "of ${Store.funLimitMin} min today"
            ring.frac = left / (Store.funLimitMin * 60_000f).coerceAtLeast(1f)
        }
        studyTv.text = fmtHm(Store.usage("learn"))
        val d = Store.examDaysLeft()
        examTv.text = when {
            d == null -> "MHT-CET date not set"
            d > 1 -> "MHT-CET in $d days"
            d == 1 -> "MHT-CET is tomorrow"
            d == 0 -> "MHT-CET is today"
            else -> "MHT-CET date has passed"
        }
        guardChip.visibility = if (GuardService.isEnabled(this)) View.GONE else View.VISIBLE
    }

    // ---------- apps ----------
    private fun loadApps() {
        Thread {
            val l = AppsRepo.load(this)
            runOnUiThread {
                if (!isDestroyed) {
                    allApps = l
                    renderApps()
                    renderDock()
                }
            }
        }.start()
    }

    private fun open(target: Class<*>) = startActivity(Intent(this, target))

    private fun columns(): Int {
        val wdp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        return (wdp / 104f).toInt().coerceIn(4, 10)
    }

    private fun tile(label: String, icon: View, showLabel: Boolean, onClick: () -> Unit, onLong: (() -> Unit)? = null): View {
        val t = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(4), dp(8), dp(4), dp(8))
            background = ripple(null, 0x33FFFFFF)
            isClickable = true
            contentDescription = label
            setOnClickListener { onClick() }
            if (onLong != null) setOnLongClickListener { onLong(); true }
        }
        t.addView(icon, LinearLayout.LayoutParams(dp(60), dp(60)))
        if (showLabel && Store.labels) {
            t.addView(TextView(this).apply {
                text = label
                textSize = 12f
                setTextColor(white)
                gravity = Gravity.CENTER
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setShadowLayer(4f, 0f, 1f, 0x99000000.toInt())
            }, lpm(MATCH, WRAP, t = 6))
        }
        return t
    }

    private fun builtinIcon(b: Builtin): View {
        val f = FrameLayout(this)
        f.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor(b.c1), Color.parseColor(b.c2))
        ).apply { cornerRadius = dpf(16f) }
        f.addView(ImageView(this).apply { setImageResource(b.icon) },
            FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER))
        return f
    }

    private fun appIcon(a: AppInfo): View = ImageView(this).apply {
        setImageDrawable(a.icon.constantState?.newDrawable()?.mutate() ?: a.icon)
    }

    private fun builtinTile(b: Builtin, label: Boolean = true) =
        tile(b.name, builtinIcon(b), label, { open(b.target) })

    private fun appTile(a: AppInfo) = tile(a.label, appIcon(a), true, { AppsRepo.launch(this, a.pkg) }, {
        askPin("Parent PIN to block ${a.label}") {
            confirm("Block ${a.label}?", "It will disappear from the home screen and cannot be opened.", "Block") {
                Store.blockedPkgs = Store.blockedPkgs + a.pkg
                renderApps()
                renderDock()
            }
        }
    })

    private fun grid(tiles: List<View>): GridLayout {
        val g = GridLayout(this)
        val cols = columns()
        g.columnCount = cols
        tiles.forEachIndexed { i, v ->
            val p = GridLayout.LayoutParams(GridLayout.spec(i / cols), GridLayout.spec(i % cols, 1f))
            p.width = 0
            p.setGravity(Gravity.FILL_HORIZONTAL)
            g.addView(v, p)
        }
        return g
    }

    private fun title(s: String) = tv(s.uppercase(), 12f, dim, true).apply {
        letterSpacing = 0.12f
        setPadding(dp(2), dp(18), 0, dp(8))
    }

    private fun renderApps() {
        appsBox.removeAllViews()
        val q = query.trim().lowercase()

        val mine = builtins.filter { q.isEmpty() || it.name.lowercase().contains(q) }
        if (mine.isNotEmpty()) {
            appsBox.addView(title("Vidya"))
            appsBox.addView(grid(mine.map { builtinTile(it) }))
        }

        val normal = ArrayList<AppInfo>()
        val games = ArrayList<AppInfo>()
        var lockedGames = 0
        for (a in allApps) {
            if (q.isNotEmpty() && !a.label.lowercase().contains(q)) continue
            val isG = Policy.isGameApp(this, a.pkg)
            if (Policy.isBlocked(this, a.pkg)) {
                if (isG) lockedGames++
                continue
            }
            if (isG) games.add(a) else normal.add(a)
        }
        if (normal.isNotEmpty()) {
            appsBox.addView(title("Apps"))
            appsBox.addView(grid(normal.map { appTile(it) }))
        }
        if (games.isNotEmpty()) {
            appsBox.addView(title("Games - Funday"))
            appsBox.addView(grid(games.map { appTile(it) }))
        }
        if (lockedGames > 0 && q.isEmpty()) {
            val msg = if (Store.fundayOn && Store.fundayGames)
                "$lockedGames game(s) installed. They open on Sunday."
            else "$lockedGames game(s) installed. Games are closed."
            appsBox.addView(tv(msg, 13f, dim).apply { setPadding(dp(2), dp(14), 0, 0) })
        }
    }

    private fun renderDock() {
        dock.removeAllViews()
        for (b in builtins.take(4)) dock.addView(builtinTile(b, false), lpm(WRAP, WRAP, l = 4, r = 4))
        allApps.firstOrNull { it.pkg == "com.android.vending" }?.let {
            val t = tile(it.label, appIcon(it), false, { AppsRepo.launch(this, it.pkg) })
            dock.addView(t, lpm(WRAP, WRAP, l = 4, r = 4))
        }
    }

    // ---------- lifecycle ----------
    override fun onResume() {
        super.onResume()
        Pal.refresh(this)
        if (sig != signature()) {
            recreate()
            return
        }
        refreshLive()
        if (allApps.isNotEmpty()) {
            renderApps()
            renderDock()
        }
        h.removeCallbacks(ticker)
        h.postDelayed(ticker, 15_000)
    }

    override fun onPause() {
        h.removeCallbacks(ticker)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (::scroll.isInitialized) {
            scroll.smoothScrollTo(0, 0)
            search.setText("")
            hideKeyboard()
        }
    }

    // a home screen has nothing to go back to
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {}

    override fun onDestroy() {
        try { unregisterReceiver(pkgReceiver) } catch (e: Exception) { }
        super.onDestroy()
    }
}
