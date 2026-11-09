package com.vidya.os

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * A browser that only opens websites on the parent's approved list (MHT-CET Cell, Balbharati,
 * NCERT and so on). YouTube links are handed to the Videos app so the daily limit applies.
 * Time spent here counts as study time.
 */
class BrowserActivity : BaseActivity() {

    private lateinit var web: WebView
    private lateinit var urlEt: EditText
    private lateinit var startView: ScrollView
    private lateinit var blockedView: LinearLayout
    private lateinit var blockedText: android.widget.TextView
    private var mode = 0              // 0 start page, 1 web page, 2 blocked
    private var blockedHost = ""
    private var blockedUrl = ""
    private var since = 0L

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val c = page("Study Browser", scroll = false, narrow = false, padded = false)

        // toolbar
        val bar = LinearLayout(this).apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(8))
        }
        bar.addView(chip("Home") { showStart() }, lpm(WRAP, WRAP, r = 6))
        bar.addView(chip("<") { if (mode == 1 && web.canGoBack()) web.goBack() else showStart() }, lpm(WRAP, WRAP, r = 6))
        bar.addView(chip(">") { if (web.canGoForward()) web.goForward() }, lpm(WRAP, WRAP, r = 6))
        bar.addView(chip("Reload") { if (mode == 1) web.reload() }, lpm(WRAP, WRAP, r = 8))
        urlEt = EditText(this).apply {
            hint = "Type a website address"
            setSingleLine(true)
            textSize = 14f
            setTextColor(Pal.fg)
            setHintTextColor(Pal.muted)
            imeOptions = EditorInfo.IME_ACTION_GO
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = shape(Pal.surface, 20, Pal.line, 1)
            setOnEditorActionListener { _, id, ev ->
                if (id == EditorInfo.IME_ACTION_GO || (ev != null && ev.keyCode == KeyEvent.KEYCODE_ENTER)) {
                    hideKeyboard()
                    go(text.toString())
                    true
                } else false
            }
        }
        bar.addView(urlEt, lp(0, WRAP, 1f))
        c.addView(bar, lp(MATCH, WRAP))

        val area = FrameLayout(this)
        c.addView(area, lp(MATCH, 0, 1f))

        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (!request.isForMainFrame) return false
                    return !allow(request.url)
                }

                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    if (!urlEt.hasFocus()) urlEt.setText(url)
                }
            }
            visibility = View.GONE
        }
        area.addView(web, FrameLayout.LayoutParams(MATCH, MATCH))

        // start page: approved sites
        startView = ScrollView(this)
        val sc = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(24))
        }
        sc.addView(tv("Approved sites", 20f, Pal.fg, true))
        sc.addView(
            tv("Study sites a parent has approved. Anything else is closed. YouTube links open in Videos.", 13f, Pal.muted),
            lpm(MATCH, WRAP, t = 2, b = 10)
        )
        val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val cols = if (land) 3 else 2
        val sites = Store.sites
        var rowL: LinearLayout? = null
        sites.forEachIndexed { i, host ->
            if (i % cols == 0) {
                rowL = LinearLayout(this)
                sc.addView(rowL, lpm(MATCH, WRAP, t = 8))
            }
            val cardV = card().apply {
                background = ripple(shape(Pal.surface, 18, Pal.line, 1))
                isClickable = true
                setOnClickListener { go(host) }
            }
            cardV.addView(tv(Store.siteLabel(host), 16f, Pal.fg, true))
            cardV.addView(tv(host, 12f, Pal.muted))
            rowL?.addView(cardV, lpm(0, MATCH, 1f, l = 4, r = 4))
        }
        // fill the last row so cards keep the same width
        val rem = sites.size % cols
        if (rem != 0) for (k in rem until cols) rowL?.addView(View(this), lpm(0, WRAP, 1f, l = 4, r = 4))
        sc.addView(button("Add a site (parent)") {
            askPin {
                promptText("Allow a website", "example.com") { raw ->
                    val host = raw.lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore('/')
                    if (host.contains('.') && !Store.sites.contains(host)) {
                        Store.sites = Store.sites + host
                        recreate()
                    }
                }
            }
        }, lpm(WRAP, WRAP, t = 18))
        startView.addView(sc)
        area.addView(startView, FrameLayout.LayoutParams(MATCH, MATCH))

        // blocked page
        blockedView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(Pal.bg)
            visibility = View.GONE
        }
        blockedText = tv("", 18f, Pal.fg, true).apply { gravity = android.view.Gravity.CENTER }
        blockedView.addView(blockedText, lp(MATCH, WRAP))
        blockedView.addView(
            tv("This site is not on the approved list.", 14f, Pal.muted).apply { gravity = android.view.Gravity.CENTER },
            lpm(MATCH, WRAP, t = 6, b = 16)
        )
        blockedView.addView(button("Ask a parent to allow it", primary = true) {
            askPin {
                if (!Store.sites.contains(blockedHost)) Store.sites = Store.sites + blockedHost
                go(blockedUrl)
            }
        }, lp(WRAP, WRAP))
        blockedView.addView(button("Back to approved sites") { showStart() }, lpm(WRAP, WRAP, t = 10))
        area.addView(blockedView, FrameLayout.LayoutParams(MATCH, MATCH))

        showStart()
        intent.getStringExtra("url")?.let { go(it) }
    }

    private fun setMode(m: Int) {
        mode = m
        startView.visibility = if (m == 0) View.VISIBLE else View.GONE
        web.visibility = if (m == 1) View.VISIBLE else View.GONE
        blockedView.visibility = if (m == 2) View.VISIBLE else View.GONE
    }

    private fun showStart() {
        setMode(0)
        urlEt.setText("")
    }

    /** Is this page allowed? Handles YouTube and blocked sites as a side effect. */
    private fun allow(uri: Uri): Boolean {
        val scheme = uri.scheme ?: return false
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host ?: return false
        if (Yt.isYoutubeHost(host)) {
            if (Yt.videoId(uri.toString()) != null) {
                startActivity(Intent(this, VideosActivity::class.java).setAction(Intent.ACTION_VIEW).setData(uri))
            } else toast("YouTube pages open only as single videos, in Videos.")
            return false
        }
        if (Store.siteAllowed(host)) return true
        showBlocked(host, uri.toString())
        return false
    }

    private fun showBlocked(host: String, url: String) {
        blockedHost = host.removePrefix("www.")
        blockedUrl = url
        blockedText.text = host
        web.stopLoading()
        setMode(2)
    }

    private fun go(raw: String) {
        var t = raw.trim()
        if (t.isEmpty()) return
        if (!t.contains('.') || t.contains(' ')) {
            toast("Type a website address, for example ebalbharati.in")
            return
        }
        if (!t.startsWith("http")) t = "https://$t"
        val uri = Uri.parse(t)
        if (uri.host == null) {
            toast("That address does not look right.")
            return
        }
        if (allow(uri)) {
            setMode(1)
            web.loadUrl(t)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
        since = SystemClock.elapsedRealtime()
    }

    override fun onPause() {
        // time on an approved site counts as study time
        if (mode == 1 && since > 0) Store.addUsage("learn", SystemClock.elapsedRealtime() - since)
        since = 0
        web.onPause()
        super.onPause()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            mode == 1 && web.canGoBack() -> web.goBack()
            mode != 0 -> showStart()
            else -> super.onBackPressed()
        }
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
