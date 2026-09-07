package me.phh.treble.app

import android.content.Context
import android.os.Bundle
import android.preference.Preference
import android.preference.PreferenceFragment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

object MediatekSettings : Settings {
    val mtkTouchHintIsRotate = "key_mtk_mediatek_touch_hint_rotate"
    val mtkGedKpi = "key_mtk_mediatek_ged_kpi"
    val cognitive = "key_mtk_force_cognitive"

    override fun enabled(context: Context): Boolean {
        val isMediatek = Tools.devicePlatform.toLowerCase().startsWith("mt")
        Log.d("PHH", "MediatekSettings enabled() called, isMediatek = $isMediatek")
        return isMediatek
    }
}

class MediatekSettingsFragment : PreferenceFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.pref_mediatek)

        if (MediatekSettings.enabled(activity)) {
            Log.d("PHH", "Loading Mediatek fragment ${MediatekSettings.enabled(activity)}")
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
            setPadding(32, 64, 32, 32)
        }
    }
}
