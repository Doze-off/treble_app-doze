package me.phh.treble.app

import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageInfo
import android.os.Bundle
import android.preference.ListPreference
import android.preference.Preference
import android.preference.PreferenceFragment
import android.util.Log
import android.view.View
import android.widget.ListView
import android.widget.Toast
import androidx.preference.PreferenceManager

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

object UiSettings : Settings {
    val twoPaneLayout = "key_ui_two_pane_layout"
    val forceNavbarOff = "key_UI_force_navbar_off"
    val fodColor = "key_ui_fod_color"
    val pointerType = "key_ui_pointer_type"
    val statusbarpaddingtop = "key_ui_sb_padding_top"
    val statusbarpaddingstart = "key_ui_sb_padding_start"
    val statusbarpaddingend = "key_ui_sb_padding_end"
    val qsclockleftpadding = "key_misc_qs_clock_left_padding"
    val qsclockrightpadding = "key_misc_qs_clock_right_padding"
    val restartSystemUI = "key_ui_restart_systemui"

    val accentColor = "key_custom_accent_color"
    val iconShape = "key_custom_icon_shape"
    val fontFamily = "key_custom_font_family"
    val iconPack = "key_custom_icon_pack"

    val stateMap = mapOf(
        statusbarpaddingtop to "persist.sys.phh.status_bar_padding_top",
        statusbarpaddingstart to "persist.sys.phh.status_bar_padding_start",
        statusbarpaddingend to "persist.sys.phh.status_bar_padding_end"
    )

    override fun enabled(context: Context): Boolean {
        Log.d("PHH", "Initializing UI settings")
        return true
    }
}

class UiSettingsFragment : PreferenceFragment() {
    private var packages = listOf<PackageInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.pref_ui)

        if (UiSettings.enabled(context)) {
            Tools.updatePreferenceState(this, UiSettings.stateMap)

            listOf(
                UiSettings.fodColor,
                UiSettings.pointerType,
                UiSettings.statusbarpaddingtop,
                UiSettings.statusbarpaddingstart,
                UiSettings.statusbarpaddingend,
                UiSettings.qsclockleftpadding,
                UiSettings.qsclockrightpadding,
                UiSettings.accentColor,
                UiSettings.iconShape,
                UiSettings.fontFamily,
                UiSettings.iconPack
            ).forEach { prefKey ->
                findPreference(prefKey)?.let {
                    SettingsActivity.bindPreferenceSummaryToValue(it)
                }
            }

            findPreference(UiSettings.restartSystemUI)?.setOnPreferenceClickListener {
                restartUIDialog()
                true
            }

            packages = activity.packageManager.getInstalledPackages(0)

            setupOverlayListPreference(UiSettings.accentColor, OverlayPicker.ThemeOverlay.AccentColor)
            setupOverlayListPreference(UiSettings.iconShape, OverlayPicker.ThemeOverlay.IconShape)
            setupOverlayListPreference(UiSettings.fontFamily, OverlayPicker.ThemeOverlay.FontFamily)
            setupIconPackPreference(UiSettings.iconPack)
        }
    }

    private fun restartUIDialog(): Boolean {
        AlertDialog.Builder(activity)
            .setTitle("Restarting System UI")
            .setMessage("Are you sure?")
            .setPositiveButton(android.R.string.yes) { _, _ ->
                try {
                    val exitCode = Runtime.getRuntime().exec(arrayOf("su", "-c", "pkill -f com.android.systemui")).waitFor()
                    if (exitCode == 0) {
                        Toast.makeText(activity, "SystemUI restarted", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(activity, "Failed to restart SystemUI", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(activity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.no, null)
            .show()
        return true
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

    private fun setupOverlayListPreference(key: String, overlayType: OverlayPicker.ThemeOverlay) {
        val pref = findPreference(key) as? ListPreference ?: return
        val overlays = OverlayPicker.getThemeOverlays(overlayType)
        val entries = listOf("Default") + overlays.map { getTargetName(it.packageName) }
        val values = listOf("") + overlays.map { it.packageName }
        pref.entries = entries.toTypedArray()
        pref.entryValues = values.toTypedArray()
    }

    private fun setupIconPackPreference(key: String) {
        val pref = findPreference(key) as? ListPreference ?: return
        val overlays = OverlayPicker.getThemeOverlays(OverlayPicker.ThemeOverlay.IconPack)
        var iconPackMap = hashMapOf<String, String>()
        overlays.forEach { iconPackMap = addOverlayToMap(iconPackMap, it.packageName) }
        val entries = listOf("Default") + iconPackMap.values
        val values = listOf("") + iconPackMap.keys
        pref.entries = entries.toTypedArray()
        pref.entryValues = values.toTypedArray()
    }

    private fun getTargetName(p: String): String {
        var targetName = p.substringAfterLast(".").replaceFirstChar { it.uppercase() }
        val packageInfo = packages.find { it.packageName == p }
        if (packageInfo != null) {
            targetName = packageInfo.applicationInfo.loadLabel(activity.packageManager).toString()
        }
        return targetName
    }

    private fun addOverlayToMap(map: HashMap<String, String>, o: String): HashMap<String, String> {
        val genericValue = o.substringBeforeLast(".")
        val duplicates = map.filterKeys { it.substringBeforeLast(".") == genericValue }
        if (duplicates.isEmpty()) {
            map[o] = getTargetName(o)
        }
        return map
    }
}
