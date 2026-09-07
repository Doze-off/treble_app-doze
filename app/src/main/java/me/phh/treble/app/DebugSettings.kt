package me.phh.treble.app

import android.app.Fragment
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
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

object DebugSettings : Settings {
    val restartApp = "key_debug_restart_app"
    val debuggable = "key_debug_debuggable_mode"

    val stateMap = mapOf(
        "key_debug_remotectl" to "persist.sys.phh.remote",
        "key_debug_debuggable_mode" to "persist.sys.phh.debuggable",
    )

    override fun enabled(context: Context): Boolean {
        Log.d("PHH", "Initializing Debug settings")
        return true
    }
}

class DebugSettingsFragment : PreferenceFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.pref_debug)

        Tools.updatePreferenceState(this, DebugSettings.stateMap)

        findPreference(DebugSettings.restartApp)?.setOnPreferenceClickListener {
            restartApp()
            true
        }

        Log.d("PHH", "Debug settings loaded successfully")
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

    private fun restartApp() {
        val intent = activity?.baseContext?.packageManager
        ?.getLaunchIntentForPackage(activity?.baseContext?.packageName ?: return)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        Process.killProcess(Process.myPid())
    }
}
