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

object XiaomiSettings : Settings {
    val dt2w = "xiaomi_double_tap_to_wake"

    override fun enabled(context: Context): Boolean {
        val isXiaomi = Tools.vendorFp.toLowerCase().startsWith("xiaomi") ||
        Tools.vendorFp.toLowerCase().startsWith("redmi/") ||
        Tools.vendorFp.toLowerCase().startsWith("poco/")
        Log.d("PHH", "XiaomiSettings enabled() called, isXiaomi = $isXiaomi")
        return isXiaomi
    }
}

class XiaomiSettingsFragment : PreferenceFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.pref_xiaomi)

        if (XiaomiSettings.enabled(context)) {
            Log.d("PHH", "Loading Xiaomi fragment ${XiaomiSettings.enabled(context)}")

            // Exemplo de uso de bindPreferenceSummaryToValue
            findPreference(XiaomiSettings.dt2w)?.let {
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
