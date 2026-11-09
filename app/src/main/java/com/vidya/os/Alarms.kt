package com.vidya.os

import android.app.Activity
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.format.DateFormat
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date

/** days: bit 0 = Monday ... bit 6 = Sunday. 0 means "once". */
data class AlarmItem(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val days: Int,
    val label: String,
    val enabled: Boolean
)

object AlarmStore {
    fun all(): List<AlarmItem> {
        val raw = Store.raw().getString("alarms", null) ?: return emptyList()
        return try {
            val a = JSONArray(raw)
            List(a.length()) {
                val o = a.getJSONObject(it)
                AlarmItem(o.getInt("id"), o.getInt("h"), o.getInt("m"), o.getInt("d"), o.optString("l"), o.optBoolean("e", true))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save(list: List<AlarmItem>) {
        val a = JSONArray()
        for (x in list) a.put(JSONObject().put("id", x.id).put("h", x.hour).put("m", x.minute).put("d", x.days).put("l", x.label).put("e", x.enabled))
        Store.raw().edit().putString("alarms", a.toString()).apply()
    }

    fun get(id: Int): AlarmItem? = all().firstOrNull { it.id == id }

    fun upsert(a: AlarmItem) {
        val l = all().toMutableList()
        val i = l.indexOfFirst { it.id == a.id }
        if (i >= 0) l[i] = a else l.add(a)
        save(l)
    }

    fun remove(id: Int) = save(all().filter { it.id != id })

    fun nextId(): Int = (all().maxOfOrNull { it.id } ?: 0) + 1
}

/** Countdown timer. State lives in preferences so it keeps running when the app is closed. */
object TimerState {
    private val sp get() = Store.raw()

    var total: Long
        get() = sp.getLong("t_total", 10 * 60_000L)
        set(v) = sp.edit().putLong("t_total", v).apply()
    var endAt: Long
        get() = sp.getLong("t_end", 0L)
        set(v) = sp.edit().putLong("t_end", v).apply()
    private var paused: Long
        get() = sp.getLong("t_left", -1L)
        set(v) = sp.edit().putLong("t_left", v).apply()

    fun running(): Boolean = endAt > System.currentTimeMillis()

    fun left(): Long = when {
        endAt > 0L -> (endAt - System.currentTimeMillis()).coerceAtLeast(0L)
        paused >= 0L -> paused
        else -> total
    }

    fun start(ctx: Context) {
        val l = left().takeIf { it > 0 } ?: total
        endAt = System.currentTimeMillis() + l
        AlarmScheduler.scheduleAt(ctx, AlarmScheduler.TIMER_ID, endAt, "Timer finished")
    }

    fun pause(ctx: Context) {
        paused = left()
        endAt = 0L
        AlarmScheduler.cancel(ctx, AlarmScheduler.TIMER_ID)
    }

    fun reset(ctx: Context) {
        endAt = 0L
        paused = -1L
        AlarmScheduler.cancel(ctx, AlarmScheduler.TIMER_ID)
    }

    fun finished() {
        endAt = 0L
        paused = -1L
    }

    fun setTotal(ctx: Context, ms: Long) {
        reset(ctx)
        total = ms
    }
}

object AlarmScheduler {
    const val TIMER_ID = -1
    const val SNOOZE_ID = -2

    private fun am(c: Context) = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun firePi(c: Context, id: Int, label: String?): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java).setAction("com.vidya.os.ALARM").putExtra("id", id)
        if (label != null) i.putExtra("label", label)
        return PendingIntent.getBroadcast(c, id, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun nextTrigger(a: AlarmItem): Long {
        val now = LocalDateTime.now()
        var t = now.withHour(a.hour).withMinute(a.minute).withSecond(0).withNano(0)
        if (a.days == 0) {
            if (!t.isAfter(now)) t = t.plusDays(1)
        } else {
            var n = 0
            while (n < 8 && (!t.isAfter(now) || ((a.days shr (t.dayOfWeek.value - 1)) and 1) == 0)) {
                t = t.plusDays(1)
                n++
            }
        }
        return t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun scheduleAt(c: Context, id: Int, at: Long, label: String?) {
        val show = PendingIntent.getActivity(c, 0, Intent(c, ClockActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        // setAlarmClock is exact and needs no special permission
        am(c).setAlarmClock(AlarmManager.AlarmClockInfo(at, show), firePi(c, id, label))
    }

    fun schedule(c: Context, a: AlarmItem) = scheduleAt(c, a.id, nextTrigger(a), null)

    fun cancel(c: Context, id: Int) {
        val pi = firePi(c, id, null)
        am(c).cancel(pi)
        pi.cancel()
    }

    fun snooze(c: Context, minutes: Int) =
        scheduleAt(c, SNOOZE_ID, System.currentTimeMillis() + minutes * 60_000L, "Snoozed alarm")

    fun rescheduleAll(c: Context) {
        for (a in AlarmStore.all()) if (a.enabled) schedule(c, a)
        val end = TimerState.endAt
        if (end > System.currentTimeMillis()) scheduleAt(c, TIMER_ID, end, "Timer finished") else if (end > 0) TimerState.finished()
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        val id = i.getIntExtra("id", 0)
        val title: String
        if (id >= 0) {
            val a = AlarmStore.get(id) ?: return
            title = a.label.ifBlank { "Alarm" }
            if (a.days == 0) AlarmStore.upsert(a.copy(enabled = false)) else AlarmScheduler.schedule(ctx, a)
        } else {
            title = i.getStringExtra("label") ?: "Timer finished"
            if (id == AlarmScheduler.TIMER_ID) TimerState.finished()
        }
        ctx.startForegroundService(Intent(ctx, AlarmService::class.java).putExtra("title", title))
    }
}

/** Alarms are lost when a tablet restarts, so put them back. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        AlarmScheduler.rescheduleAll(ctx)
    }
}

class AlarmService : Service() {
    private var player: MediaPlayer? = null
    private var vib: Vibrator? = null
    private val h = Handler(Looper.getMainLooper())

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val ch = NotificationChannel(CH, "Alarms and timers", NotificationManager.IMPORTANCE_HIGH)
        ch.setSound(null, null)
        ch.enableVibration(false)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        when (i?.action) {
            ACTION_STOP -> {
                stopAll()
                return START_NOT_STICKY
            }
            ACTION_SNOOZE -> {
                AlarmScheduler.snooze(this, 10)
                stopAll()
                return START_NOT_STICKY
            }
        }
        val title = i?.getStringExtra("title") ?: "Alarm"
        startForeground(NOTIF, notification(title))
        startSound()
        h.removeCallbacksAndMessages(null)
        h.postDelayed({ stopAll() }, 60_000L)   // rings for one minute
        return START_NOT_STICKY
    }

    private fun notification(title: String): Notification {
        val imm = PendingIntent.FLAG_IMMUTABLE
        val full = PendingIntent.getActivity(
            this, 0, Intent(this, AlarmRingActivity::class.java).putExtra("title", title),
            imm or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(this, 1, Intent(this, AlarmService::class.java).setAction(ACTION_STOP), imm)
        val snz = PendingIntent.getService(this, 2, Intent(this, AlarmService::class.java).setAction(ACTION_SNOOZE), imm)
        @Suppress("DEPRECATION")
        return Notification.Builder(this, CH)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(DateFormat.getTimeFormat(this).format(Date()))
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(full)
            .setFullScreenIntent(full, true)
            .addAction(0, "Snooze 10 min", snz)
            .addAction(0, "Dismiss", stop)
            .build()
    }

    private fun startSound() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            player?.release()
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmService, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) { /* no sound available */ }
        try {
            vib = getSystemService(Vibrator::class.java)
            vib?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 600), 0))
        } catch (e: Exception) { }
    }

    private fun stopAll() {
        h.removeCallbacksAndMessages(null)
        try { player?.stop() } catch (e: Exception) { }
        player?.release()
        player = null
        vib?.cancel()
        @Suppress("DEPRECATION")
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        stopAll()
        super.onDestroy()
    }

    companion object {
        const val CH = "alarm"
        const val NOTIF = 77
        const val ACTION_STOP = "com.vidya.os.STOP"
        const val ACTION_SNOOZE = "com.vidya.os.SNOOZE"
    }
}

/** Full-screen "wake up" screen that shows over the lock screen. */
class AlarmRingActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        Pal.refresh(this)
        window.statusBarColor = Color.parseColor("#0E1118")
        window.navigationBarColor = Color.parseColor("#0E1118")

        val title = intent.getStringExtra("title") ?: "Alarm"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0E1118"))
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        root.addView(tv(DateFormat.getTimeFormat(this).format(Date()), 72f, Color.WHITE, true))
        root.addView(tv(title, 24f, Color.parseColor("#C9CEDD")), lpm(WRAP, WRAP, t = 8, b = 40))
        root.addView(button("Snooze 10 min") { send(AlarmService.ACTION_SNOOZE) }, lpm(dp(260), WRAP))
        root.addView(button("Dismiss", primary = true) { send(AlarmService.ACTION_STOP) }, lpm(dp(260), WRAP, t = 14))
        setContentView(root)
    }

    private fun send(action: String) {
        startService(Intent(this, AlarmService::class.java).setAction(action))
        finish()
    }
}
