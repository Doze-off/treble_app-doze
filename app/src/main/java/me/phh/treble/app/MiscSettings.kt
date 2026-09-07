package me.phh.treble.app

import android.content.Context
import android.os.Bundle
import android.preference.Preference
import android.preference.PreferenceFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import java.io.File

object MiscSettings : Settings {
    val biometricstrong = "key_misc_biometricstrong"
    val securize = "key_misc_securize"
    val treatVirtualSensorsAsReal = "key_misc_treat_virtual_sensors_as_real"
    val launcher3 = "key_misc_launcher3"
    val disableSaeUpgrade = "key_misc_disable_sae_upgrade"
    val storageFUSE = "key_misc_storage_fuse"
    val disableDisplayDozeSuspend = "key_misc_disable_display_doze_suspend"
    val disableExpensiveRenderingMode = "key_misc_disable_expensive_rendering_mode"
    val unihertzdt2w = "key_misc_unihertz_dt2w"
    val dt2w = "key_misc_dt2w"
    val activityAnimPerfOverride = "key_misc_activity_anim_perf_override"
    val lmkTweaks = "key_misc_lmk_tweaks"
    val preferHwCodecs = "key_misc_prefer_hw_codecs"
    val preferSwCodecs = "key_misc_prefer_sw_codecs"
    val fixScreenRecorder = "key_misc_fix_screen_recorder"
    val maxCompatibility = "key_misc_max_compatibility"
    val legacyMode = "key_misc_legacy_mode"
    val bluetoothFix =  "key_misc_bluetooth_fix"
    val unisocColorTransform = "key_misc_unisoc_color_transform"
    val axionProps = "key_misc_axion_props"
    val scrollBoost = "key_misc_scroll_boost"
    val a2dpAddr = "key_misc_a2dp_identity"
    val safeMedia = "key_misc_safe_media"
    val trafficFix = "key_misc_traffic_indicator"

    



    override fun enabled(context: Context): Boolean = true
    fun isRoot() = File(Tools.phhsu).exists()
}

class MiscSettingsFragment : PreferenceFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.pref_misc) // Inicializa preferências aqui

        activity?.let { context ->
            EntryService.getEnabledPreferences(context).forEach { (key, isEnabled) ->
                if (!isEnabled) {
                    findPreference(key)?.let { pref ->
                        pref.parent?.removePreference(pref)
                    }
                }
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
            setPadding(32, 64, 32, 32)
        }
    }
}
