package me.phh.treble.app

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemProperties
import android.util.Log
import androidx.preference.PreferenceManager
import java.lang.ref.WeakReference
import android.annotation.SuppressLint

object Ui : EntryStartup {
    private var ctxt: WeakReference<Context>? = null

    val spListener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
        when (key) {
            UiSettings.twoPaneLayout -> {
                val value = sp.getBoolean(key, false)
                SystemProperties.set("persist.sys.phh.two_pane_layout", if (value) "true" else "false")
            }

            UiSettings.forceNavbarOff -> {
                val value = sp.getBoolean(key, false)
                SystemProperties.set("persist.sys.phh.mainkeys", if (value) "1" else "0")
            }

            UiSettings.pointerType -> {
                val value = sp.getString(key, "mouse")
                when (value) {
                    "mouse" -> {
                        SystemProperties.set("persist.sys.overlay.spen_pointer", "false")
                        SystemProperties.set("persist.sys.overlay.trans_pointer", "false")
                    }

                    "pen" -> {
                        SystemProperties.set("persist.sys.overlay.spen_pointer", "true")
                        SystemProperties.set("persist.sys.overlay.trans_pointer", "false")
                    }

                    "transparent" -> {
                        SystemProperties.set("persist.sys.overlay.spen_pointer", "false")
                        SystemProperties.set("persist.sys.overlay.trans_pointer", "true")
                    }
                }
            }

            UiSettings.fodColor -> {
                val value = sp.getString(key, "00ff00")
                SystemProperties.set("persist.sys.phh.fod_color", value)
            }

            UiSettings.statusbarpaddingtop -> {
                val value = sp.getString(key, "-1")?.toIntOrNull() ?: -1
                SystemProperties.set("persist.sys.phh.status_bar_padding_top", value.toString())
            }

            UiSettings.statusbarpaddingstart -> {
                val value = sp.getString(key, "-1")?.toIntOrNull() ?: -1
                SystemProperties.set("persist.sys.phh.status_bar_padding_start", value.toString())
            }

            UiSettings.statusbarpaddingend -> {
                val value = sp.getString(key, "-1")?.toIntOrNull() ?: -1
                SystemProperties.set("persist.sys.phh.status_bar_padding_end", value.toString())
            }

            UiSettings.accentColor -> {
                val value = sp.getString(key, "")
                val overlays = OverlayPicker.getThemeOverlays(OverlayPicker.ThemeOverlay.AccentColor)
                overlays.filter { it.packageName != value }
                    .forEach { OverlayPicker.setOverlayEnabled(it.packageName, false) }
                if (!value.isNullOrEmpty()) {
                    OverlayPicker.setOverlayEnabled(value, true)
                }
            }

            UiSettings.iconShape -> {
                val value = sp.getString(key, "")
                val overlays = OverlayPicker.getThemeOverlays(OverlayPicker.ThemeOverlay.IconShape)
                overlays.filter { it.packageName != value }
                    .forEach { OverlayPicker.setOverlayEnabled(it.packageName, false) }
                if (!value.isNullOrEmpty()) {
                    OverlayPicker.setOverlayEnabled(value, true)
                }
            }

            UiSettings.fontFamily -> {
                val value = sp.getString(key, "")
                val overlays = OverlayPicker.getThemeOverlays(OverlayPicker.ThemeOverlay.FontFamily)
                overlays.filter { it.packageName != value }
                    .forEach { OverlayPicker.setOverlayEnabled(it.packageName, false) }
                if (!value.isNullOrEmpty()) {
                    OverlayPicker.setOverlayEnabled(value, true)
                }
            }

            UiSettings.iconPack -> {
                val value = sp.getString(key, "")
                val overlays = OverlayPicker.getThemeOverlays(OverlayPicker.ThemeOverlay.IconPack)
                val genericValue = value.toString().substringBeforeLast(".")
                for (o in overlays) {
                    if (!value.isNullOrEmpty() && o.packageName.startsWith(genericValue)) {
                        OverlayPicker.setOverlayEnabled(o.packageName, true)
                    } else {
                        OverlayPicker.setOverlayEnabled(o.packageName, false)
                    }
                }
            }
        }
    }

    override fun startup(ctxt: Context) {
        if (!UiSettings.enabled(ctxt)) return

        Log.d("PHH", "Loading UI fragment")

        val sp = PreferenceManager.getDefaultSharedPreferences(ctxt)
        sp.registerOnSharedPreferenceChangeListener(spListener)

        this.ctxt = WeakReference(ctxt.applicationContext)

        // Refresh parameters on boot
        Telephony.spListener.onSharedPreferenceChanged(sp, UiSettings.twoPaneLayout)
    }
}
