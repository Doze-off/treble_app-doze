package me.phh.treble.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView

object MyDeviceSettings : Settings {
    val maintainer = "key_mydevice_maintainer"
    val tgGroup = "key_mydevice_telegram_group"
    val presets = "key_mydevice_apply_presets"

    override fun enabled(context: Context): Boolean {
        val presetExist = synchronized(PresetDownloader.jsonLock) { PresetDownloader.matchedNodes.isNotEmpty() }
        Log.d("PHH", "MyDeviceSettings enabled() called, presetExist = $presetExist")
        return presetExist
    }
}

class MyDeviceSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_mydevice, rootKey)

        val maintainerPref = findPreference<Preference>(MyDeviceSettings.maintainer)
        val tgGroupPref = findPreference<Preference>(MyDeviceSettings.tgGroup)
        val applyPresetsPref = findPreference<Preference>(MyDeviceSettings.presets)

        val nodes = synchronized(PresetDownloader.jsonLock) { PresetDownloader.matchedNodes }
        val deviceNode = nodes.last { it.has("device_name") }

        val deviceName = deviceNode.getString("device_name")
        val maintainer = if (deviceNode.has("maintainer")) deviceNode.getJSONObject("maintainer") else return
        val maintainerNick = maintainer.getString("name")
        val community = if (deviceNode.has("community")) deviceNode.getJSONObject("community") else return

        maintainerPref?.title = "The maintainer of your $deviceName is $maintainerNick"

        if (maintainer.has("telegram")) {
            maintainerPref?.setOnPreferenceClickListener {
                Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(maintainer.getString("telegram"))
                    activity?.startActivity(this)
                }
                true
            }
        }

        if (community.has("telegram")) {
            val url = community.getString("telegram")
            tgGroupPref?.title = "Your device has a community Telegram group at $url"
            tgGroupPref?.setOnPreferenceClickListener {
                Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(url)
                    activity?.startActivity(this)
                }
                true
            }
        } else {
            tgGroupPref?.isVisible = false
        }

        applyPresetsPref?.setOnPreferenceClickListener {
            PresetDownloader.forcePresets = true
            PresetDownloader.handler.post(PresetDownloader.applyPresets)
            true
        }

        Log.d("PHH", "MyDevice settings loaded successfully")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Apply same visual settings as AudioEffectsFragment
        val listView = view.findViewById<RecyclerView>(androidx.preference.R.id.recycler_view)
        listView?.apply {
            overScrollMode = RecyclerView.OVER_SCROLL_NEVER
            clipToPadding = true
            setPadding(32, paddingTop, 32, paddingBottom)

            // Remove dividers
            for (i in 0 until itemDecorationCount) {
                getItemDecorationAt(i)?.let {
                    if (it is androidx.recyclerview.widget.DividerItemDecoration) {
                        removeItemDecoration(it)
                    }
                }
            }
        }
    }
}
