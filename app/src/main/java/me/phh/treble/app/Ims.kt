package me.phh.treble.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.ServiceManager
import android.os.SystemProperties
import android.preference.PreferenceManager
import android.util.Log
import java.lang.ref.WeakReference

@SuppressLint("StaticFieldLeak")
object Ims : EntryStartup {
    lateinit var ctxt: WeakReference<Context>

    val networkListener = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.i("PHH", "Network $network is available!")
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            Log.i(
                "PHH",
                "Received info about network $network, got $networkCapabilities"
            )
        }
    }

    var registeredNetwork = false

    val spListener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
        val context = ctxt.get() ?: return@OnSharedPreferenceChangeListener
        val cm = context.getSystemService(ConnectivityManager::class.java)

        when (key) {
            ImsSettings.requestNetwork -> {
                val enabled = sp.getBoolean(key, false)

                if (enabled && !registeredNetwork) {
                    try {
                        val networkRequest = NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_IMS)
                        .build()

                        cm.requestNetwork(networkRequest, networkListener)
                        registeredNetwork = true

                        Log.d("PHH", "Requested IMS network")
                    } catch (t: Throwable) {
                        Log.e("PHH", "Unable to request IMS network", t)
                    }
                } else if (!enabled && registeredNetwork) {
                    try {
                        cm.unregisterNetworkCallback(networkListener)
                    } catch (t: Throwable) {
                        Log.e("PHH", "Unable to unregister IMS network callback", t)
                    } finally {
                        registeredNetwork = false
                    }
                }
            }

            ImsSettings.forceEnableSettings -> {
                val value = if (sp.getBoolean(key, false)) "1" else "0"

                Tools.safeSetprop("persist.dbg.volte_avail_ovr", value)
                Tools.safeSetprop("persist.dbg.wfc_avail_ovr", value)
                Tools.safeSetprop("persist.dbg.allow_ims_off", value)

                Log.d("PHH", "Forced IMS settings to $value")
            }

            ImsSettings.allowBinderThread -> {
                val value = if (sp.getBoolean(key, false)) "1" else "0"

                Tools.safeSetprop(
                    "persist.sys.phh.allow_binder_thread_on_incoming_calls",
                    value
                )

                Log.d("PHH", "Binder thread workaround set to $value")
            }
        }
    }

    val mHidlService = android.hidl.manager.V1_0.IServiceManager.getService()

    val mAllSlots = listOf(
        "imsrild1",
        "imsrild2",
        "imsrild3",
        "slot1",
        "slot2",
        "slot3",
        "imsSlot1",
        "imsSlot2",
        "mtkSlot1",
        "mtkSlot2",
        "imsradio0",
        "imsradio1"
    )

    val gotMtkP = mAllSlots.find { slot ->
        mHidlService.get(
            "vendor.mediatek.hardware.radio@3.0::IRadio",
            slot
        ) != null
    } != null

    val gotMtkQ = mAllSlots.find { slot ->
        mHidlService.get(
            "vendor.mediatek.hardware.mtkradioex@1.0::IMtkRadioEx",
            slot
        ) != null
    } != null

    val gotMtkR = mAllSlots.find { slot ->
        mHidlService.get(
            "vendor.mediatek.hardware.mtkradioex@2.0::IMtkRadioEx",
            slot
        ) != null
    } != null

    val gotMtkS = mAllSlots.find { slot ->
        mHidlService.get(
            "vendor.mediatek.hardware.mtkradioex@3.0::IMtkRadioEx",
            slot
        ) != null
    } != null

    val gotMtkAidl = mAllSlots.find { slot ->
        ServiceManager.getService(
            "vendor.mediatek.hardware.mtkradioex.ims.IMtkRadioExIms/$slot"
        ) != null
    } != null

    val gotQcomHidl = mAllSlots.find { slot ->
        mHidlService.get(
            "vendor.qti.hardware.radio.ims@1.0::IImsRadio",
            slot
        ) != null
    } != null

    val gotQcomHidlMoto = gotQcomHidl &&
    SystemProperties.get("ro.product.vendor.brand", "")
    .equals("motorola", ignoreCase = true)

    val gotQcomAidl = mAllSlots.find { slot ->
        ServiceManager.getService(
            "vendor.qti.hardware.radio.ims.IImsRadio/$slot"
        ) != null
    } != null

    val gotSLSI = mAllSlots.find { slot ->
        mHidlService.get(
            "vendor.samsung_slsi.telephony.hardware.radio@1.0::IOemSamsungslsi",
            slot
        ) != null
    } != null

    val gotSPRD = mAllSlots.find { slot ->
        mHidlService.get(
            "vendor.sprd.hardware.radio@1.0::IExtRadio",
            slot
        ) != null
    } != null

    val gotHW = mAllSlots.find { slot ->
        mHidlService.get(
            "vendor.huawei.hardware.radio@1.0::IRadio",
            slot
        ) != null
    } != null

    override fun startup(ctxt: Context) {
        Log.d("PHH", "Loading IMS fragment")

        this.ctxt = WeakReference(ctxt.applicationContext)

        val packageManager = ctxt.packageManager

        val gotFloss = packageManager.getInstalledPackages(0).any {
            it.packageName == "me.phh.ims"
        }

        val gotMtkIms = packageManager.getInstalledPackages(0).any {
            it.packageName == "com.mediatek.ims"
        }

        Log.d("PHH", "Detected Floss IMS: $gotFloss")
        Log.d("PHH", "Detected MediaTek IMS: $gotMtkIms")
        Log.d("PHH", "Detected MediaTek P: $gotMtkP")
        Log.d("PHH", "Detected MediaTek Q: $gotMtkQ")
        Log.d("PHH", "Detected MediaTek R: $gotMtkR")
        Log.d("PHH", "Detected MediaTek S: $gotMtkS")
        Log.d("PHH", "Detected MediaTek AIDL: $gotMtkAidl")
        Log.d("PHH", "Detected Qualcomm HIDL: $gotQcomHidl")
        Log.d("PHH", "Detected Qualcomm AIDL: $gotQcomAidl")

        val sp = PreferenceManager.getDefaultSharedPreferences(ctxt)
        sp.registerOnSharedPreferenceChangeListener(spListener)

        val allOverlays = listOf(
            "me.phh.treble.overlay.mtkims",
            "me.phh.treble.overlay.mtkims_telephony",
            "me.phh.treble.overlay.cafims",
            "me.phh.treble.overlay.cafims_telephony",
            "me.phh.treble.overlay.hwims",
            "me.phh.treble.overlay.hwims_telephony",
            "me.phh.treble.overlay.flossims_telephony",
            "me.phh.treble.overlay.slsiims_telephony",
            "me.phh.treble.overlay.sprdims",
            "me.phh.treble.overlay.sprdims_telephony"
        )

        val selectedOverlays = when {
            gotFloss ->
            listOf("me.phh.treble.overlay.flossims_telephony")

            gotMtkP || gotMtkQ || gotMtkR || gotMtkS || gotMtkAidl ->
            listOf(
                "me.phh.treble.overlay.mtkims",
                "me.phh.treble.overlay.mtkims_telephony"
            )

            gotQcomHidl || gotQcomAidl ->
            listOf(
                "me.phh.treble.overlay.cafims",
                "me.phh.treble.overlay.cafims_telephony"
            )

            gotSLSI ->
            listOf("me.phh.treble.overlay.slsiims_telephony")

            gotSPRD ->
            listOf(
                "me.phh.treble.overlay.sprdims",
                "me.phh.treble.overlay.sprdims_telephony"
            )

            gotHW ->
            listOf(
                "me.phh.treble.overlay.hwims",
                "me.phh.treble.overlay.hwims_telephony"
            )

            else ->
                emptyList()
        }

        Tools.safeSetprop(
            "persist.sys.phh.ims.floss",
            if (gotFloss) "true" else "false"
        )

        if (selectedOverlays.isNotEmpty()) {
            allOverlays
            .filter { it !in selectedOverlays }
            .forEach { overlay ->
                OverlayPicker.setOverlayEnabled(overlay, false)
            }

            selectedOverlays.forEach { overlay ->
                OverlayPicker.setOverlayEnabled(overlay, true)
            }

            Log.d(
                "PHH",
                "Enabled IMS overlays: ${selectedOverlays.joinToString()}"
            )
        } else {
            Log.d("PHH", "No compatible IMS overlay selected")
        }

        spListener.onSharedPreferenceChanged(
            sp,
            ImsSettings.requestNetwork
        )

        spListener.onSharedPreferenceChanged(
            sp,
            ImsSettings.forceEnableSettings
        )

        spListener.onSharedPreferenceChanged(
            sp,
            ImsSettings.allowBinderThread
        )
    }
}
