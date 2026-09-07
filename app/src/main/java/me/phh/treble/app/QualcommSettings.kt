package me.phh.treble.app

import android.app.Fragment
import android.content.Context
import android.os.Bundle
import android.os.SystemProperties
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

object QualcommSettings : Settings {
    val alternateMediaprofile = "key_qualcomm_alternate_mediaprofile"
    val disableSoundVolumeEffect = "key_qualcomm_disable_soundvolume_effect"
    val disableStereoVoip = "key_qualcomm_disable_stereo_voip"
    val directOutputVoip = "key_qualcomm_direct_output_voip"
    val restartQCrild = "key_qualcomm_restart_qcrild"

    override fun enabled(context: Context): Boolean {
        val isQualcomm = QtiAudio.isQualcommDevice || SystemProperties.get("ro.hardware", "N/A") == "qcom"
        Log.d("PHH", "QualcommSettings enabled() called, isQualcomm = $isQualcomm")
        return isQualcomm
    }
}

class QualcommSettingsFragment : PreferenceFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.pref_qualcomm)

        if (QualcommSettings.enabled(context)) {
            Log.d("PHH", "Loading Qualcomm fragment ${QualcommSettings.enabled(context)}")
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
