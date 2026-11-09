package com.vidya.os

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.ConcurrentHashMap

/** Decides which apps may open. The drawer and the guard both ask this one place. */
object Policy {
    val BROWSERS = setOf(
        "com.android.chrome", "org.mozilla.firefox", "org.mozilla.firefox_beta", "com.brave.browser",
        "com.microsoft.emmx", "com.opera.browser", "com.opera.mini.native", "com.sec.android.app.sbrowser",
        "com.android.browser", "com.duckduckgo.mobile.android", "com.vivaldi.browser", "com.UCMobile.intl",
        "com.mi.globalbrowser", "com.heytap.browser", "com.google.android.apps.chrome"
    )
    val YOUTUBE = setOf(
        "com.google.android.youtube", "com.google.android.apps.youtube.kids",
        "app.revanced.android.youtube", "com.vanced.android.youtube"
    )
    private const val SETTINGS = "com.android.settings"

    private val gameCache = ConcurrentHashMap<String, Boolean>()
    fun clearCache() = gameCache.clear()

    /** Android marks games with a category; Play Store sets it for installed games. */
    @Suppress("DEPRECATION")
    fun isGame(pm: PackageManager, pkg: String): Boolean = gameCache.getOrPut(pkg) {
        try {
            val ai = pm.getApplicationInfo(pkg, 0)
            ai.category == ApplicationInfo.CATEGORY_GAME || (ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0
        } catch (e: Exception) {
            false
        }
    }

    /** A game, unless the parent said "this one is fine". */
    fun isGameApp(ctx: Context, pkg: String): Boolean =
        pkg !in Store.allowedPkgs && isGame(ctx.packageManager, pkg)

    fun isBlocked(ctx: Context, pkg: String): Boolean {
        if (pkg == ctx.packageName) return false
        if (pkg in Store.blockedPkgs) return true
        val funday = Store.isFunday()
        if (pkg in YOUTUBE) return !(funday && Store.fundayYoutubeApp)
        if (pkg in BROWSERS) return !(funday && Store.fundayBrowsers)
        if (pkg == SETTINGS) return System.currentTimeMillis() > Store.settingsUnlockedUntil
        if (isGameApp(ctx, pkg)) return !(funday && Store.fundayGames)
        return false
    }
}

data class AppInfo(val pkg: String, val label: String, val icon: Drawable)

object AppsRepo {
    @Volatile
    var cache: List<AppInfo>? = null

    fun load(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val seen = HashSet<String>()
        val out = ArrayList<AppInfo>()
        for (ri in pm.queryIntentActivities(q, 0)) {
            val pkg = ri.activityInfo.packageName
            if (pkg == ctx.packageName || !seen.add(pkg)) continue
            out.add(AppInfo(pkg, ri.loadLabel(pm).toString(), ri.loadIcon(pm)))
        }
        out.sortBy { it.label.lowercase() }
        cache = out
        return out
    }

    fun launch(ctx: Context, pkg: String) {
        if (Policy.isBlocked(ctx, pkg)) {
            ctx.toast("This app is closed right now.")
            return
        }
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg)
        if (i == null) ctx.toast("Could not open this app.") else ctx.startActivity(i)
    }
}

/**
 * Accessibility service used only as a guard: when a blocked app comes to the front it
 * sends the tablet back to the home screen. It reads the package name of the window, nothing else.
 */
class GuardService : AccessibilityService() {

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null || e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString() ?: return
        if (Policy.isBlocked(this, pkg)) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            toast("That app is closed right now.")
        }
    }

    override fun onInterrupt() {}

    companion object {
        fun isEnabled(ctx: Context): Boolean {
            val s = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val cn = ComponentName(ctx, GuardService::class.java)
            return s.split(':').any {
                it.equals(cn.flattenToString(), true) || it.equals(cn.flattenToShortString(), true)
            }
        }
    }
}

object Wallpapers {
    class W(val id: String, val name: String, val colors: IntArray)

    private fun c(vararg s: String) = IntArray(s.size) { android.graphics.Color.parseColor(s[it]) }

    val all = listOf(
        W("dusk", "Dusk", c("#1B1340", "#5B2A86", "#E0647A")),
        W("lagoon", "Lagoon", c("#07263A", "#0B7A8A", "#2459D6")),
        W("ember", "Ember", c("#2A0F12", "#C0392B", "#FF9F43")),
        W("meadow", "Meadow", c("#0B3D2E", "#1F7A4D", "#7CC15A")),
        W("midnight", "Midnight", c("#060818", "#10204A", "#2B3A8C")),
        W("sunrise", "Sunrise", c("#2B1055", "#D4506A", "#FFB347")),
        W("graphite", "Graphite", c("#272B36", "#14161D", "#0B0C10")),
        W("aurora", "Aurora", c("#0B1F3A", "#0F766E", "#4C1D95"))
    )

    fun file(ctx: Context) = java.io.File(ctx.filesDir, "wallpaper.jpg")
    fun stamp(ctx: Context): Long = file(ctx).lastModified()

    fun gradient(w: W) = android.graphics.drawable.GradientDrawable(
        android.graphics.drawable.GradientDrawable.Orientation.TL_BR, w.colors
    )

    /** Puts the chosen wallpaper into an ImageView (photo = cropped to fill, gradient = stretched). */
    fun show(ctx: Context, iv: android.widget.ImageView) {
        if (Store.wallpaper == "custom" && file(ctx).exists()) {
            val bmp: Bitmap? = android.graphics.BitmapFactory.decodeFile(file(ctx).path)
            if (bmp != null) {
                iv.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                iv.setImageBitmap(bmp)
                return
            }
        }
        val w = all.firstOrNull { it.id == Store.wallpaper } ?: all[0]
        iv.scaleType = android.widget.ImageView.ScaleType.FIT_XY
        iv.setImageDrawable(gradient(w))
    }
}
