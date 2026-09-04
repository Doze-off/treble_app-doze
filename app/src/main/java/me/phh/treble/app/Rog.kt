package me.phh.treble.app

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.util.Log
import java.io.File

// ASUS ROG Phone 5 / 5s (ASUS_I005D / ASUS_I005_1) gaming-hardware controls.
//
// Unlike Asus.kt (dt2w/glove/fp-wake - all either a persist.* prop or a call
// into the stock vendor.ims.zenmotion HIDL service), these are ROG-specific
// accessory/RGB MCUs with no HIDL/AIDL service at all. Userspace controls
// them purely through sysfs, the same way Nubia.kt drives RedMagic's
// fan/RGB/shoulder-button hardware. Every attribute name and sysfs path
// below is taken directly from ASUS's own published GPL kernel source
// (ASUS_I005_1-33.0210.0210.200-kernel-src.tar.gz,
// drivers/aura_sync/{rog5_inbox,ms51_phone,ms51_side,ms51_backcover}.c,
// referenced from the real ZS673KS device-tree overlay) - not guessed or
// ported from another device:
//   - LED classdevs "aura_sync"/"aura_sync_side"/"aura_backcover" (built-in
//     RGB, always present) and "aura_inbox" (AeroActive Cooler 5 accessory,
//     HID driver "rog5_inbox" - only enumerated while attached) all register
//     the same attribute_group: red_pwm/green_pwm/blue_pwm/led_on/mode/apply.
//   - mode_store() writes straight to the MCU immediately.
//   - apply_store() is what actually latches buffered red/green/blue/led_on
//     state to the MCU over i2c - confirmed from its own log line
//     ("Send apply. RGB:%d %d %d, mode:%d, speed:%d, led_on:%d, led2_on:%d").
//   - rog5_inbox's fan_rpm_store() takes a raw 0-255 setpoint (not literal
//     RPM despite the name) and rejects anything outside that range.
//
// NOT implemented here: the built-in on-frame AirTrigger ultrasonic sensors,
// and the AeroActive Cooler's own shoulder-button passthrough (key_state).
// No "airtrigger"/ultrasonic driver exists anywhere in ASUS's published GPL
// kernel or vendor source for AirTriggers - it's either fully proprietary
// out-of-tree or folded into the touchscreen digitizer's own closed
// firmware. key_state does exist and is real, but it's a raw HID
// button-state readout (DEVICE_ATTR(key_state, 0664, key_state_show, NULL) -
// no store function) meant for driver-internal polling, not a settings
// toggle - wiring it up to actual key-event injection is a separate,
// bigger piece of work than this module covers.
object Rog: EntryStartup {
    private const val COOLER_BASE = "/sys/class/leds/aura_inbox"
    private const val AURA_PHONE_BASE = "/sys/class/leds/aura_sync"
    private const val AURA_SIDE_BASE = "/sys/class/leds/aura_sync_side"
    private const val AURA_BACKCOVER_BASE = "/sys/class/leds/aura_backcover"

    // All four LED classdevs share the same attribute_group, so RGB/mode
    // apply uniformly to whichever of them are actually present - the
    // cooler is accessory-dependent, the other three are always there.
    private val auraZones = listOf(AURA_PHONE_BASE, AURA_SIDE_BASE, AURA_BACKCOVER_BASE, COOLER_BASE)

    private fun writeToFileNofail(path: String, content: String) {
        try {
            File(path).printWriter().use { it.println(content) }
        } catch (t: Throwable) {
            Log.d("PHH", "Rog: failed writing to $path", t)
        }
    }

    private fun applyAura(sp: SharedPreferences) {
        val enabled = sp.getBoolean(RogSettings.auraEnable, false)
        val red = sp.getString(RogSettings.auraRed, "255")
        val green = sp.getString(RogSettings.auraGreen, "255")
        val blue = sp.getString(RogSettings.auraBlue, "255")
        for (base in auraZones) {
            if (!File(base).exists()) continue
            writeToFileNofail("$base/led_on", if (enabled) "1" else "0")
            writeToFileNofail("$base/red_pwm", red ?: "255")
            writeToFileNofail("$base/green_pwm", green ?: "255")
            writeToFileNofail("$base/blue_pwm", blue ?: "255")
            // Buffered state above is only latched to the MCU on this write.
            writeToFileNofail("$base/apply", "1")
        }
    }

    private fun applyAuraMode(sp: SharedPreferences) {
        val mode = sp.getString(RogSettings.auraMode, "0")
        for (base in auraZones) {
            if (File(base).exists()) writeToFileNofail("$base/mode", mode ?: "0")
        }
    }

    val spListener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
        when (key) {
            RogSettings.auraEnable, RogSettings.auraRed, RogSettings.auraGreen, RogSettings.auraBlue ->
                applyAura(sp)
            RogSettings.auraMode -> applyAuraMode(sp)
            RogSettings.coolerFanEnable -> {
                val on = sp.getBoolean(key, false)
                writeToFileNofail("$COOLER_BASE/fan_enable", if (on) "1" else "0")
            }
            RogSettings.coolerFanSpeed -> {
                val speed = sp.getString(key, "0")?.toIntOrNull()?.coerceIn(0, 255) ?: 0
                writeToFileNofail("$COOLER_BASE/fan_rpm", speed.toString())
            }
        }
    }

    override fun startup(ctxt: Context) {
        if (!RogSettings.enabled()) return
        Log.d("PHH", "Starting Rog service")
        val sp = PreferenceManager.getDefaultSharedPreferences(ctxt)
        sp.registerOnSharedPreferenceChangeListener(spListener)

        // The MCUs don't remember settings across power cycles, so
        // re-apply persisted state on boot (same reasoning as Nubia.kt's
        // own "refresh parameters on boot" calls).
        applyAura(sp)
        applyAuraMode(sp)
        if (RogSettings.coolerPresent()) {
            spListener.onSharedPreferenceChanged(sp, RogSettings.coolerFanEnable)
            spListener.onSharedPreferenceChanged(sp, RogSettings.coolerFanSpeed)
        }
    }
}
