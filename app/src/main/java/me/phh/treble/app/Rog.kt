package me.phh.treble.app

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.util.Log
import java.io.File
import java.util.Locale

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
// Also implemented: charging limit / ultra battery life (plain persist.sys.*
// properties - ASUS's own init.asus.rc does the sysfs write on property
// change, confirmed from that exact file) and X Mode gaming touch tuning
// (game mode, touch report rate, corner-grip rejection - a sysfs attribute
// group on the FocalTech touch IC itself, drivers/input/touchscreen/ROG5_TP/
// asus/asus_game.c, confirmed live at
// /sys/devices/platform/soc/990000.i2c/i2c-2/2-0038/ via the same
// init.asus.rc file's chown/chmod/restorecon + property-triggered writes for
// these exact attribute names).
//
// NOT implemented here: the built-in on-frame AirTrigger ultrasonic sensors,
// the AeroActive Cooler's own shoulder-button passthrough (key_state), the
// touch IC's raw sensitivity/precision tuning (game_settings) or its virtual
// touch-injection "Key Mapping" (keymapping_touch). AirTrigger turned out to
// be real and controllable - vendor.ims.airtrigger@1.2::IAirTrigger/default
// is a genuine, versioned HIDL service (confirmed via its VINTF manifest
// entry and decompiled symbol table: setEnable/isEnabled,
// setBarTabConfig/getBarTabConfig, setBarSqueezeConfig (v1.1),
// setBarSlideConfig/setBarSwipeConfig (v1.2)) - same architecture Asus.kt
// already uses for vendor.ims.zenmotion. It's not wired up here because no
// prebuilt Java HIDL stub for it ships anywhere in the stock firmware (unlike
// IZenMotion), so calling it needs either the real .hal source or a
// hand-written one matching the recovered method signatures - bigger,
// separate work. key_state is real but read-only (no store function) -
// wiring it to actual key-event injection is also separate work. game_settings
// and keymapping_touch are real and writable, but game_settings' three
// tunables are written straight to a hardware register with no kernel-side
// range validation (unlike edge_settings) and no ASUS-documented safe values
// were found, and keymapping_touch isn't a meaningful toggle without a
// zone-assignment UI - both need more work than a simple sysfs write.
object Rog: EntryStartup {
    private const val COOLER_BASE = "/sys/class/leds/aura_inbox"
    private const val AURA_PHONE_BASE = "/sys/class/leds/aura_sync"
    private const val AURA_SIDE_BASE = "/sys/class/leds/aura_sync_side"
    private const val AURA_BACKCOVER_BASE = "/sys/class/leds/aura_backcover"
    private const val TOUCH_IC_BASE = "/sys/devices/platform/soc/990000.i2c/i2c-2/2-0038"

    // All four LED classdevs share the same attribute_group, so RGB/mode
    // apply uniformly to whichever of them are actually present - the
    // cooler is accessory-dependent, the other three are always there.
    // internal: RogEvents.kt writes the same zones directly while an event
    // trigger (call/charging/notification/music) is overriding the base color.
    internal val auraZones = listOf(AURA_PHONE_BASE, AURA_SIDE_BASE, AURA_BACKCOVER_BASE, COOLER_BASE)

    internal fun writeToFileNofail(path: String, content: String) {
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
            if (!File(base).exists()) continue
            writeToFileNofail("$base/mode", mode ?: "0")
            // mode_store() (ms51_phone.c) writes register 0x8021 immediately,
            // but that only updates the MCU's *cached* mode - the MCU doesn't
            // actually re-render until it receives the 0x802F "apply" trigger
            // (apply_store() bundles every cached parameter - color, mode,
            // speed, led_on - into that one command). Without this, a mode
            // change is invisible until something else happens to call
            // apply() next (e.g. toggling Enable off/on) - confirmed live.
            writeToFileNofail("$base/apply", "1")
        }
    }

    // set_speed() (drivers/aura_sync/ms51_phone.c) only accepts these five
    // exact values, rejecting anything else outright - no other range works.
    // Written straight to register 0x8022 with no documented meaning beyond
    // that; 0/1/2 are presumed slow/medium/fast given they're the small
    // monotonic values sharing the set with the two large "special" codes.
    private fun applyAuraSpeed(sp: SharedPreferences) {
        val speed = sp.getString(RogSettings.auraSpeed, "1")?.toIntOrNull()
        if (speed !in setOf(0, 1, 2, 254, 255)) return
        for (base in auraZones) {
            if (File(base).exists()) writeToFileNofail("$base/speed", speed.toString())
        }
    }

    private fun applyGameMode(sp: SharedPreferences) {
        val on = sp.getBoolean(RogSettings.gameMode, false)
        writeToFileNofail("$TOUCH_IC_BASE/fts_game_mode", if (on) "1" else "0")
    }

    private fun applyTouchReportRate(sp: SharedPreferences) {
        // 0 = auto (120<->300Hz based on game mode), 1 = force 300Hz,
        // 2 = force 560Hz - the three values fts_ts's rise_report_rate_store
        // actually recognizes, anything else is silently ignored by it.
        val rate = sp.getString(RogSettings.touchReportRate, "0") ?: "0"
        writeToFileNofail("$TOUCH_IC_BASE/rise_report_rate", rate)
    }

    private fun applyEdgeReject(sp: SharedPreferences) {
        // edge_settings_store parses two 3-hex-digit fields via its own
        // shex_to_u16(), not decimal - "%03X.%03X" matches that, not the
        // driver's own (inconsistent) "%03d.%03d" show() format. Kernel-side
        // Rcoefleft/RcoefRight > 10 disables edge rejection entirely, so
        // "off" is sent as 11 (0x00B) to land past that threshold on
        // purpose rather than guessing at some other disable path.
        val strength = sp.getString(RogSettings.edgeRejectStrength, "11")?.toIntOrNull()?.coerceIn(0, 11) ?: 11
        val hex = String.format(Locale.ROOT, "%03X.%03X", strength, strength)
        writeToFileNofail("$TOUCH_IC_BASE/edge_settings", hex)
    }

    private fun applyCharging(sp: SharedPreferences) {
        // ASUS's own init.asus.rc reacts to these two persist.sys.*
        // properties by writing /sys/class/asuslib/{charger_limit_mode,
        // ultra_bat_life} itself - same mechanism the stock Settings app
        // uses, so there's no sysfs path to write here directly.
        val limit = sp.getString(RogSettings.chargingLimit, "0") ?: "0"
        Misc.safeSetprop("persist.sys.charginglimit", limit)
        val ultra = sp.getBoolean(RogSettings.ultraBatteryLife, false)
        Misc.safeSetprop("persist.sys.ultrabatterylife", if (ultra) "1" else "0")
    }

    val spListener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
        when (key) {
            RogSettings.auraEnable, RogSettings.auraRed, RogSettings.auraGreen, RogSettings.auraBlue ->
                applyAura(sp)
            RogSettings.auraMode -> applyAuraMode(sp)
            RogSettings.auraSpeed -> applyAuraSpeed(sp)
            RogSettings.coolerFanEnable -> {
                val on = sp.getBoolean(key, false)
                writeToFileNofail("$COOLER_BASE/fan_enable", if (on) "1" else "0")
            }
            RogSettings.coolerFanSpeed -> {
                val speed = sp.getString(key, "0")?.toIntOrNull()?.coerceIn(0, 255) ?: 0
                writeToFileNofail("$COOLER_BASE/fan_rpm", speed.toString())
            }
            RogSettings.gameMode -> applyGameMode(sp)
            RogSettings.touchReportRate -> applyTouchReportRate(sp)
            RogSettings.edgeRejectStrength -> applyEdgeReject(sp)
            RogSettings.chargingLimit, RogSettings.ultraBatteryLife -> applyCharging(sp)
        }
    }

    override fun startup(ctxt: Context) {
        if (!RogSettings.enabled(ctxt)) return
        Log.d("PHH", "Starting Rog service")
        val sp = PreferenceManager.getDefaultSharedPreferences(ctxt)
        sp.registerOnSharedPreferenceChangeListener(spListener)

        // The MCUs don't remember settings across power cycles, so
        // re-apply persisted state on boot (same reasoning as Nubia.kt's
        // own "refresh parameters on boot" calls). The touch IC and
        // charging properties don't survive reboot either.
        applyAura(sp)
        applyAuraMode(sp)
        applyAuraSpeed(sp)
        if (RogSettings.coolerPresent()) {
            spListener.onSharedPreferenceChanged(sp, RogSettings.coolerFanEnable)
            spListener.onSharedPreferenceChanged(sp, RogSettings.coolerFanSpeed)
        }
        applyGameMode(sp)
        applyTouchReportRate(sp)
        applyEdgeReject(sp)
        applyCharging(sp)
    }

    // Re-applies the persisted base "Screen on" color/mode/speed to the
    // MCUs. Called by RogEvents once no event trigger (call/charging/
    // notification/music) is active anymore, to restore whatever the user
    // actually configured on the main Aura Sync screen - mirrors the same
    // three calls startup() makes at boot.
    internal fun reapplyBaseAura(ctxt: Context) {
        val sp = PreferenceManager.getDefaultSharedPreferences(ctxt)
        applyAura(sp)
        applyAuraMode(sp)
        applyAuraSpeed(sp)
    }
}
