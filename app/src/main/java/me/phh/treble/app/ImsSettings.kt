package me.phh.treble.app

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.SystemProperties
import android.preference.PreferenceFragment
import android.telephony.TelephonyManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import dalvik.system.PathClassLoader
import java.io.File

object ImsSettings : Settings {
    val requestNetwork = "key_ims_request_network"
    val createApn = "key_ims_create_apn"
    val forceEnableSettings = "key_ims_force_enable_setting"
    val installImsApk = "key_ims_install_apn"
    val allowBinderThread = "key_ims_allow_binder_thread_on_incoming_calls"

    // URLs em cascata: HTTPS (principal) → HTTP (fallback)
    internal const val PHH_HTTPS = "https://treble.phh.me"
    internal const val PHH_HTTP = "http://treble.phh.me"

    fun checkHasPhhSignature(): Boolean {
        return try {
            val cl = PathClassLoader(
                "/system/framework/services.jar",
                ClassLoader.getSystemClassLoader()
            )
            val pmUtils = cl.loadClass("com.android.server.pm.PackageManagerServiceUtils")
            val field = pmUtils.getDeclaredField("PHH_SIGNATURE")

            Log.d("PHH", "checkHasPhhSignature Field $field")
            true
        } catch (t: Throwable) {
            Log.d("PHH", "checkHasPhhSignature Field failed", t)
            false
        }
    }

    override fun enabled(context: Context): Boolean {
        Log.d("PHH", "Initializing IMS settings")
        return true
    }
}

class ImsSettingsFragment : PreferenceFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.pref_ims)

        setupInstalledImsSummary()
        setupApnPreference()
        setupImsInstallPreference()

        Log.d("PHH", "IMS settings loaded successfully")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_misc_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)

        (activity as? AppCompatActivity)?.setSupportActionBar(toolbar)
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
        }

        view.findViewById<ListView>(android.R.id.list)?.apply {
            divider = null
            dividerHeight = 0
            clipToPadding = false
            setPadding(32, 56, 32, 32)
        }
    }

    private fun setupInstalledImsSummary() {
        val packageSummaryPref = findPreference(ImsSettings.installImsApk)

        val packagesToCheck = listOf(
            "org.codeaurora.ims",
            "com.mediatek.ims",
            "me.phh.ims"
        )

        val installedPackages = activity?.applicationContext?.let { context ->
            Tools.isPackageInstalled(context, packagesToCheck)
        }

        packageSummaryPref?.summary = if (!installedPackages.isNullOrEmpty()) {
            "Installed packages: ${installedPackages.joinToString()}"
        } else {
            "No IMS packages installed"
        }
    }

    private fun setupApnPreference() {
        val createApnPreference = findPreference(ImsSettings.createApn)

        val cursor = activity?.applicationContext?.let { context ->
            Tools.checkIfApnExists(context, "PHH IMS")
        }

        createApnPreference?.summary = if (cursor != null && cursor.moveToFirst()) {
            "APN PHH IMS already exists"
        } else {
            "No APN PHH IMS found"
        }

        cursor?.close()

        createApnPreference?.setOnPreferenceClickListener {
            handleApnCreation()
            true
        }
    }

    private fun setupImsInstallPreference() {
        val installIms = findPreference(ImsSettings.installImsApk)

        logRadioInfo()

        val (primaryUrl, description) = determineImsPackage()

        // Verifica status atual do IMS
        val currentImsStatus = getCurrentImsStatus()

        installIms?.title = if (currentImsStatus.isNotEmpty()) {
            "IMS Status: $currentImsStatus"
        } else {
            "Install IMS APK for $description"
        }

        installIms?.summary = if (currentImsStatus.isNotEmpty()) {
            "IMS is already installed. Tap to reinstall if needed."
        } else {
            "Download and install: $description"
        }

        installIms?.setOnPreferenceClickListener {
            downloadAndInstallIms(primaryUrl, description)
            true
        }
    }

    /**
     * Retorna lista de pacotes IMS instalados
     */
    private fun getCurrentImsStatus(): String {
        val packagesToCheck = listOf(
            "org.codeaurora.ims",
            "com.mediatek.ims",
            "me.phh.ims"
        )

        val installedPackages = activity?.applicationContext?.let { context ->
            Tools.isPackageInstalled(context, packagesToCheck)
        }

        return if (!installedPackages.isNullOrEmpty()) {
            installedPackages.joinToString()
        } else {
            ""
        }
    }

    private fun handleApnCreation() {
        val context = activity ?: return
        val tm = context.getSystemService(TelephonyManager::class.java)

        val operator = tm?.simOperator ?: run {
            Log.d("PHH", "No current carrier, bailing out")
            Toast.makeText(context, "No SIM operator detected", Toast.LENGTH_SHORT).show()
            return
        }

        if (operator.length < 5) {
            Log.d("PHH", "Invalid SIM operator value: $operator")
            Toast.makeText(context, "Invalid SIM operator", Toast.LENGTH_SHORT).show()
            return
        }

        val mcc = operator.substring(0, 3)
        val mnc = operator.substring(3)

        Log.d("PHH", "Got mcc = $mcc and mnc = $mnc")

        val cv = ContentValues().apply {
            put("name", "PHH IMS")
            put("apn", "ims")
            put("type", "ims")
            put("edited", "1")
            put("user_editable", "1")
            put("user_visible", "1")
            put("protocol", "IPV4V6")
            put("roaming_protocol", "IPV6")
            put("modem_cognitive", "1")
            put("numeric", operator)
            put("mcc", mcc)
            put("mnc", mnc)
        }

        val result = context.contentResolver.insert(
            Uri.parse("content://telephony/carriers"),
                                                    cv
        )

        findPreference(ImsSettings.createApn)?.summary = if (result != null) {
            "IMS APN successfully added"
        } else {
            "Failed to add IMS APN"
        }
    }

    private fun logRadioInfo() {
        Log.d("PHH", "ro.vndk.version = ${SystemProperties.get("ro.vndk.version", "")}")
        Log.d("PHH", "MTK P radio = ${Ims.gotMtkP}")
        Log.d("PHH", "MTK Q radio = ${Ims.gotMtkQ}")
        Log.d("PHH", "MTK R radio = ${Ims.gotMtkR}")
        Log.d("PHH", "MTK S radio = ${Ims.gotMtkS}")
        Log.d("PHH", "MTK AIDL radio = ${Ims.gotMtkAidl}")
        Log.d("PHH", "Qualcomm HIDL radio = ${Ims.gotQcomHidl}")
        Log.d("PHH", "Qualcomm AIDL radio = ${Ims.gotQcomAidl}")
    }

    private fun determineImsPackage(): Pair<String, String> {
        val signSuffix = if (ImsSettings.checkHasPhhSignature()) "-resigned" else ""

        return when {
            Ims.gotMtkR || Ims.gotMtkS || Ims.gotMtkAidl -> {
                Pair(
                    "${ImsSettings.PHH_HTTPS}/ims-mtk-u$signSuffix.apk",
                     "MediaTek R+ vendor (PHH)"
                )
            }

            Ims.gotMtkP -> {
                Pair(
                    "${ImsSettings.PHH_HTTPS}/stable/ims-mtk-p$signSuffix.apk",
                     "MediaTek P vendor (PHH)"
                )
            }

            Ims.gotMtkQ -> {
                Pair(
                    "${ImsSettings.PHH_HTTPS}/stable/ims-mtk-q$signSuffix.apk",
                     "MediaTek Q vendor (PHH)"
                )
            }

            Ims.gotMtkR -> {
                Pair(
                    "${ImsSettings.PHH_HTTPS}/stable/ims-mtk-r$signSuffix.apk",
                     "MediaTek R vendor (PHH)"
                )
            }

            Ims.gotMtkS -> {
                Pair(
                    "${ImsSettings.PHH_HTTPS}/stable/ims-mtk-s$signSuffix.apk",
                     "MediaTek S vendor (PHH)"
                )
            }

            Ims.gotQcomHidlMoto &&
            SystemProperties.getInt("ro.vndk.version", -1) <= 31 -> {
                Pair(
                    "${ImsSettings.PHH_HTTPS}/stable/ims-caf-moto$signSuffix.apk",
                     "Qualcomm pre-S vendor (Motorola)"
                )
            }

            (Ims.gotQcomHidl || Ims.gotQcomAidl) &&
            Build.VERSION.SDK_INT >= 34 -> {
                Pair(
                    "${ImsSettings.PHH_HTTPS}/ims-caf-u$signSuffix.apk",
                     "Qualcomm vendor"
                )
            }

            Ims.gotQcomHidl -> {
                Pair(
                    "${ImsSettings.PHH_HTTPS}/stable/ims-q.64$signSuffix.apk",
                     "Qualcomm pre-S vendor"
                )
            }

            Ims.gotQcomAidl -> {
                Pair(
                    "${ImsSettings.PHH_HTTPS}/stable/ims-caf-s$signSuffix.apk",
                     "Qualcomm S+ vendor"
                )
            }

            else -> {
                Pair(
                    "${ImsSettings.PHH_HTTPS}/floss-ims-resigned.apk",
                     "Floss IMS (EXPERIMENTAL)"
                )
            }
        }
    }

    /**
     * Tenta baixar em cascata: HTTPS → HTTP
     */
    @SuppressLint("Range")
    private fun downloadAndInstallIms(primaryUrl: String, description: String) {
        val context = activity ?: return

        val urlsToTry = listOf(
            primaryUrl,
            primaryUrl.replace(ImsSettings.PHH_HTTPS, ImsSettings.PHH_HTTP)
        )

        Log.d("PHH", "Attempting download from URLs: ${urlsToTry.joinToString()}")

        Toast.makeText(
            context,
            "Starting IMS download for: $description",
            Toast.LENGTH_SHORT
        ).show()

        tryDownloadCascade(context, urlsToTry, 0)
    }

    private fun tryDownloadCascade(
        context: Context,
        urls: List<String>,
        index: Int
    ) {
        if (index >= urls.size) {
            Toast.makeText(
                context,
                "All download sources failed. Try manual download.",
                Toast.LENGTH_LONG
            ).show()

            Toast.makeText(
                context,
                "Download from: ${ImsSettings.PHH_HTTPS}/stable/",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val url = urls[index]
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        Log.d("PHH", "Trying download from: $url (attempt ${index + 1}/${urls.size})")

        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("IMS APK")
            setDescription("Downloading IMS service package (attempt ${index + 1})")
            setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                "ImsService.apk"
            )
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }

        val downloadId: Long
        try {
            downloadId = dm.enqueue(request)
        } catch (e: Exception) {
            Log.e("PHH", "Download failed for $url: ${e.message}", e)

            val isLastAttempt = index >= urls.size - 1
            val errorMsg = when {
                e.message?.contains("certificate", ignoreCase = true) == true ->
                "SSL certificate error"
                e.message?.contains("connection", ignoreCase = true) == true ->
                "Connection failed"
                e.message?.contains("permission", ignoreCase = true) == true ->
                "Permission denied"
                else ->
                    "Error: ${e.message}"
            }

            if (!isLastAttempt) {
                Toast.makeText(
                    context,
                    "$errorMsg. Trying HTTP...",
                    Toast.LENGTH_SHORT
                ).show()

                tryDownloadCascade(context, urls, index + 1)
            } else {
                Toast.makeText(
                    context,
                    "All sources failed: $errorMsg",
                    Toast.LENGTH_LONG
                ).show()
            }

            return
        }

        Toast.makeText(context, "Downloading IMS APK...", Toast.LENGTH_SHORT).show()

        context.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(receiverContext: Context, intent: Intent) {
                    val completedId = intent.getLongExtra(
                        DownloadManager.EXTRA_DOWNLOAD_ID,
                        -1L
                    )

                    if (completedId != downloadId) return

                        val cursor = dm.query(
                            DownloadManager.Query().setFilterById(downloadId)
                        )

                        if (!cursor.moveToFirst()) {
                            Log.e("PHH", "DownloadManager returned an empty cursor")
                            cursor.close()
                            receiverContext.unregisterReceiver(this)

                            if (index < urls.size - 1) {
                                tryDownloadCascade(context, urls, index + 1)
                            }
                            return
                        }

                        val status = cursor.getInt(
                            cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        )

                        if (status != DownloadManager.STATUS_SUCCESSFUL) {
                            val reason = cursor.getInt(
                                cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                            )

                            val reasonText = when (reason) {
                                DownloadManager.ERROR_UNKNOWN -> "Unknown error"
                                DownloadManager.ERROR_FILE_ERROR -> "File error"
                                DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "HTTP error"
                                DownloadManager.ERROR_HTTP_DATA_ERROR -> "HTTP data error"
                                DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Too many redirects"
                                DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Insufficient space"
                                DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Device not found"
                                else -> "Error code: $reason"
                            }

                            Log.e("PHH", "Download failed for $url. Reason: $reason ($reasonText)")

                            cursor.close()
                            receiverContext.unregisterReceiver(this)

                            if (index < urls.size - 1) {
                                Toast.makeText(
                                    receiverContext,
                                    "Download failed. Trying HTTP...",
                                    Toast.LENGTH_SHORT
                                ).show()

                                tryDownloadCascade(context, urls, index + 1)
                            } else {
                                Toast.makeText(
                                    receiverContext,
                                    "All sources failed: $reasonText",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            return
                        }

                        val localUriString = cursor.getString(
                            cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                        )

                        cursor.close()
                        receiverContext.unregisterReceiver(this)

                        if (localUriString.isNullOrEmpty()) {
                            Log.e("PHH", "Downloaded IMS APK has no local URI")
                            Toast.makeText(
                                receiverContext,
                                "Unable to access downloaded IMS APK",
                                Toast.LENGTH_LONG
                            ).show()
                            return
                        }

                        val localUri = Uri.parse(localUriString)
                        val path = localUri.path

                        if (path.isNullOrEmpty()) {
                            Log.e("PHH", "Unable to resolve downloaded APK path: $localUri")
                            Toast.makeText(
                                receiverContext,
                                "Unable to resolve IMS APK file path",
                                Toast.LENGTH_LONG
                            ).show()
                            return
                        }

                        installDownloadedApk(receiverContext, path)
                }
            },
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        )
    }

    private fun installDownloadedApk(context: Context, path: String) {
        try {
            val file = File(path)

            if (!file.exists()) {
                Log.e("PHH", "IMS APK file not found: $path")
                Toast.makeText(
                    context,
                    "IMS APK file not found",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            val uri = Uri.fromFile(file)

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)

            Toast.makeText(
                context,
                "Opening system installer for IMS APK",
                Toast.LENGTH_LONG
            ).show()

            Log.d("PHH", "Launched system installer for: $path")
        } catch (t: Throwable) {
            Log.e("PHH", "Failed to launch system installer", t)

            Toast.makeText(
                context,
                "Unable to open system installer: ${t.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // Atualiza o status do IMS quando o fragmento volta a ficar visível
        setupImsInstallPreference()
    }
}
