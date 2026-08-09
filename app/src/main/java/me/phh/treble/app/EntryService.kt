package me.phh.treble.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.IBinder
import android.os.UserHandle
import android.os.SystemProperties
import android.util.Log
import dalvik.system.PathClassLoader
import kotlin.concurrent.thread

class EntryService: Service() {
    companion object {
        var service: EntryService? = null
        private const val CHANNEL_ID = "phh_treble_entry"
        private const val NOTIF_ID = 1
    }
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun tryC(fnc: () -> Unit) {
        try {
            fnc()
        } catch(e: Throwable) {
            Log.e("PHH", "Caught", e)
        }
    }

    // run as a freezer-exempt foreground service so that device-specific
    // sensor listeners (e.g. motorola chop-chop flashlight, handwave/pocket
    // doze) keep firing while the screen is off and the app is backgrounded.
    // without this the android app freezer suspends the whole process and the
    // gestures stop working until the user opens treble settings again.
    private fun startForegroundNotif() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(NotificationChannel(
                CHANNEL_ID,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW
            ))
        }
        val notif = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setSmallIcon(R.drawable.ic_settings)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notif)
    }


    override fun onCreate() {
        service = this
        startForegroundNotif()

        thread {
            tryC { Tools.startup(this) }
            tryC { QtiAudio.startup(this) }
            tryC { Lenovo.startup(this) }
            tryC { OnePlus.startup(this) }
            tryC { Oppo.startup(this) }
            tryC { OverlayPicker.startup(this) }
            tryC { Doze.startup(this) }
            tryC { Huawei.startup(this) }
            tryC { Misc.startup(this) }
            tryC { Samsung.startup(this) }
            tryC { Transsion.startup(this) }
            tryC { Xiaomi.startup(this) }
            tryC { Asus.startup(this) }
            tryC { Qualcomm.startup(this) }
            tryC { Vsmart.startup(this) }
            tryC { Nubia.startup(this) }
            tryC { Ims.startup(this) }
            tryC { Custom.startup(this) }
            tryC { Hct.startup(this) }

            tryC { Desktop.startup(this) }
            tryC { Lid.startup(this) }
            tryC { AudioEffects.startup(this) }

            tryC { PresetDownloader.startup(this) }
            tryC {
                val p = SystemProperties.get("ro.system.ota.json_url", "")
                val c = ComponentName(this, UpdaterActivity::class.java)
                if(p.trim() == "") {
                    packageManager.setComponentEnabledSetting(c, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0)
                } else {
                    packageManager.setComponentEnabledSetting(c, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, 0)
                }
            }
        }
    }
}

class Starter: BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val caller = UserHandle.getCallingUserId()
        if(caller != 0) {
            Log.d("PHH", "Service called from user none 0, ignore")
            return
        }
        Log.d("PHH", "Starting service")
        //TODO: Check current user == "admin" == 0
        when(intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                context.startForegroundServiceAsUser(Intent(context, EntryService::class.java), UserHandle.SYSTEM)
            }
        }
    }
}

interface EntryStartup {
    fun startup(ctxt: Context)
}
