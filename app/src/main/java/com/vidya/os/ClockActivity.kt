package com.vidya.os

import android.Manifest
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Stopwatch state is kept here so it survives turning the tablet sideways. */
object SwState {
    var running = false
    var startAt = 0L
    var acc = 0L
    val laps = ArrayList<Long>()

    fun elapsed(): Long = acc + if (running) SystemClock.elapsedRealtime() - startAt else 0L
    fun toggle() {
        if (running) { acc += SystemClock.elapsedRealtime() - startAt; running = false }
        else { startAt = SystemClock.elapsedRealtime(); running = true }
    }
    fun reset() { running = false; acc = 0L; laps.clear() }
}

class ClockActivity : BaseActivity() {

    private val h = Handler(Looper.getMainLooper())
    private var tab = 0
    private var live: (() -> Unit)? = null
    private val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    private val loop = object : Runnable {
        override fun run() {
            live?.invoke()
            h.postDelayed(this, 100)
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        render()
    }

    override fun onResume() {
        super.onResume()
        h.removeCallbacks(loop)
        h.post(loop)
    }

    override fun onPause() {
        h.removeCallbacks(loop)
        super.onPause()
    }

    private fun render() {
        live = null
        val c = page("Clock")
        val tabs = LinearLayout(this)
        listOf("Alarm", "Timer", "Stopwatch", "World").forEachIndexed { i, name ->
            tabs.addView(chip(name, tab == i) { tab = i; render() }, lpm(WRAP, WRAP, r = 8))
        }
        val hs = android.widget.HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        hs.addView(tabs)
        c.addView(hs, lp(MATCH, WRAP))
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        c.addView(body, lpm(MATCH, WRAP, t = 14))
        when (tab) {
            0 -> alarmTab(body)
            1 -> timerTab(body)
            2 -> stopwatchTab(body)
            else -> worldTab(body)
        }
    }

    // ============ alarms ============
    private fun fmtTime(hh: Int, mm: Int): String =
        if (DateFormat.is24HourFormat(this)) "%02d:%02d".format(hh, mm)
        else "%d:%02d %s".format(if (hh % 12 == 0) 12 else hh % 12, mm, if (hh < 12) "AM" else "PM")

    private fun daysText(d: Int): String = when (d) {
        0 -> "Once"
        127 -> "Every day"
        31 -> "Weekdays"
        96 -> "Weekends"
        else -> dayNames.filterIndexed { i, _ -> ((d shr i) and 1) == 1 }.joinToString(", ")
    }

    private fun alarmTab(c: LinearLayout) {
        c.addView(button("+ Add alarm", primary = true) { editAlarm(null) }, lp(WRAP, WRAP))
        val list = AlarmStore.all().sortedBy { it.hour * 60 + it.minute }
        if (list.isEmpty()) {
            c.addView(tv("No alarms yet.", 15f, Pal.muted), lpm(MATCH, WRAP, t = 20))
            return
        }
        for (a in list) {
            val row = card().apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = ripple(shape(Pal.surface, 18, Pal.line, 1))
                isClickable = true
                setOnClickListener { editAlarm(a) }
            }
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(tv(fmtTime(a.hour, a.minute), 32f, if (a.enabled) Pal.fg else Pal.muted, true))
            val sub = (if (a.label.isNotBlank()) a.label + "  -  " else "") + daysText(a.days)
            col.addView(tv(sub, 13f, Pal.muted))
            row.addView(col, lp(0, WRAP, 1f))
            row.addView(button("Delete", danger = true) {
                confirm("Delete this alarm?", fmtTime(a.hour, a.minute) + "  " + daysText(a.days), "Delete") {
                    AlarmScheduler.cancel(this, a.id)
                    AlarmStore.remove(a.id)
                    render()
                }
            }, lpm(WRAP, WRAP, r = 10))
            val sw = Switch(this).apply { isChecked = a.enabled }
            sw.setOnCheckedChangeListener { _, on ->
                val n = a.copy(enabled = on)
                AlarmStore.upsert(n)
                if (on) {
                    AlarmScheduler.schedule(this, n)
                    toast(ringsIn(AlarmScheduler.nextTrigger(n)))
                } else AlarmScheduler.cancel(this, a.id)
                render()
            }
            row.addView(sw)
            c.addView(row, lpm(MATCH, WRAP, t = 10))
        }
    }

    private fun ringsIn(at: Long): String {
        val m = ((at - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0)
        val d = m / 1440
        val hh = (m % 1440) / 60
        val mm = m % 60
        return "Rings in " + (if (d > 0) "${d}d " else "") + (if (hh > 0 || d > 0) "${hh}h " else "") + "${mm}m"
    }

    private fun editAlarm(existing: AlarmItem?) {
        val base = existing ?: AlarmItem(AlarmStore.nextId(), 7, 0, 0, "", true)
        TimePickerDialog(this, { _, hh, mm -> alarmOptions(base.copy(hour = hh, minute = mm, enabled = true)) },
            base.hour, base.minute, DateFormat.is24HourFormat(this)).show()
    }

    private fun alarmOptions(a: AlarmItem) {
        var days = a.days
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val label = EditText(this).apply {
            hint = "Label (optional)"
            setText(a.label)
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        box.addView(label, lp(MATCH, WRAP))
        box.addView(tv("Repeat (none selected = once)", 13f, Pal.muted), lpm(MATCH, WRAP, t = 14, b = 6))
        val row = LinearLayout(this)
        val hs = android.widget.HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        dayNames.forEachIndexed { i, n ->
            val ch = chip(n, ((days shr i) and 1) == 1) { }
            ch.setOnClickListener {
                days = days xor (1 shl i)
                val on = ((days shr i) and 1) == 1
                ch.setTextColor(if (on) Pal.accentInk else Pal.fg)
                ch.background = ripple(shape(if (on) Pal.accent else Pal.surface2, 20, Pal.line, if (on) 0 else 1))
            }
            row.addView(ch, lpm(WRAP, WRAP, r = 6))
        }
        hs.addView(row)
        box.addView(hs, lp(MATCH, WRAP))
        AlertDialog.Builder(this)
            .setTitle(fmtTime(a.hour, a.minute))
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val n = a.copy(days = days, label = label.text.toString().trim())
                AlarmStore.upsert(n)
                AlarmScheduler.schedule(this, n)
                toast(ringsIn(AlarmScheduler.nextTrigger(n)))
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ============ timer ============
    private fun timerTab(c: LinearLayout) {
        val big = tv("", 64f, Pal.fg, true).apply { gravity = Gravity.CENTER }
        c.addView(big, lpm(MATCH, WRAP, t = 8, b = 8))
        live = { big.text = fmtTimer(TimerState.left()) }
        live?.invoke()

        if (!TimerState.running()) {
            val presets = LinearLayout(this)
            val hs = android.widget.HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
            listOf(1, 5, 10, 15, 25, 45, 60).forEach { m ->
                presets.addView(chip("$m min", TimerState.total == m * 60_000L) {
                    TimerState.setTotal(this, m * 60_000L)
                    render()
                }, lpm(WRAP, WRAP, r = 8))
            }
            presets.addView(chip("Custom") {
                promptText("Timer minutes", "e.g. 20") { t ->
                    val m = t.toIntOrNull()
                    if (m == null || m < 1 || m > 600) toast("Enter 1 to 600 minutes.")
                    else { TimerState.setTotal(this, m * 60_000L); render() }
                }
            })
            hs.addView(presets)
            c.addView(hs, lpm(MATCH, WRAP, b = 14))
        }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
        row.addView(button(if (TimerState.running()) "Pause" else "Start", primary = true) {
            if (TimerState.running()) TimerState.pause(this) else TimerState.start(this)
            render()
        }, lpm(dp(130), WRAP, r = 10))
        row.addView(button("Reset") { TimerState.reset(this); render() }, lp(dp(130), WRAP))
        c.addView(row, lp(MATCH, WRAP))
        c.addView(tv("The timer rings even if you leave the app.", 13f, Pal.muted).apply { gravity = Gravity.CENTER }, lpm(MATCH, WRAP, t = 14))
    }

    private fun fmtTimer(ms: Long): String {
        val s = (ms + 999) / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
    }

    // ============ stopwatch ============
    private fun stopwatchTab(c: LinearLayout) {
        val big = tv("", 64f, Pal.fg, true).apply { gravity = Gravity.CENTER }
        c.addView(big, lpm(MATCH, WRAP, t = 8, b = 12))
        val lapBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        live = { big.text = fmtSw(SwState.elapsed()) }
        live?.invoke()
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
        row.addView(button(if (SwState.running) "Stop" else "Start", primary = true) { SwState.toggle(); render() }, lpm(dp(110), WRAP, r = 8))
        row.addView(button("Lap") { if (SwState.running) { SwState.laps.add(SwState.elapsed()); render() } }, lpm(dp(110), WRAP, r = 8))
        row.addView(button("Reset") { SwState.reset(); render() }, lp(dp(110), WRAP))
        c.addView(row, lp(MATCH, WRAP))
        c.addView(lapBox, lpm(MATCH, WRAP, t = 16))
        var prev = 0L
        val rows = SwState.laps.mapIndexed { i, t -> val d = t - prev; prev = t; Triple(i + 1, d, t) }
        for ((n, d, t) in rows.reversed()) {
            val r = LinearLayout(this).apply { setPadding(dp(4), dp(8), dp(4), dp(8)) }
            r.addView(tv("Lap $n", 15f, Pal.muted), lp(0, WRAP, 1f))
            r.addView(tv(fmtSw(d), 15f, Pal.fg, true), lp(0, WRAP, 1f))
            r.addView(tv(fmtSw(t), 15f, Pal.muted).apply { gravity = Gravity.END }, lp(0, WRAP, 1f))
            lapBox.addView(r, lp(MATCH, WRAP))
        }
    }

    private fun fmtSw(ms: Long): String {
        val s = ms / 1000
        return "%02d:%02d.%d".format(s / 60, s % 60, (ms % 1000) / 100)
    }

    // ============ world clock ============
    private val allZones = listOf(
        "Asia/Kolkata", "Europe/London", "America/New_York", "America/Los_Angeles", "America/Chicago", "America/Toronto",
        "Asia/Dubai", "Asia/Singapore", "Asia/Tokyo", "Asia/Shanghai", "Asia/Karachi", "Asia/Dhaka", "Asia/Kathmandu",
        "Asia/Colombo", "Europe/Paris", "Europe/Berlin", "Europe/Moscow", "Australia/Sydney", "Pacific/Auckland",
        "Africa/Cairo", "Africa/Johannesburg", "America/Sao_Paulo"
    )

    private fun zones(): List<String> {
        val raw = Store.raw().getString("zones", null)
        if (raw != null) try {
            val a = JSONArray(raw)
            return List(a.length()) { a.getString(it) }
        } catch (e: Exception) { }
        return listOf("Asia/Kolkata", "Europe/London", "America/New_York", "Asia/Dubai", "Asia/Singapore", "Asia/Tokyo", "Australia/Sydney")
    }

    private fun saveZones(l: List<String>) = Store.raw().edit().putString("zones", JSONArray(l).toString()).apply()

    private fun worldTab(c: LinearLayout) {
        c.addView(button("+ Add city", primary = true) {
            val have = zones()
            val options = allZones.filter { it !in have }
            if (options.isEmpty()) { toast("All cities are already added."); return@button }
            AlertDialog.Builder(this)
                .setTitle("Add a city")
                .setItems(options.map { cityName(it) }.toTypedArray()) { _, i ->
                    saveZones(have + options[i])
                    render()
                }
                .show()
        }, lp(WRAP, WRAP))

        val tvs = ArrayList<Triple<TextView, TextView, String>>()
        for (z in zones()) {
            val row = card().apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(tv(cityName(z), 17f, Pal.fg, true))
            val sub = tv("", 13f, Pal.muted)
            col.addView(sub)
            row.addView(col, lp(0, WRAP, 1f))
            val time = tv("", 28f, Pal.fg, true)
            row.addView(time)
            row.setOnLongClickListener {
                confirm("Remove ${cityName(z)}?", "", "Remove") { saveZones(zones().filter { it != z }); render() }
                true
            }
            tvs.add(Triple(time, sub, z))
            c.addView(row, lpm(MATCH, WRAP, t = 10))
        }
        c.addView(tv("Long-press a city to remove it.", 13f, Pal.muted), lpm(MATCH, WRAP, t = 12))

        val fmt = SimpleDateFormat(if (DateFormat.is24HourFormat(this)) "HH:mm" else "h:mm a", Locale.getDefault())
        live = {
            val now = Date()
            val local = TimeZone.getDefault()
            for ((time, sub, z) in tvs) {
                val tz = TimeZone.getTimeZone(z)
                fmt.timeZone = tz
                time.text = fmt.format(now)
                val diff = (tz.getOffset(now.time) - local.getOffset(now.time)) / 60_000
                val dayFmt = SimpleDateFormat("EEE d MMM", Locale.getDefault()).apply { timeZone = tz }
                val off = if (diff == 0) "same time as here" else {
                    val a = Math.abs(diff)
                    (if (diff > 0) "+" else "-") + (a / 60) + "h" + (if (a % 60 != 0) " ${a % 60}m" else "") + " from here"
                }
                sub.text = dayFmt.format(now) + "  -  " + off
            }
        }
        live?.invoke()
    }

    private fun cityName(id: String) = id.substringAfter('/').replace('_', ' ')
}
