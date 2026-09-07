package me.phh.treble.app

import android.app.Fragment
import android.content.Context
import android.os.Bundle
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
import java.io.File

object RogSettings : Settings {
    // AeroActive Cooler 5 (detachable accessory, HID MCU "rog5_inbox")
    val coolerFanEnable = "rog_cooler_fan_enable"
    val coolerFanSpeed = "rog_cooler_fan_speed"

    // Aura Sync RGB - phone body / side rail / back cover / cooler, all
    // driven the same way (separate MS51/rog5_inbox MCUs, same attr set)
    val auraEnable = "rog_aura_enable"
    val auraMode = "rog_aura_mode"
    val auraRed = "rog_aura_red"
    val auraGreen = "rog_aura_green"
    val auraBlue = "rog_aura_blue"

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
}

class RogSettingsFragment : PreferenceFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.pref_rog)

        if (RogSettings.enabled(context!!)) {
            Log.d("PHH", "Loading Rog fragment ${RogSettings.enabled(context!!)}")
            SettingsActivity.bindPreferenceSummaryToValue(findPreference(RogSettings.auraMode)!!)
            SettingsActivity.bindPreferenceSummaryToValue(findPreference(RogSettings.auraRed)!!)
            SettingsActivity.bindPreferenceSummaryToValue(findPreference(RogSettings.auraGreen)!!)
            SettingsActivity.bindPreferenceSummaryToValue(findPreference(RogSettings.auraBlue)!!)
            SettingsActivity.bindPreferenceSummaryToValue(findPreference(RogSettings.coolerFanSpeed)!!)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_misc_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Configura a Toolbar
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        (activity as? AppCompatActivity)?.setSupportActionBar(toolbar)
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
        }

        // Configura o ListView
        view.findViewById<ListView>(android.R.id.list)?.apply {
            divider = null
            dividerHeight = 0
            clipToPadding = false
            setPadding(32, 56, 32, 32)
        }
    }
}
