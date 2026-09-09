package me.phh.treble.app

import android.app.AlertDialog
import android.app.Fragment
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.preference.Preference
import android.preference.PreferenceFragment
import android.util.Log
import android.view.View
import android.widget.ListView
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.preference.PreferenceManager
import java.io.File

object RogSettings : Settings {
    // AeroActive Cooler 5 (detachable accessory, HID MCU "rog5_inbox")
    val coolerFanEnable = "rog_cooler_fan_enable"
    val coolerFanSpeed = "rog_cooler_fan_speed"

    // Aura Sync RGB - phone body / side rail / back cover / cooler, all
    // driven the same way (separate MS51/rog5_inbox MCUs, same attr set).
    // UI for these now lives in AuraSyncActivity, not this fragment - see
    // "rog_aura_sync_open" below.
    val auraEnable = "rog_aura_enable"
    val auraMode = "rog_aura_mode"
    val auraRed = "rog_aura_red"
    val auraGreen = "rog_aura_green"
    val auraBlue = "rog_aura_blue"
    // set_speed() only accepts 0, 1, 2, 254 or 255 - see Rog.kt.
    val auraSpeed = "rog_aura_speed"

    // The 6 preference keys AuraSyncActivity needs to edit one color/mode
    // profile - bundled so the same editor screen can be reused for both
    // the base "Screen on" color and each event-triggered profile below,
    // instead of hardcoding the base keys throughout that activity.
    data class AuraKeySet(
        val enable: String, val red: String, val green: String,
        val blue: String, val mode: String, val speed: String
    )

    val screenOn = AuraKeySet(auraEnable, auraRed, auraGreen, auraBlue, auraMode, auraSpeed)

    // Per-event lighting profiles. Confirmed from ASUS's own real Aura
    // Sync software that these 4 scenarios (ringing/charging/notification/
    // music) each carry their own independent (enabled, color, mode,
    // speed) profile - AuraLightManager's own setScenarioEffect(scenario,
    // active, color, mode, speed) has exactly this shape. All default to
    // disabled so nothing changes for existing users until they opt in.
    val ringingEvent = AuraKeySet(
        "rog_aura_event_ringing_enable", "rog_aura_event_ringing_red",
        "rog_aura_event_ringing_green", "rog_aura_event_ringing_blue",
        "rog_aura_event_ringing_mode", "rog_aura_event_ringing_speed"
    )
    val chargingEvent = AuraKeySet(
        "rog_aura_event_charging_enable", "rog_aura_event_charging_red",
        "rog_aura_event_charging_green", "rog_aura_event_charging_blue",
        "rog_aura_event_charging_mode", "rog_aura_event_charging_speed"
    )
    val notificationEvent = AuraKeySet(
        "rog_aura_event_notification_enable", "rog_aura_event_notification_red",
        "rog_aura_event_notification_green", "rog_aura_event_notification_blue",
        "rog_aura_event_notification_mode", "rog_aura_event_notification_speed"
    )
    val musicEvent = AuraKeySet(
        "rog_aura_event_music_enable", "rog_aura_event_music_red",
        "rog_aura_event_music_green", "rog_aura_event_music_blue",
        "rog_aura_event_music_mode", "rog_aura_event_music_speed"
    )

    // Ordered highest-priority-first: if more than one event is active at
    // once (e.g. a call rings while charging), the first enabled-and-active
    // one in this order wins. id/keys/label triples drive both the
    // trigger-list screen and AuraSyncActivity's per-scenario title.
    val eventScenarios = listOf(
        Triple("ringing", ringingEvent, "Incoming Call"),
        Triple("notification", notificationEvent, "Notification"),
        Triple("music", musicEvent, "Music Playback"),
        Triple("charging", chargingEvent, "Charging")
    )

    const val SCENARIO_SCREEN_ON = "screen_on"

    fun keySetFor(scenario: String): AuraKeySet =
        eventScenarios.firstOrNull { it.first == scenario }?.second ?: screenOn

    fun labelFor(scenario: String): String =
        eventScenarios.firstOrNull { it.first == scenario }?.third ?: "Aura Sync RGB"

    // Charging - plain persist.sys.* properties, ASUS's own init.asus.rc does
    // the actual sysfs write (charger_limit_mode / ultra_bat_life) on
    // property change, same mechanism the stock Settings app uses.
    val chargingLimit = "rog_charging_limit"
    val ultraBatteryLife = "rog_ultra_battery_life"

    // X Mode gaming touch tuning - FocalTech touch IC sysfs group, confirmed
    // live at /sys/devices/platform/soc/990000.i2c/i2c-2/2-0038/ via ASUS's
    // own init.asus.rc (chown/chmod/restorecon + property-triggered writes
    // for these exact attribute names). Kernel source:
    // drivers/input/touchscreen/ROG5_TP/asus/asus_game.c.
    val gameMode = "rog_game_mode"
    val touchReportRate = "rog_touch_report_rate"
    // edge_settings' left/right coefficients are exposed as one symmetric
    // strength value (0-10, kernel-validated; >10 disables it entirely) -
    // game_settings' three raw touch-IC register tunables (sliding
    // sensitivity/precision, touch sensitivity) are deliberately NOT exposed:
    // unlike edge_settings, the kernel does not range-check them before
    // writing straight to hardware registers, and no ASUS-documented safe
    // values were found to default them to.
    val edgeRejectStrength = "rog_edge_reject_strength"

    // ASUS_I005D/ASUS_I005_1 is the one shared device fingerprint for both
    // ROG Phone 5 (ZS673KS) and ROG Phone 5s (ZS676KS) - confirmed via real
    // stock build.prop, not assumed.
    override fun enabled(context: Context): Boolean {
        val isRog = Tools.vendorFpLow.contains("i005d")
        Log.d("PHH", "RogSettings.enabled() called, isRog = $isRog")
        return isRog
    }

    // AeroActive Cooler 5 is detachable - its "rog5_inbox" HID MCU is only
    // enumerated in sysfs while physically attached, so gate its controls on
    // that rather than just on enabled().
    fun coolerPresent() = File("/sys/class/leds/aura_inbox/fan_enable").exists()

    // Corner-grip rejection strength: labels/values kept identical to the
    // former ListPreference's pref_rog_edge_reject{,_values} arrays.
    val edgeRejectLabels = arrayOf("Off", "1 (lightest)", "3", "5", "7", "10 (strongest)")
    val edgeRejectValues = arrayOf("11", "1", "3", "5", "7", "10")
}

class RogSettingsFragment : PreferenceFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.pref_rog)

        if (RogSettings.enabled(context!!)) {
            Log.d("PHH", "Loading Rog fragment ${RogSettings.enabled(context!!)}")
            SettingsActivity.bindPreferenceSummaryToValue(findPreference(RogSettings.coolerFanSpeed)!!)
        }

        findPreference("rog_aura_sync_open")!!.setOnPreferenceClickListener {
            startActivity(Intent(activity, AuraSyncActivity::class.java))
            true
        }

        findPreference("rog_aura_lighting_triggers_open")!!.setOnPreferenceClickListener {
            startActivity(Intent(activity, LightingTriggersActivity::class.java))
            true
        }

        findPreference(RogSettings.edgeRejectStrength)!!.setOnPreferenceClickListener {
            showEdgeRejectDialog()
            true
        }
    }

    // See pref_rog.xml's comment on rog_edge_reject_strength: the equivalent
    // ListPreference reproducibly crashed (NullPointerException in
    // ArrayAdapter.createViewFromResource) opening this exact dialog, 3/3
    // repro, despite its entries/entryValues resource arrays being
    // well-formed. Building the dialog by hand from a hardcoded Kotlin
    // array sidesteps whichever part of the ListPreference/resource-array
    // codepath was actually responsible.
    private fun showEdgeRejectDialog() {
        val sp = PreferenceManager.getDefaultSharedPreferences(activity)
        val current = sp.getString(RogSettings.edgeRejectStrength, "11")
        val checkedIndex = RogSettings.edgeRejectValues.indexOf(current).coerceAtLeast(0)

        AlertDialog.Builder(activity)
            .setTitle("Corner-grip rejection")
            .setSingleChoiceItems(RogSettings.edgeRejectLabels, checkedIndex) { dialog, which ->
                sp.edit().putString(RogSettings.edgeRejectStrength, RogSettings.edgeRejectValues[which]).apply()
                findPreference(RogSettings.edgeRejectStrength)!!.summary =
                    "Rejects touches near the edges while gripping the phone in landscape (${RogSettings.edgeRejectLabels[which]})"
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_misc_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        (activity as? AppCompatActivity)?.setSupportActionBar(toolbar)
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
        }

        view.findViewById<ListView>(android.R.id.list)?.apply {
            divider = null
            dividerHeight = 0
            clipToPadding = false
            setPadding(32, 56, 32, 32)
        }
    }
}
