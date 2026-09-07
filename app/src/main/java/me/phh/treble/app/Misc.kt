package me.phh.treble.app

import android.content.Context
import android.content.SharedPreferences
import android.os.ServiceManager
import android.os.SystemProperties
import android.util.Log
import androidx.preference.PreferenceManager
import vendor.ims.zenmotion.V1_0.IZenMotion
import vendor.mediatek.hardware.agolddaemon.IAgoldDaemon

object Misc: EntryStartup {
    fun safeSetprop(key: String, value: String?) {
        try {
            Log.d("PHH", "Setting property $key to $value")
            SystemProperties.set(key, value)
        } catch (e: Exception) {
            Log.d("PHH", "Failed setting prop $key", e)
        }
    }

    val spListener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
        when (key) {
            MiscSettings.biometricstrong -> {
                val value = sp.getBoolean(key, false)
                SystemProperties.set("persist.sys.phh.biometricstrong", if (value) "true" else "false")
            }
            MiscSettings.securize -> {
                val value = sp.getBoolean(key, false)
                SystemProperties.set("persist.sys.phh.securize", if (value) "1" else "0")
            }
            MiscSettings.treatVirtualSensorsAsReal -> {
                val value = sp.getBoolean(key, false)
                SystemProperties.set("persist.sys.phh.virtual_sensors_are_real", if (value) "1" else "0")
            }
            MiscSettings.launcher3 -> {
                val value = sp.getBoolean(key, false)
                SystemProperties.set("persist.sys.phh.launcher3", if (value) "true" else "false")
            }
            MiscSettings.disableSaeUpgrade -> {
                val value = sp.getBoolean(key, false)
                SystemProperties.set("persist.sys.phh.wifi_disable_sae", if (value) "true" else "false")
            }
            MiscSettings.storageFUSE -> {
                val value = sp.getBoolean(key, false)
                Log.d("PHH", "Setting storageFUSE to $value")
                SystemProperties.set("persist.sys.fflag.override.settings_fuse", if (!value) "true" else "false")
            }
            MiscSettings.disableDisplayDozeSuspend -> {
                val value = sp.getBoolean(key, true)
                SystemProperties.set("persist.sys.phh.disable_display_doze_suspend", if (value) "true" else "false")
            }
            MiscSettings.activityAnimPerfOverride -> {
                val value = sp.getBoolean(key, false)
                SystemProperties.set("persist.sys.activity_anim_perf_override", if (value) "true" else "false")
            }
            MiscSettings.lmkTweaks -> {
                val value = sp.getBoolean(key, false)
                SystemProperties.set("persist.sys.phh.lmk_tweaks", if (value) "true" else "false")
            }
            MiscSettings.disableExpensiveRenderingMode -> {
                val value = sp.getBoolean(key, false)
                SystemProperties.set("persist.sys.phh.disable_expensive_rendering_mode", if (value) "1" else "0")
            }
            MiscSettings.preferHwCodecs -> {
                val value = sp.getBoolean(key, false)
                SystemProperties.set("persist.sys.phh.prefer_hw_codecs", if (value) "true" else "false")
            }
            MiscSettings.preferSwCodecs -> {
                val value = sp.getBoolean(key, false)
                SystemProperties.set("persist.sys.phh.prefer_sw_codecs", if (value) "true" else "false")
            }
            MiscSettings.fixScreenRecorder -> {
                val value = sp.getBoolean(key, false)
                SystemProperties.set("persist.sys.phh.fix_screen_recorder", if (value) "true" else "false")
            }
            MiscSettings.maxCompatibility -> {
                val value = sp.getBoolean(key, false)
                SystemProperties.set("persist.sys.phh.max_compatibility", if (value) "true" else "false")
            }
            MiscSettings.legacyMode -> {
                val value = sp.getBoolean(key, false)
                SystemProperties.set("persist.sys.phh.legacy_mode", if (value) "true" else "false")
            }
            MiscSettings.bluetoothFix -> {
                val value = sp.getBoolean(key, false)
                SystemProperties.set("persist.sys.phh.bluetooth_fix", if (value) "true" else "false")
            }
            MiscSettings.unisocColorTransform -> {
                val value = sp.getBoolean(key, false)
                SystemProperties.set("persist.sys.phh.unisoc_color_transform_workaround", if (value) "true" else "false")
            }
            MiscSettings.axionProps -> {
                val value = sp.getBoolean(key, false)
                SystemProperties.set("persist.sys.phh.axion_props", if (value) "true" else "false")
            }
            MiscSettings.scrollBoost -> {
                val value = sp.getBoolean(key, false)
                SystemProperties.set("persist.sys.phh.scroll_boost", if (value) "true" else "false")
            }
            MiscSettings.a2dpAddr -> {
                val value = sp.getBoolean(key, false)
                SystemProperties.set("persist.sys.phh.a2dp_identity_addr", if (value) "true" else "false")
            }
            MiscSettings.safeMedia -> {
                val value = sp.getBoolean(key, false)
                SystemProperties.set("persist.sys.phh.disable_safe_media_volume", if (value) "true" else "false")
            }
            MiscSettings.trafficFix -> {
                val value = sp.getBoolean(key, true)
                SystemProperties.set("persist.sys.phh.traffic_indicator_fallback", if (value) "true" else "false")
            }
            MiscSettings.unihertzdt2w -> {
                val value = sp.getBoolean(key, false)
                try {
                    val binder = android.os.Binder.allowBlocking(
                        ServiceManager.waitForDeclaredService(IAgoldDaemon.DESCRIPTOR + "/default")
                    )
                    val instance = IAgoldDaemon.Stub.asInterface(binder)
                    val ret = instance.SendMessageToIoctl(100, 0, if (value) 1 else 0, if (value) 1 else 0)
                    Log.d("PHH", "Setting agold touch mode returned $ret")
                } catch (t: Throwable) {
                    Log.d("PHH", "Setting agold touch mode failed", t)
                }
            }
            MiscSettings.dt2w -> {
                val value = sp.getBoolean(key, false)
                val asusSvc = try { IZenMotion.getService() } catch (e: Exception) { null }
                asusSvc?.setDclickEnable(if (value) 1 else 0)
            }
        }
    }

    override fun startup(ctxt: Context) {
        Log.d("PHH", "Loading Misc fragment")
        val sp = PreferenceManager.getDefaultSharedPreferences(ctxt)
        sp.registerOnSharedPreferenceChangeListener(spListener)

        // Refresh on boot
        spListener.onSharedPreferenceChanged(sp, MiscSettings.storageFUSE)
        spListener.onSharedPreferenceChanged(sp, MiscSettings.unihertzdt2w)
        spListener.onSharedPreferenceChanged(sp, MiscSettings.dt2w)
        spListener.onSharedPreferenceChanged(sp, MiscSettings.unisocColorTransform)
        spListener.onSharedPreferenceChanged(sp, MiscSettings.trafficFix)
        spListener.onSharedPreferenceChanged(sp, MiscSettings.safeMedia)
        spListener.onSharedPreferenceChanged(sp, MiscSettings.preferHwCodecs)
        spListener.onSharedPreferenceChanged(sp, MiscSettings.preferSwCodecs)
        spListener.onSharedPreferenceChanged(sp, MiscSettings.fixScreenRecorder)
        spListener.onSharedPreferenceChanged(sp, MiscSettings.maxCompatibility)
        spListener.onSharedPreferenceChanged(sp, MiscSettings.legacyMode)
    }
}
