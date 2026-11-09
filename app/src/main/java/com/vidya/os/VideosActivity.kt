package com.vidya.os

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Reads YouTube links. */
object Yt {
    private val ID = Regex("^[A-Za-z0-9_-]{11}$")

    fun extractUrl(text: String): String? = Regex("https?://\\S+").find(text)?.value

    fun videoId(input: String): String? {
        val t = input.trim()
        if (ID.matches(t)) return t
        val uri = try { Uri.parse(t) } catch (e: Exception) { return null }
        val host = (uri.host ?: return null).lowercase().removePrefix("www.").removePrefix("m.")
        if (host == "youtu.be") return uri.pathSegments.firstOrNull()?.takeIf { ID.matches(it) }
        if (host.endsWith("youtube.com") || host.endsWith("youtube-nocookie.com")) {
            val v = uri.getQueryParameter("v")
            if (v != null && ID.matches(v)) return v
            val segs = uri.pathSegments
            if (segs.size >= 2 && segs[0] in setOf("shorts", "embed", "live", "v")) return segs[1].takeIf { ID.matches(it) }
        }
        return null
    }

    fun isYoutubeHost(host: String): Boolean {
        val h = host.lowercase()
        return h == "youtu.be" || h == "youtube.com" || h.endsWith(".youtube.com")
    }
}

/**
 * Plays YouTube links inside the launcher. It asks YouTube who made the video:
 * channels on the parent's Learn list never use Fun time, everything else counts
 * against the daily Fun budget (no limit on Sunday). Time is counted only while the video is playing.
 */
class VideosActivity : BaseActivity() {

    private class Meta(val id: String, val title: String, val author: String, val kind: String)

    private val h = Handler(Looper.getMainLooper())
    private var web: WebView? = null
    private var current: Meta? = null
    private var ytState = -1
    private var lockMsg: String? = null
    private var busy = false
    private var resumed = false
    private var lastTick = 0L
    private var warned5 = false
    private var warned1 = false
    private var infoTv: TextView? = null
    private var linkText = ""

    private val tick = object : Runnable {
        override fun run() {
            val m = current
            if (m != null && resumed) {
                val now = SystemClock.elapsedRealtime()
                val d = (now - lastTick).coerceIn(0L, 3000L)
                lastTick = now
                if (ytState == 1) {
                    Store.addUsage(m.kind, d)
                    checkLimit(m)
                }
                updateInfo()
            }
            h.postDelayed(this, 1000)
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        render()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        val text = when (i?.action) {
            Intent.ACTION_VIEW -> i?.dataString
            Intent.ACTION_SEND -> i?.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        } ?: return
        open(Yt.extractUrl(text) ?: text)
    }

    // ---------- screens ----------
    private fun render() {
        val m = current
        if (m == null) {
            val c = page("Videos")
            renderHome(c)
        } else {
            val c = page("Now playing", scroll = false, narrow = false, padded = false)
            renderPlayer(c, m)
        }
    }

    private fun renderHome(c: LinearLayout) {
        infoTv = null
        (web?.parent as? ViewGroup)?.removeView(web)

        val today = card()
        val funday = Store.isFunday()
        today.addView(tv("TODAY", 12f, Pal.muted, true).apply { letterSpacing = 0.1f })
        val status = when {
            funday -> "Funday: watch what you like, no limit."
            Store.learnOnly -> "Fun videos are paused by a parent. Learning videos are open."
            else -> "Fun time left: ${fmtMs(Store.funLeftMs())} of ${Store.funLimitMin} min"
        }
        today.addView(tv(status, 18f, Pal.fg, true), lpm(MATCH, WRAP, t = 4))
        today.addView(
            tv("Learning today: ${fmtHm(Store.usage("learn"))}. Videos from channels on the Learn list never use Fun time.", 13f, Pal.muted),
            lpm(MATCH, WRAP, t = 4)
        )
        c.addView(today, lp(MATCH, WRAP))

        lockMsg?.let {
            val lock = card().apply { background = shape(Pal.surface, 18, Pal.bad, 2) }
            lock.addView(tv(it, 15f, Pal.bad, true))
            c.addView(lock, lpm(MATCH, WRAP, t = 12))
        }

        c.addView(sectionLabel("Play a YouTube link"))
        val box = card()
        val input = EditText(this).apply {
            hint = "Paste a YouTube link here"
            setText(linkText)
            setSingleLine(true)
            setTextColor(Pal.fg)
            setHintTextColor(Pal.muted)
        }
        box.addView(input, lp(MATCH, WRAP))
        val row = LinearLayout(this)
        row.addView(button("Paste") {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val t = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
            if (t.isNullOrBlank()) toast("Nothing to paste") else input.setText(t)
        }, lpm(0, WRAP, 1f, r = 6))
        row.addView(button("Play", primary = true) {
            linkText = input.text.toString()
            hideKeyboard()
            open(linkText)
        }, lpm(0, WRAP, 1f, l = 6))
        box.addView(row, lpm(MATCH, WRAP, t = 10))
        box.addView(
            tv("Tip: in WhatsApp or any app, tap Share on a YouTube video and choose Vidya OS.", 13f, Pal.muted),
            lpm(MATCH, WRAP, t = 10)
        )
        c.addView(box, lp(MATCH, WRAP))

        val rec = Store.recents()
        if (rec.isNotEmpty()) {
            c.addView(sectionLabel("Recent"))
            val list = card()
            rec.forEach { r ->
                val row2 = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, dp(8), 0, dp(8))
                    background = ripple(null)
                    isClickable = true
                    setOnClickListener { open("https://youtu.be/${r.id}") }
                }
                row2.addView(tv(r.title, 15f, Pal.fg, true).apply { maxLines = 2 })
                val tag = if (r.kind == "learn") "Learning" else "Fun"
                row2.addView(tv("$tag  -  ${r.author}", 13f, if (r.kind == "learn") Pal.good else Pal.warn))
                list.addView(row2, lp(MATCH, WRAP))
            }
            c.addView(list, lp(MATCH, WRAP))
        }
        if (busy) c.addView(tv("Loading video details...", 14f, Pal.muted), lpm(MATCH, WRAP, t = 12))
    }

    private fun renderPlayer(c: LinearLayout, m: Meta) {
        val w = ensureWeb()
        (w.parent as? ViewGroup)?.removeView(w)
        val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val frame = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        frame.addView(w, FrameLayout.LayoutParams(MATCH, MATCH))

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        info.addView(tv(m.title, 18f, Pal.fg, true))
        info.addView(tv(m.author, 14f, Pal.muted), lpm(MATCH, WRAP, t = 2))
        val kindLabel = if (m.kind == "learn") "Learning video" else "Fun video"
        info.addView(tv(kindLabel, 13f, if (m.kind == "learn") Pal.good else Pal.warn, true), lpm(MATCH, WRAP, t = 8))
        infoTv = tv("", 15f, Pal.fg).also { info.addView(it, lpm(MATCH, WRAP, t = 4)) }
        info.addView(button("Stop and close") { closePlayer() }, lpm(WRAP, WRAP, t = 16))

        if (land) {
            val row = LinearLayout(this)
            row.addView(frame, lp(0, MATCH, 2f))
            row.addView(info, lp(0, MATCH, 1f))
            c.addView(row, lp(MATCH, MATCH))
        } else {
            val hgt = resources.displayMetrics.widthPixels * 9 / 16
            c.addView(frame, lp(MATCH, hgt))
            c.addView(info, lp(MATCH, WRAP))
        }
        updateInfo()
    }

    private fun updateInfo() {
        val m = current ?: return
        val t = infoTv ?: return
        t.text = when {
            m.kind == "learn" -> "Counting as study time. No Fun time is used."
            Store.isFunday() -> "Funday: no time limit today."
            else -> "Fun time left today: ${fmtMs(Store.funLeftMs())}"
        }
    }

    // ---------- opening a video ----------
    private fun open(input: String) {
        val id = Yt.videoId(input)
        if (id == null) {
            toast("That does not look like a YouTube video link.")
            return
        }
        if (busy) return
        busy = true
        lockMsg = null
        if (current == null) render()
        Thread {
            val meta = fetchMeta(id)
            runOnUiThread {
                busy = false
                if (isDestroyed) return@runOnUiThread
                if (meta == null) {
                    toast("Could not load this video. Check the link and your internet.")
                    if (current == null) render()
                } else {
                    start(Meta(id, meta.first, meta.second, kindFor(meta.second)))
                }
            }
        }.start()
    }

    private fun kindFor(author: String) =
        if (Store.learnChannels.any { author.contains(it, ignoreCase = true) }) "learn" else "fun"

    private fun fetchMeta(id: String): Pair<String, String>? {
        var c: HttpURLConnection? = null
        return try {
            val watch = URLEncoder.encode("https://www.youtube.com/watch?v=$id", "UTF-8")
            c = URL("https://www.youtube.com/oembed?format=json&url=$watch").openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            if (c.responseCode != 200) null
            else {
                val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                Pair(j.optString("title", "Video"), j.optString("author_name", ""))
            }
        } catch (e: Exception) {
            null
        } finally {
            c?.disconnect()
        }
    }

    private fun start(m: Meta) {
        if (m.kind == "fun" && !Store.funAllowed()) {
            lockMsg = if (Store.learnOnly)
                "Fun videos are switched off by a parent. Learning videos are still open."
            else
                "Fun time is over for today. Learning videos are still open, and Sunday is Funday."
            current = null
            render()
            return
        }
        Store.addRecent(Recent(m.id, m.title, m.author, m.kind))
        current = m
        ytState = -1
        warned5 = false
        warned1 = false
        lastTick = SystemClock.elapsedRealtime()
        render()
        val html = PLAYER_HTML.replace("__ID__", m.id)
        ensureWeb().loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "utf-8", null)
    }

    private fun closePlayer() {
        web?.evaluateJavascript("pauseNow()", null)
        web?.loadUrl("about:blank")
        current = null
        ytState = -1
        render()
    }

    private fun checkLimit(m: Meta) {
        if (m.kind != "fun" || Store.isFunday()) return
        if (Store.learnOnly) {
            closePlayer()
            lockMsg = "Fun videos were switched off by a parent."
            render()
            return
        }
        val left = Store.funLeftMs()
        if (left <= 0L) {
            closePlayer()
            lockMsg = "Fun time is over for today. Learning videos are still open, and Sunday is Funday."
            render()
            return
        }
        if (left <= 60_000L && !warned1) {
            warned1 = true
            toast("Less than 1 minute of Fun time left.")
        } else if (left <= 300_000L && !warned5) {
            warned5 = true
            toast("5 minutes of Fun time left.")
        }
    }

    private fun playerError(code: Int) {
        val msg = if (code == 101 || code == 150 || code == 153)
            "The owner of this video does not allow it to play here."
        else "This video could not be played."
        closePlayer()
        lockMsg = msg
        render()
    }

    // ---------- web view ----------
    private fun ensureWeb(): WebView {
        web?.let { return it }
        val w = WebView(this)
        w.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
        }
        w.setBackgroundColor(Color.BLACK)
        w.addJavascriptInterface(Bridge(), "Android")
        w.webChromeClient = WebChromeClient()
        // Any tap on a YouTube logo, "watch on YouTube" or link tries a page change. Block them all.
        w.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                request.isForMainFrame
        }
        web = w
        return w
    }

    inner class Bridge {
        @JavascriptInterface
        fun state(s: Int) {
            h.post { ytState = s }
        }

        @JavascriptInterface
        fun error(code: Int) {
            h.post { playerError(code) }
        }
    }

    // ---------- lifecycle ----------
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        render()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        lastTick = SystemClock.elapsedRealtime()
        web?.onResume()
        h.removeCallbacks(tick)
        h.postDelayed(tick, 1000)
        if (current == null) render()   // refresh today's numbers
    }

    override fun onPause() {
        resumed = false
        h.removeCallbacks(tick)
        web?.evaluateJavascript("pauseNow()", null)
        web?.onPause()
        super.onPause()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (current != null) closePlayer() else super.onBackPressed()
    }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        web?.destroy()
        web = null
        super.onDestroy()
    }

    companion object {
        private const val PLAYER_HTML = """<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#p{position:absolute;left:0;top:0;width:100%;height:100%;border:0}</style></head>
<body><div id="p"></div>
<script>
var tag=document.createElement('script');tag.src='https://www.youtube.com/iframe_api';document.head.appendChild(tag);
var player;
function onYouTubeIframeAPIReady(){
  player=new YT.Player('p',{videoId:'__ID__',playerVars:{playsinline:1,rel:0,fs:0,modestbranding:1,autoplay:1,iv_load_policy:3},
    events:{onReady:function(e){e.target.playVideo();},onStateChange:function(e){Android.state(e.data);},onError:function(e){Android.error(e.data);}}});
}
function pauseNow(){try{player.pauseVideo();}catch(x){}}
</script></body></html>"""
    }
}
