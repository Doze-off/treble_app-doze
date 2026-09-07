package me.phh.treble.app

import android.app.Fragment
import android.content.Context
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

object NubiaSettings : Settings {
    val dt2w = "nubia_double_tap_to_wake"
    val bypassCharger = "nubia_bypass_charger"
    val highTouchScreenSampleRate = "nubia_high_touch_sample_rate"
    val highTouchScreenSensitivity = "nubia_high_touch_sensitivity"
    val tsGameMode = "nubia_touchscreen_game_mode"
    val fanSpeed = "nubia_fan_speed"
    val logoBreath = "nubia_redmagic_logo_breath"
    val redmagicLed = "nubia_redmagic_led"
    val boostCpu = "nubia_boost_cpu"
    val boostGpu = "nubia_boost_gpu"
    val boostCache = "nubia_boost_cache"
    val boostUfs = "nubia_boost_ufs"
    val shoulderBtn = "nubia_shoulder_btn"

    override fun enabled(context: Context): Boolean {
        val isNubia = Tools.vendorFp.toLowerCase().startsWith("nubia/")
        Log.d("PHH", "NubiaSettings enabled() called, isNubia = $isNubia")
        return isNubia
    }

    fun is6Series() = Tools.vendorFp.toLowerCase().startsWith("nubia/nx669")
    fun is5GLite() = Tools.vendorFp.toLowerCase().startsWith("nubia/nx651")
    fun is5G5S() = Tools.vendorFp.toLowerCase().startsWith("nubia/nx659")
}

class NubiaSettingsFragment : PreferenceFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.pref_nubia)

        if (NubiaSettings.enabled(context)) {
            Log.d("PHH", "Loading Nubia fragment ${NubiaSettings.enabled(context)}")

            findPreference(NubiaSettings.fanSpeed)?.let {
                SettingsActivity.bindPreferenceSummaryToValue(it)
            }

            findPreference(NubiaSettings.redmagicLed)?.let {
                SettingsActivity.bindPreferenceSummaryToValue(it)
            }
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
