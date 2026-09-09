package me.phh.treble.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Color
import android.media.audiofx.Visualizer
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.preference.PreferenceManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.File

// Per-event Aura Sync lighting: incoming call / charging / notification /
// music, layered on top of the base "Screen on" color from Rog.kt. Each
// scenario's own (enabled, color, mode, speed) profile lives under
// RogSettings.eventScenarios - ASUS's own real AuraLightManager confirms
// exactly this shape via its setScenarioEffect(scenario, active, color,
// mode, speed) API.
//
// Unlike Rog.kt (pure preference-change-driven), these are OS-event-driven:
// a TelephonyCallback for ringing, a dynamically-registered
// BroadcastReceiver for charging, and RogNotificationListenerService for
// notification/music (both need an enabled NotificationListenerService -
// the one piece of this feature needing a user-granted permission, since
// it can't be silently self-granted like everything else in this
// system-signed app).
object RogEvents : EntryStartup {
    private lateinit var appContext: Context

    // Highest-priority-first, matching RogSettings.eventScenarios: whichever
    // of these is both user-enabled and currently "active" wins ties are
    // broken by that ordering (a ringing call always beats a notification,
    // which beats music, which beats charging).
    private val active = mutableMapOf(
        "ringing" to false,
        "notification" to false,
        "music" to false,
        "charging" to false
    )

    private var currentScenario: String? = null
    private val notificationClearHandler = Handler(Looper.getMainLooper())
    private var notificationClearRunnable: Runnable? = null

    // ---- Priority resolution -----------------------------------------

    @Synchronized
    private fun setActive(scenario: String, isActive: Boolean) {
        if (!::appContext.isInitialized) return
        active[scenario] = isActive
        recompute()
    }

    private fun recompute() {
        val sp = PreferenceManager.getDefaultSharedPreferences(appContext)
        val winner = RogSettings.eventScenarios.firstOrNull { (id, keys, _) ->
            active[id] == true && sp.getBoolean(keys.enable, false)
        }
        if (winner == null) {
            if (currentScenario != null) {
                stopMusicVisualizerIfNeeded(currentScenario)
                currentScenario = null
                Rog.reapplyBaseAura(appContext)
            }
            return
        }
        val (id, keys, _) = winner
        if (id != currentScenario) {
            stopMusicVisualizerIfNeeded(currentScenario)
            currentScenario = id
        }
        if (id == "music") {
            startMusicVisualizerIfNeeded(sp, keys)
        } else {
            val r = sp.getString(keys.red, "255")?.toIntOrNull() ?: 255
            val g = sp.getString(keys.green, "255")?.toIntOrNull() ?: 255
            val b = sp.getString(keys.blue, "255")?.toIntOrNull() ?: 255
            val mode = sp.getString(keys.mode, "1")?.toIntOrNull() ?: 1
            val speed = sp.getString(keys.speed, "1")?.toIntOrNull() ?: 1
            writeDirect(r, g, b, mode, speed)
        }
    }

    // Writes straight to the aura sysfs zones (bypassing SharedPreferences)
    // so an event override never clobbers the user's persisted base color.
    private fun writeDirect(r: Int, g: Int, b: Int, mode: Int, speed: Int) {
        for (base in Rog.auraZones) {
            if (!File(base).exists()) continue
            Rog.writeToFileNofail("$base/led_on", "1")
            Rog.writeToFileNofail("$base/red_pwm", r.toString())
            Rog.writeToFileNofail("$base/green_pwm", g.toString())
            Rog.writeToFileNofail("$base/blue_pwm", b.toString())
            Rog.writeToFileNofail("$base/mode", mode.toString())
            if (speed in setOf(0, 1, 2, 254, 255)) Rog.writeToFileNofail("$base/speed", speed.toString())
            Rog.writeToFileNofail("$base/apply", "1")
        }
    }

    // ---- Ringing (incoming call) --------------------------------------

    @RequiresApi(Build.VERSION_CODES.S)
    private val telephonyCallback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) {
            setActive("ringing", state == TelephonyManager.CALL_STATE_RINGING)
        }
    }

    // ---- Charging -------------------------------------------------------

    private val chargingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> setActive("charging", true)
                Intent.ACTION_POWER_DISCONNECTED -> setActive("charging", false)
            }
        }
    }

    // ---- Notification / Music (both driven by RogNotificationListenerService) --

    // A notification is a one-off event, not a continuous state - flash the
    // configured color for a few seconds then let priority fall back down.
    fun onNotificationPosted() {
        setActive("notification", true)
        notificationClearRunnable?.let { notificationClearHandler.removeCallbacks(it) }
        val r = Runnable { setActive("notification", false) }
        notificationClearRunnable = r
        notificationClearHandler.postDelayed(r, 3000)
    }

    fun onMusicPlaybackActiveChanged(isPlaying: Boolean) {
        setActive("music", isPlaying)
    }

    // ---- Music: real-time peak-reactive hue, matching ASUS's own real
    // music-lighting algorithm exactly: Visualizer(session 0) in
    // MEASUREMENT_MODE_PEAK_RMS, and on each waveform capture, if the
    // measured peak+RMS aren't both silence AND the peak changed since the
    // last capture, advance a running hue by 6 degrees (wrapping at 360)
    // and re-render. That's genuinely the whole algorithm - ASUS's own FFT
    // capture callback is an empty no-op, there's no real spectrum
    // analysis involved. The user's own configured Music color supplies
    // the saturation/brightness; only hue animates automatically.
    private var visualizer: Visualizer? = null
    private var musicHue = 0f
    private var lastPeak = Int.MIN_VALUE

    private fun startMusicVisualizerIfNeeded(sp: SharedPreferences, keys: RogSettings.AuraKeySet) {
        if (visualizer != null) return
        val r = sp.getString(keys.red, "255")?.toIntOrNull() ?: 255
        val g = sp.getString(keys.green, "255")?.toIntOrNull() ?: 255
        val b = sp.getString(keys.blue, "255")?.toIntOrNull() ?: 255
        val hsv = FloatArray(3)
        Color.RGBToHSV(r, g, b, hsv)
        musicHue = hsv[0]
        val sat = hsv[1]
        val value = hsv[2]
        lastPeak = Int.MIN_VALUE
        try {
            val v = Visualizer(0)
            v.setMeasurementMode(Visualizer.MEASUREMENT_MODE_PEAK_RMS)
            v.setCaptureSize(128)
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(vis: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                    val peakRms = Visualizer.MeasurementPeakRms()
                    if (v.getMeasurementPeakRms(peakRms) != Visualizer.SUCCESS) return
                    if (peakRms.mRms == SILENCE_MB && peakRms.mPeak == SILENCE_MB) return
                    if (peakRms.mPeak == lastPeak) return
                    lastPeak = peakRms.mPeak
                    musicHue = (musicHue + 6f) % 360f
                    val color = Color.HSVToColor(floatArrayOf(musicHue, sat, value))
                    // mode 1 (Static) so the MCU just displays whatever
                    // color we push each capture, instead of fighting it
                    // with its own animation.
                    writeDirect(Color.red(color), Color.green(color), Color.blue(color), 1, 1)
                }

                override fun onFftDataCapture(vis: Visualizer?, fft: ByteArray?, samplingRate: Int) {}
            }, Visualizer.getMaxCaptureRate() / 2, true, false)
            v.setEnabled(true)
            visualizer = v
        } catch (t: Throwable) {
            Log.d("PHH", "RogEvents: failed starting music Visualizer", t)
        }
    }

    private fun stopMusicVisualizerIfNeeded(previousScenario: String?) {
        if (previousScenario != "music") return
        try {
            visualizer?.release()
        } catch (t: Throwable) { }
        visualizer = null
    }

    // ---- Startup ----------------------------------------------------------

    override fun startup(ctxt: Context) {
        if (!RogSettings.enabled(ctxt)) return
        appContext = ctxt

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val handler = Handler(HandlerThread("RogEventsThread").apply { start() }.looper)
                val tm = ctxt.getSystemService(TelephonyManager::class.java)
                tm.registerTelephonyCallback({ r -> handler.post(r) }, telephonyCallback)
            }
        } catch (t: Throwable) {
            Log.d("PHH", "RogEvents: failed registering telephony callback", t)
        }

        try {
            ctxt.registerReceiver(chargingReceiver, IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            })
        } catch (t: Throwable) {
            Log.d("PHH", "RogEvents: failed registering charging receiver", t)
        }

        Log.d("PHH", "RogEvents started")
    }

    private const val SILENCE_MB = -9600
}
