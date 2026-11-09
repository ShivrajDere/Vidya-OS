package com.vidya.os

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class VidyaApp : android.app.Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Store.pruneOld()
    }
}

data class Recent(val id: String, val title: String, val author: String, val kind: String)

/** Everything the launcher remembers. One small SharedPreferences file, no database. */
object Store {
    private lateinit var sp: SharedPreferences
    private val listCache = HashMap<String, List<String>>()

    fun init(c: Context) {
        sp = c.applicationContext.getSharedPreferences("vidya", Context.MODE_PRIVATE)
    }

    fun raw(): SharedPreferences = sp

    // ---------- defaults the parent can edit ----------
    val DEFAULT_CHANNELS = listOf(
        "Physics Wallah", "Unacademy", "Vedantu", "3Blue1Brown", "CrashCourse",
        "TED-Ed", "The Organic Chemistry Tutor", "Veritasium"
    )
    val DEFAULT_SITES = listOf(
        "cetcell.mahacet.org", "mahacet.org", "ebalbharati.in", "balbharati.in",
        "ncert.nic.in", "mahahsscboard.in", "wikipedia.org", "phet.colorado.edu", "desmos.com"
    )
    private val SITE_NAMES = mapOf(
        "cetcell.mahacet.org" to "MHT-CET Cell (official)",
        "mahacet.org" to "Maharashtra CET Cell",
        "ebalbharati.in" to "Balbharati e-books",
        "balbharati.in" to "Balbharati",
        "ncert.nic.in" to "NCERT textbooks",
        "mahahsscboard.in" to "Maharashtra HSC Board",
        "wikipedia.org" to "Wikipedia",
        "phet.colorado.edu" to "PhET simulations",
        "desmos.com" to "Desmos graphs"
    )
    fun siteLabel(host: String) = SITE_NAMES[host] ?: host

    // ---------- simple settings ----------
    var pin: String
        get() = sp.getString("pin", "1234") ?: "1234"
        set(v) = sp.edit().putString("pin", v).apply()
    var funLimitMin: Int
        get() = sp.getInt("funLimit", 30)
        set(v) = sp.edit().putInt("funLimit", v).apply()
    var learnOnly: Boolean
        get() = sp.getBoolean("learnOnly", false)
        set(v) = sp.edit().putBoolean("learnOnly", v).apply()
    var fundayOn: Boolean
        get() = sp.getBoolean("fundayOn", true)
        set(v) = sp.edit().putBoolean("fundayOn", v).apply()
    var fundayGames: Boolean
        get() = sp.getBoolean("fundayGames", true)
        set(v) = sp.edit().putBoolean("fundayGames", v).apply()
    var fundayYoutubeApp: Boolean
        get() = sp.getBoolean("fundayYt", true)
        set(v) = sp.edit().putBoolean("fundayYt", v).apply()
    var fundayBrowsers: Boolean
        get() = sp.getBoolean("fundayBrowsers", false)
        set(v) = sp.edit().putBoolean("fundayBrowsers", v).apply()
    var examDate: String
        get() = sp.getString("examDate", "") ?: ""
        set(v) = sp.edit().putString("examDate", v).apply()
    var wallpaper: String
        get() = sp.getString("wall", "dusk") ?: "dusk"
        set(v) = sp.edit().putString("wall", v).apply()
    var accent: Int
        get() = sp.getInt("accent", android.graphics.Color.parseColor("#5B5BF0"))
        set(v) = sp.edit().putInt("accent", v).apply()
    var mode: String
        get() = sp.getString("mode", "auto") ?: "auto"
        set(v) = sp.edit().putString("mode", v).apply()
    var labels: Boolean
        get() = sp.getBoolean("labels", true)
        set(v) = sp.edit().putBoolean("labels", v).apply()
    var settingsUnlockedUntil: Long
        get() = sp.getLong("settingsUntil", 0L)
        set(v) = sp.edit().putLong("settingsUntil", v).apply()

    // ---------- lists (cached in memory because the guard reads them often) ----------
    private fun list(key: String, def: List<String>): List<String> {
        listCache[key]?.let { return it }
        val raw = sp.getString(key, null)
        val out: List<String> = if (raw == null) def else try {
            val a = JSONArray(raw)
            List(a.length()) { a.getString(it) }
        } catch (e: Exception) {
            def
        }
        listCache[key] = out
        return out
    }

    private fun putList(key: String, v: List<String>) {
        listCache[key] = v
        sp.edit().putString(key, JSONArray(v).toString()).apply()
    }

    var learnChannels: List<String>
        get() = list("learnCh", DEFAULT_CHANNELS)
        set(v) = putList("learnCh", v)
    var sites: List<String>
        get() = list("sites", DEFAULT_SITES)
        set(v) = putList("sites", v)
    var blockedPkgs: List<String>
        get() = list("blockedPkgs", emptyList())
        set(v) = putList("blockedPkgs", v)
    var allowedPkgs: List<String>
        get() = list("allowedPkgs", emptyList())
        set(v) = putList("allowedPkgs", v)

    fun siteAllowed(host: String): Boolean {
        val h = host.lowercase()
        return sites.any { h == it || h.endsWith(".$it") }
    }

    // ---------- time budget ----------
    fun today(): String = LocalDate.now().toString()

    fun usage(kind: String, date: String = today()): Long = sp.getLong("u_${kind}_$date", 0L)

    fun addUsage(kind: String, ms: Long) {
        val k = "u_${kind}_${today()}"
        sp.edit().putLong(k, sp.getLong(k, 0L) + ms).apply()
    }

    fun resetFunToday() {
        sp.edit().remove("u_fun_${today()}").apply()
    }

    fun pruneOld() {
        val cutoff = LocalDate.now().minusDays(21)
        val ed = sp.edit()
        for (k in sp.all.keys) {
            if (k.startsWith("u_") && k.length > 10) {
                try {
                    if (LocalDate.parse(k.takeLast(10)).isBefore(cutoff)) ed.remove(k)
                } catch (e: Exception) { /* not a usage key */ }
            }
        }
        ed.apply()
    }

    /** Sunday is Funday: no limit on videos, and games / YouTube app can open (parent choices). */
    fun isFunday(): Boolean = fundayOn && LocalDate.now().dayOfWeek == DayOfWeek.SUNDAY

    fun funLeftMs(): Long =
        if (isFunday()) Long.MAX_VALUE
        else (funLimitMin * 60_000L - usage("fun")).coerceAtLeast(0L)

    /** Can a "Fun" video play right now? */
    fun funAllowed(): Boolean = isFunday() || (!learnOnly && funLeftMs() > 0)

    // ---------- exam countdown ----------
    fun examDaysLeft(): Int? {
        if (examDate.isBlank()) return null
        return try {
            ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(examDate)).toInt()
        } catch (e: Exception) {
            null
        }
    }

    // ---------- recent videos ----------
    fun recents(): List<Recent> {
        val raw = sp.getString("recents", null) ?: return emptyList()
        return try {
            val a = JSONArray(raw)
            List(a.length()) {
                val o = a.getJSONObject(it)
                Recent(o.getString("id"), o.getString("t"), o.getString("a"), o.getString("k"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addRecent(r: Recent) {
        val out = ArrayList<Recent>()
        out.add(r)
        for (x in recents()) if (x.id != r.id && out.size < 20) out.add(x)
        val a = JSONArray()
        for (x in out) a.put(JSONObject().put("id", x.id).put("t", x.title).put("a", x.author).put("k", x.kind))
        sp.edit().putString("recents", a.toString()).apply()
    }
}
