package me.phh.treble.app

import android.app.Fragment
import android.content.Context
import android.hardware.Sensor
import android.os.Bundle
import android.preference.Preference
import android.preference.PreferenceFragment
import android.preference.PreferenceManager
import android.preference.SwitchPreference
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

object DozeSettings : Settings {
    val handwaveKey = "key_doze_handwave"
    val pocketKey = "key_doze_pocket"
    val chopchopkey = "key_doze_chopchop"

    override fun enabled(context: Context): Boolean {
        Log.d("PHH", "Initializing Doze settings")
        return true
    }

    fun isMotorola(): Boolean {
        val isMoto = Tools.vendorFp.toLowerCase().startsWith("motorola")
        Log.d("PHH", "Chop-Chop enabled() called, isMoto = $isMoto")
        return isMoto
    }
}

class DozeSettingsFragment : PreferenceFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.pref_doze)

        // Check enabled status for each preference
        activity?.let { context ->
            EntryService.getEnabledPreferences(context).forEach { (key, isEnabled) ->
                if (!isEnabled) {
                    findPreference(key)?.let { pref ->
                        pref.parent?.removePreference(pref)
                    }
                }
            }
        }

        // Handle ChopChop sensor
        handleChopChopPreference()

        Log.d("PHH", "Doze settings loaded successfully")
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

    private fun handleChopChopPreference() {
        (findPreference(DozeSettings.chopchopkey) as? SwitchPreference)?.let { pref ->
            try {
                val hasSensor = Doze.sensorManager.getSensorList(Sensor.TYPE_ALL)
                .any { it.stringType == "com.motorola.sensor.chopchop" }

                if (!hasSensor) {
                    disableChopChopPreference(pref)
                }
            } catch (e: Exception) {
                Log.e("PHH", "Error checking ChopChop sensor", e)
                disableChopChopPreference(pref)
            }
        }
    }

    private fun disableChopChopPreference(pref: SwitchPreference) {
        pref.apply {
            isEnabled = false
            isChecked = false
        }
        PreferenceManager.getDefaultSharedPreferences(activity).edit()
        .putBoolean(DozeSettings.chopchopkey, false)
        .apply()
    }
}
