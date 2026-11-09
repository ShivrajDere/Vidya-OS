package com.vidya.os

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.provider.Settings
import java.time.LocalDate
import android.widget.LinearLayout

/** Everything a parent can change, behind the PIN. */
class ParentActivity : BaseActivity() {

    private var unlocked = false
    private var toSettings = false

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        page("Parent controls")
        askPin(onCancel = { finish() }) {
            unlocked = true
            render()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (unlocked) render()
    }

    override fun onResume() {
        super.onResume()
        toSettings = false
        if (unlocked) render()      // guard status may have changed in Settings
    }

    // leaving this screen (Home, Recents) locks it again, except when going to Android settings
    override fun onStop() {
        super.onStop()
        if (unlocked && !toSettings) finish()
    }

    private fun render() {
        val c = page("Parent controls")

        // ----- guard -----
        c.addView(sectionLabel("App guard"))
        val guard = card()
        val on = GuardService.isEnabled(this)
        guard.addView(listRow(
            if (on) "Guard is on" else "Guard is off",
            if (on) "Chrome, other browsers, the YouTube app and games are closed when the rules say so."
            else "Turn it on so Chrome, the YouTube app and games cannot be opened outside Vidya OS.",
            if (on) "Manage" else "Turn on", false
        ) {
            unlockSettings()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        guard.addView(listRow("Home app", "Make Vidya OS the home screen so the Home button comes here.", "Choose", false) {
            unlockSettings()
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        })
        c.addView(guard, lp(MATCH, WRAP))

        // ----- limits -----
        c.addView(sectionLabel("Fun time"))
        val lim = card()
        val limRow = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
        val val_ = tv("${Store.funLimitMin} min a day", 20f, Pal.fg, true)
        limRow.addView(button("- 5") { setLimit(Store.funLimitMin - 5) }, lpm(WRAP, WRAP, r = 10))
        limRow.addView(val_, lp(0, WRAP, 1f))
        limRow.addView(button("+ 5") { setLimit(Store.funLimitMin + 5) })
        lim.addView(limRow, lp(MATCH, WRAP))
        lim.addView(
            tv("Used today: ${fmtHm(Store.usage("fun"))} Fun, ${fmtHm(Store.usage("learn"))} study", 13f, Pal.muted),
            lpm(MATCH, WRAP, t = 8)
        )
        lim.addView(switchRow("Learning only", "Pause Fun videos (not on Sunday)", Store.learnOnly) { Store.learnOnly = it })
        lim.addView(button("Give back today's Fun time") {
            confirm("Reset today's Fun time?", "He gets the full ${Store.funLimitMin} minutes again today.", "Reset") {
                Store.resetFunToday()
                toast("Fun time reset for today.")
                render()
            }
        }, lpm(WRAP, WRAP, t = 6))
        c.addView(lim, lp(MATCH, WRAP))

        // ----- funday -----
        c.addView(sectionLabel("Sunday is Funday"))
        val fd = card()
        fd.addView(switchRow("Funday on Sundays", "No video limit all day", Store.fundayOn) { Store.fundayOn = it; render() })
        if (Store.fundayOn) {
            fd.addView(switchRow("Games open", "Installed games can be played on Sunday", Store.fundayGames) { Store.fundayGames = it })
            fd.addView(switchRow("YouTube app opens", "The real YouTube app, if installed", Store.fundayYoutubeApp) { Store.fundayYoutubeApp = it })
            fd.addView(switchRow("Other browsers open", "Chrome and similar", Store.fundayBrowsers) { Store.fundayBrowsers = it })
        }
        c.addView(fd, lp(MATCH, WRAP))

        // ----- exam -----
        c.addView(sectionLabel("MHT-CET countdown"))
        val ex = card()
        val d = Store.examDaysLeft()
        ex.addView(listRow(
            if (Store.examDate.isBlank()) "Exam date not set" else Store.examDate,
            if (d == null) "The CET Cell has not confirmed the 2027 dates when this was built. Set the date once it is announced."
            else if (d >= 0) "$d days to go" else "This date has passed",
            "Set date", false
        ) { pickDate() })
        if (Store.examDate.isNotBlank()) ex.addView(button("Clear date") { Store.examDate = ""; render() }, lpm(WRAP, WRAP, t = 6))
        c.addView(ex, lp(MATCH, WRAP))

        // ----- learn channels -----
        c.addView(sectionLabel("Learning channels (never use Fun time)"))
        val ch = card()
        for (name in Store.learnChannels) {
            ch.addView(listRow(name, null, "Remove", true) { Store.learnChannels = Store.learnChannels - name; render() })
        }
        ch.addView(button("+ Add a channel") {
            promptText("Channel name", "As shown on YouTube") { n ->
                if (n !in Store.learnChannels) Store.learnChannels = Store.learnChannels + n
                render()
            }
        }, lpm(WRAP, WRAP, t = 6))
        ch.addView(tv("A video counts as learning when its channel name contains one of these words.", 13f, Pal.muted), lpm(MATCH, WRAP, t = 8))
        c.addView(ch, lp(MATCH, WRAP))

        // ----- sites -----
        c.addView(sectionLabel("Approved study sites"))
        val st = card()
        for (host in Store.sites) {
            st.addView(listRow(Store.siteLabel(host), host, "Remove", true) { Store.sites = Store.sites - host; render() })
        }
        st.addView(button("+ Add a site") {
            promptText("Website", "example.com") { raw ->
                val host = raw.lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore('/')
                if (host.contains('.') && host !in Store.sites) Store.sites = Store.sites + host
                render()
            }
        }, lpm(WRAP, WRAP, t = 6))
        c.addView(st, lp(MATCH, WRAP))

        // ----- apps -----
        c.addView(sectionLabel("Games and blocked apps"))
        val ap = card()
        val apps = AppsRepo.cache ?: AppsRepo.load(this)
        var any = false
        for (a in apps) {
            val isGame = Policy.isGame(packageManager, a.pkg)
            val blocked = a.pkg in Store.blockedPkgs
            val allowed = a.pkg in Store.allowedPkgs
            if (blocked) {
                any = true
                ap.addView(listRow(a.label, "Blocked by a parent", "Unblock", false) { Store.blockedPkgs = Store.blockedPkgs - a.pkg; render() })
            } else if (isGame) {
                any = true
                if (allowed) ap.addView(listRow(a.label, "Game, allowed to open", "Treat as game", false) { Store.allowedPkgs = Store.allowedPkgs - a.pkg; render() })
                else ap.addView(listRow(a.label, "Game, closed except on Funday", "Not a game", false) { Store.allowedPkgs = Store.allowedPkgs + a.pkg; render() })
            }
        }
        if (!any) ap.addView(tv("No games or blocked apps right now.", 14f, Pal.muted))
        ap.addView(button("+ Block an app") { pickAppToBlock(apps) }, lpm(WRAP, WRAP, t = 8))
        c.addView(ap, lp(MATCH, WRAP))

        // ----- week -----
        c.addView(sectionLabel("Last 7 days"))
        val wk = card()
        for (i in 0..6) {
            val day = LocalDate.now().minusDays(i.toLong())
            val key = day.toString()
            val label = if (i == 0) "Today" else day.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
            wk.addView(listRow(label, "Fun ${fmtHm(Store.usage("fun", key))}   -   Study ${fmtHm(Store.usage("learn", key))}"))
        }
        c.addView(wk, lp(MATCH, WRAP))

        // ----- security -----
        c.addView(sectionLabel("Security"))
        val sec = card()
        sec.addView(listRow("Change PIN", "Now a ${Store.pin.length}-digit PIN", "Change", false) { changePin() })
        sec.addView(listRow("Android settings", "Opens for 10 minutes, then closes again", "Open", false) {
            unlockSettings()
            startActivity(Intent(Settings.ACTION_SETTINGS))
        })
        c.addView(sec, lp(MATCH, WRAP))
        c.addView(button("Lock parent controls", primary = true) { finish() }, lpm(WRAP, WRAP, t = 20))
    }

    private fun unlockSettings() {
        toSettings = true
        Store.settingsUnlockedUntil = System.currentTimeMillis() + 10 * 60_000L
    }

    private fun setLimit(v: Int) {
        Store.funLimitMin = v.coerceIn(5, 180)
        render()
    }

    private fun pickDate() {
        val now = LocalDate.now()
        DatePickerDialog(this, { _, y, m, d ->
            Store.examDate = LocalDate.of(y, m + 1, d).toString()
            render()
        }, now.year, now.monthValue - 1, now.dayOfMonth).show()
    }

    private fun pickAppToBlock(apps: List<AppInfo>) {
        val list = apps.filter { it.pkg !in Store.blockedPkgs }
        AlertDialog.Builder(this)
            .setTitle("Block which app?")
            .setItems(list.map { it.label }.toTypedArray()) { _, i ->
                Store.blockedPkgs = Store.blockedPkgs + list[i].pkg
                render()
            }
            .show()
    }

    private fun changePin() {
        promptText("New 4-digit PIN", "0000") { p ->
            if (Regex("^\\d{4}$").matches(p)) {
                Store.pin = p
                toast("PIN changed.")
                render()
            } else toast("The PIN needs exactly 4 digits.")
        }
    }
}
