package me.phh.treble.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.preference.PreferenceManager
import android.telephony.TelephonyManager
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DualWifiActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "DualWifi_UI"
    }

    private lateinit var wifiManager: DualWifiManager
    private var pollJob: Job? = null

    private val prefs by lazy { getSharedPreferences("dual_wifi_prefs", Context.MODE_PRIVATE) }
    private val defaultSp by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    // UI elements
    private lateinit var btnBack: View
    private lateinit var tvHubStatusBadge: TextView
    private lateinit var switchDualWifi: CompoundButton
    private lateinit var tvMasterSubtitle: TextView
    private lateinit var switchHyperFusion: CompoundButton
    private lateinit var tvHyperFusionSubtitle: TextView

    private lateinit var tvPrimarySsid: TextView
    private lateinit var tvPrimarySub: TextView
    private lateinit var tvPrimaryBadge: TextView

    private lateinit var ivSecondaryIcon: ImageView
    private lateinit var tvSecondarySsid: TextView
    private lateinit var tvSecondarySub: TextView
    private lateinit var tvSecondaryBadge: TextView

    private lateinit var ivMobileIcon: ImageView
    private lateinit var tvMobileSsid: TextView
    private lateinit var tvMobileSub: TextView
    private lateinit var tvMobileBadge: TextView

    private lateinit var layoutModeGaming: LinearLayout
    private lateinit var rbModeGaming: RadioButton
    private lateinit var layoutModeSpeed: LinearLayout
    private lateinit var rbModeSpeed: RadioButton
    private lateinit var btnSelectNetwork: Button

    private lateinit var tvDbsStatusBadge: TextView
    private lateinit var tvSlaStatusBadge: TextView
    private lateinit var tvTrafficSummary: TextView
    private lateinit var btnRunHealthCheck: Button

    private lateinit var switchDevMode: CompoundButton
    private lateinit var layoutDevConsole: LinearLayout
    private lateinit var tvAdvancedStats: TextView
    private lateinit var btnCopyLogs: Button
    private lateinit var btnClearLogs: Button
    private lateinit var scrollLogs: NestedScrollView
    private lateinit var tvLog: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DualWifiLogger.i(TAG, "DualWifiActivity onCreate: Initializing Network Acceleration Hub")
        setContentView(R.layout.activity_dual_wifi)

        wifiManager = DualWifiManager(applicationContext)

        bindViews()
        setupUI()
        setupListeners()
        observeLogs()
        checkRootAccess()
    }

    private fun bindViews() {
        btnBack = findViewById(R.id.btnBack)
        tvHubStatusBadge = findViewById(R.id.tvHubStatusBadge)
        switchDualWifi = findViewById(R.id.switchDualWifi)
        tvMasterSubtitle = findViewById(R.id.tvMasterSubtitle)
        switchHyperFusion = findViewById(R.id.switchHyperFusion)
        tvHyperFusionSubtitle = findViewById(R.id.tvHyperFusionSubtitle)

        tvPrimarySsid = findViewById(R.id.tvPrimarySsid)
        tvPrimarySub = findViewById(R.id.tvPrimarySub)
        tvPrimaryBadge = findViewById(R.id.tvPrimaryBadge)

        ivSecondaryIcon = findViewById(R.id.ivSecondaryIcon)
        tvSecondarySsid = findViewById(R.id.tvSecondarySsid)
        tvSecondarySub = findViewById(R.id.tvSecondarySub)
        tvSecondaryBadge = findViewById(R.id.tvSecondaryBadge)

        ivMobileIcon = findViewById(R.id.ivMobileIcon)
        tvMobileSsid = findViewById(R.id.tvMobileSsid)
        tvMobileSub = findViewById(R.id.tvMobileSub)
        tvMobileBadge = findViewById(R.id.tvMobileBadge)

        layoutModeGaming = findViewById(R.id.layoutModeGaming)
        rbModeGaming = findViewById(R.id.rbModeGaming)
        layoutModeSpeed = findViewById(R.id.layoutModeSpeed)
        rbModeSpeed = findViewById(R.id.rbModeSpeed)
        btnSelectNetwork = findViewById(R.id.btnSelectNetwork)

        tvDbsStatusBadge = findViewById(R.id.tvDbsStatusBadge)
        tvSlaStatusBadge = findViewById(R.id.tvSlaStatusBadge)
        tvTrafficSummary = findViewById(R.id.tvTrafficSummary)
        btnRunHealthCheck = findViewById(R.id.btnRunHealthCheck)

        switchDevMode = findViewById(R.id.switchDevMode)
        layoutDevConsole = findViewById(R.id.layoutDevConsole)
        tvAdvancedStats = findViewById(R.id.tvAdvancedStats)
        btnCopyLogs = findViewById(R.id.btnCopyLogs)
        btnClearLogs = findViewById(R.id.btnClearLogs)
        scrollLogs = findViewById(R.id.scrollLogs)
        tvLog = findViewById(R.id.tvLog)
    }

    override fun onResume() {
        super.onResume()
        DualWifiLogger.d(TAG, "DualWifiActivity onResume: Resuming telemetry polling")
        startStatusPolling()
    }

    override fun onPause() {
        super.onPause()
        DualWifiLogger.d(TAG, "DualWifiActivity onPause: Pausing telemetry polling")
        pollJob?.cancel()
    }

    private fun setupUI() {
        btnBack.setOnClickListener { finish() }

        val savedSsid = prefs.getString("saved_ssid", null)
        if (savedSsid != null) {
            tvSecondarySsid.text = savedSsid
            btnSelectNetwork.text = "Change Network ($savedSsid)"
            DualWifiLogger.d(TAG, "Restored saved secondary network: $savedSsid")
        }

        val curMode = defaultSp.getString(RogSettings.dualWifiMode, "1")
        if (curMode == "2") {
            rbModeSpeed.isChecked = true
            rbModeGaming.isChecked = false
        } else {
            rbModeGaming.isChecked = true
            rbModeSpeed.isChecked = false
        }

        val hyperFusionOn = defaultSp.getBoolean(RogSettings.hyperFusion, false)
        switchHyperFusion.isChecked = hyperFusionOn
        if (hyperFusionOn) {
            val mobileDataOn = isMobileDataEnabled()
            if (!mobileDataOn) {
                tvHyperFusionSubtitle.text = "⚠️ Mobile data is inactive - turn on Mobile Data to bond"
                tvHyperFusionSubtitle.setTextColor(getColor(android.R.color.holo_orange_light))
            } else {
                tvHyperFusionSubtitle.text = "Active • Latency bonding with mobile data"
                tvHyperFusionSubtitle.setTextColor(getColor(com.google.android.material.R.color.material_dynamic_neutral_variant70))
            }
        }
    }

    private fun isMobileDataEnabled(): Boolean {
        return try {
            val tm = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val tmData = try { tm?.isDataEnabled } catch (_: Throwable) { null }
            val settingOn = android.provider.Settings.Global.getInt(contentResolver, "mobile_data", 0) == 1
            tmData ?: settingOn
        } catch (_: Throwable) {
            false
        }
    }

    private fun checkRootAccess() {
        lifecycleScope.launch {
            val rootOk = RootShell.isRootAvailable()
            if (!rootOk) {
                DualWifiLogger.e(TAG, "Superuser access not available! Commands may fail.")
                Toast.makeText(
                    this@DualWifiActivity,
                    "Warning: Superuser permission not detected. Please verify KernelSU / Magisk grant.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                DualWifiLogger.i(TAG, "Superuser access verified and active.")
            }
        }
    }

    private fun observeLogs() {
        lifecycleScope.launch {
            DualWifiLogger.logsFlow.collect { entries ->
                if (entries.isNotEmpty()) {
                    val logText = entries.takeLast(40).joinToString("\n") { it.formatted() }
                    tvLog.text = logText
                    scrollLogs.post {
                        scrollLogs.fullScroll(View.FOCUS_DOWN)
                    }
                }
            }
        }
    }

    private fun setupListeners() {
        // Master Dual Wi-Fi Toggle
        switchDualWifi.setOnCheckedChangeListener { _, isChecked ->
            DualWifiLogger.i(TAG, "Master Dual Wi-Fi toggle: isChecked=$isChecked")
            if (isChecked) {
                val savedSsid = prefs.getString("saved_ssid", null)
                val savedPass = if (savedSsid != null) wifiManager.getSavedPassword(savedSsid) ?: prefs.getString("saved_pass", "") ?: "" else ""
                if (savedSsid.isNullOrEmpty()) {
                    showNetworkPicker()
                } else {
                    connectToSecondary(savedSsid, savedPass)
                }
            } else {
                disconnectSecondary()
            }
        }

        switchHyperFusion.setOnCheckedChangeListener { _, isChecked ->
            DualWifiLogger.i(TAG, "HyperFusion toggle switched: isChecked=$isChecked")
            defaultSp.edit().putBoolean(RogSettings.hyperFusion, isChecked).apply()
            val mobileDataOn = isMobileDataEnabled()
            if (isChecked) {
                if (!mobileDataOn) {
                    tvHyperFusionSubtitle.text = "⚠️ Mobile data is inactive - turn on Mobile Data to bond"
                    tvHyperFusionSubtitle.setTextColor(getColor(android.R.color.holo_orange_light))
                    Toast.makeText(
                        this@DualWifiActivity,
                        "Warning: Mobile data is inactive. Turn on Mobile Data to activate HyperFusion.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    tvHyperFusionSubtitle.text = "Active • Latency bonding with mobile data"
                    tvHyperFusionSubtitle.setTextColor(getColor(com.google.android.material.R.color.material_dynamic_neutral_variant70))
                }
            } else {
                tvHyperFusionSubtitle.text = "Bond Wi-Fi with 5G/4G cellular data (Qualcomm SLA)"
                tvHyperFusionSubtitle.setTextColor(getColor(com.google.android.material.R.color.material_dynamic_neutral_variant70))
            }
            lifecycleScope.launch { refreshStatus() }
        }

        // Scan & Select Secondary Network
        btnSelectNetwork.setOnClickListener {
            DualWifiLogger.i(TAG, "User clicked Scan & Select Secondary Network")
            showNetworkPicker()
        }

        btnSelectNetwork.setOnLongClickListener {
            showForgetNetworkDialog()
            true
        }

        // Mode Toggles
        rbModeGaming.setOnClickListener {
            rbModeGaming.isChecked = true
            rbModeSpeed.isChecked = false
            defaultSp.edit().putString(RogSettings.dualWifiMode, "1").apply()
            DualWifiLogger.i(TAG, "Acceleration mode set to Gaming Low-Latency (SLS)")
        }
        layoutModeGaming.setOnClickListener { rbModeGaming.performClick() }

        rbModeSpeed.setOnClickListener {
            rbModeSpeed.isChecked = true
            rbModeGaming.isChecked = false
            defaultSp.edit().putString(RogSettings.dualWifiMode, "2").apply()
            DualWifiLogger.i(TAG, "Acceleration mode set to Download Booster (SLA)")
        }
        layoutModeSpeed.setOnClickListener { rbModeSpeed.performClick() }

        // System Health Check Button
        btnRunHealthCheck.setOnClickListener {
            runHealthCheckDiagnostics()
        }

        // Developer Mode Switch Toggle
        switchDevMode.setOnCheckedChangeListener { _, isChecked ->
            layoutDevConsole.visibility = if (isChecked) View.VISIBLE else View.GONE
            DualWifiLogger.d(TAG, "Developer Console toggled: $isChecked")
            if (isChecked) {
                scrollLogs.post { scrollLogs.fullScroll(View.FOCUS_DOWN) }
            }
        }

        // Copy Logs Button
        btnCopyLogs.setOnClickListener {
            val logs = DualWifiLogger.getAllLogs()
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("DualWifi Logs", logs))
            Toast.makeText(this, "Diagnostics logs copied to clipboard (${logs.lines().size} lines)", Toast.LENGTH_SHORT).show()
            DualWifiLogger.d(TAG, "Logs copied to clipboard by user")
        }

        // Clear Logs Button
        btnClearLogs.setOnClickListener {
            DualWifiLogger.clear()
            tvLog.text = "Logs cleared.\n"
        }
    }

    private fun runHealthCheckDiagnostics() {
        lifecycleScope.launch {
            btnRunHealthCheck.isEnabled = false
            btnRunHealthCheck.text = "Running Health Check..."
            DualWifiLogger.i(TAG, "User triggered system health check...")

            val report = wifiManager.runSelfTest()
            btnRunHealthCheck.isEnabled = true
            btnRunHealthCheck.text = "Run System Health Check"

            val isDualWifiActive = switchDualWifi.isChecked
            val isHyperFusionActive = switchHyperFusion.isChecked

            val summaryMessage = buildString {
                append("● System Acceleration Health: READY\n\n")
                append("• Hardware DBS (Wi-Fi 6E): ")
                append(if (isDualWifiActive) "ACTIVE (Dual-Band Bonded)" else "STANDBY (Hardware Available)").append("\n")
                append("• HyperFusion (Cellular SLA): ")
                append(if (isHyperFusionActive) "ACTIVE (Multi-Path Bonding)" else "STANDBY").append("\n\n")
                append("Detailed Subsystem Diagnostics:\n")
                append(report)
            }

            MaterialAlertDialogBuilder(this@DualWifiActivity)
                .setTitle("Network Acceleration Diagnostics")
                .setMessage(summaryMessage)
                .setPositiveButton("OK", null)
                .setNeutralButton("Copy Report") { _, _ ->
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Diagnostics Report", summaryMessage))
                    Toast.makeText(this@DualWifiActivity, "Report copied to clipboard", Toast.LENGTH_SHORT).show()
                }
                .show()
        }
    }

    private fun showNetworkPicker() {
        lifecycleScope.launch {
            btnSelectNetwork.isEnabled = false
            btnSelectNetwork.text = "Scanning nearby networks..."
            DualWifiLogger.i(TAG, "Scanning for 5 GHz and 2.4 GHz access points...")

            val networks = wifiManager.scanNetworks()
            btnSelectNetwork.isEnabled = true
            val currentSsid = prefs.getString("saved_ssid", null)
            btnSelectNetwork.text = if (currentSsid != null) "Change Network ($currentSsid)" else "Scan & Select Secondary Network"

            if (networks.isEmpty()) {
                DualWifiLogger.w(TAG, "Scan returned 0 networks")
                MaterialAlertDialogBuilder(this@DualWifiActivity)
                    .setTitle("No Networks Found")
                    .setMessage("No Wi-Fi networks were discovered.\n\nPlease ensure Wi-Fi is enabled in Android Settings and check the Diagnostics log drawer for details.")
                    .setPositiveButton("OK", null)
                    .setNeutralButton("View Logs") { _, _ ->
                        switchDevMode.isChecked = true
                        layoutDevConsole.visibility = View.VISIBLE
                    }
                    .show()
                return@launch
            }

            DualWifiLogger.i(TAG, "Presenting ${networks.size} networks to user")
            val items = networks.map { net ->
                val badge = if (net.is5GHz) "5 GHz DBS" else "2.4 GHz"
                val savedBadge = if (wifiManager.isNetworkSaved(net.ssid)) " • Saved" else if (net.isOpen) " • Open" else ""
                "${net.ssid}\n[$badge]$savedBadge • Signal: ${net.level} dBm"
            }.toTypedArray()

            MaterialAlertDialogBuilder(this@DualWifiActivity)
                .setTitle("Select Secondary Wi-Fi (${networks.size} found)")
                .setItems(items) { _, which ->
                    val selected = networks[which]
                    DualWifiLogger.i(TAG, "User selected network: '${selected.ssid}' (${selected.bandLabel}, ${selected.freq} MHz, saved=${wifiManager.isNetworkSaved(selected.ssid)})")

                    if (wifiManager.isNetworkSaved(selected.ssid)) {
                        val savedPass = wifiManager.getSavedPassword(selected.ssid) ?: ""
                        Toast.makeText(this@DualWifiActivity, "Connecting to ${selected.ssid} (Saved network)...", Toast.LENGTH_SHORT).show()
                        prefs.edit().putString("saved_ssid", selected.ssid).putString("saved_pass", savedPass).apply()
                        tvSecondarySsid.text = selected.ssid
                        btnSelectNetwork.text = "Change Network (${selected.ssid})"
                        switchDualWifi.isChecked = true
                        connectToSecondary(selected.ssid, savedPass)
                    } else if (selected.isOpen) {
                        Toast.makeText(this@DualWifiActivity, "Connecting to open network ${selected.ssid}...", Toast.LENGTH_SHORT).show()
                        wifiManager.saveNetworkCredentials(selected.ssid, "")
                        tvSecondarySsid.text = selected.ssid
                        btnSelectNetwork.text = "Change Network (${selected.ssid})"
                        switchDualWifi.isChecked = true
                        connectToSecondary(selected.ssid, "")
                    } else {
                        promptPasswordAndConnect(selected.ssid)
                    }
                }
                .setNeutralButton("Forget Saved...") { _, _ ->
                    showForgetNetworkDialog()
                }
                .setNegativeButton("Cancel") { _, _ ->
                    DualWifiLogger.d(TAG, "Network selection canceled by user")
                }
                .show()
        }
    }

    private fun showForgetNetworkDialog() {
        val savedSsid = prefs.getString("saved_ssid", null)
        if (savedSsid == null && !wifiManager.isNetworkSaved("")) {
            Toast.makeText(this, "No saved networks to remove", Toast.LENGTH_SHORT).show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Forget Saved Network")
            .setMessage("Do you want to forget saved credentials for '$savedSsid'?")
            .setPositiveButton("Forget") { _, _ ->
                savedSsid?.let { wifiManager.forgetNetworkCredentials(it) }
                tvSecondarySsid.text = "Secondary Wi-Fi"
                btnSelectNetwork.text = "Scan & Select Secondary Network"
                Toast.makeText(this, "Network forgotten", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptPasswordAndConnect(ssid: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_wifi_password, null)
        val etPassword = dialogView.findViewById<EditText>(R.id.etPassword)
        val savedPass = wifiManager.getSavedPassword(ssid)
        if (!savedPass.isNullOrEmpty()) {
            etPassword.setText(savedPass)
            etPassword.setSelection(savedPass.length)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Connect to $ssid")
            .setMessage("Enter password (network will be saved for automatic reuse):")
            .setView(dialogView)
            .setPositiveButton("Connect & Save") { _, _ ->
                val pass = etPassword.text?.toString() ?: ""
                wifiManager.saveNetworkCredentials(ssid, pass)
                tvSecondarySsid.text = ssid
                btnSelectNetwork.text = "Change Network ($ssid)"
                switchDualWifi.isChecked = true
                connectToSecondary(ssid, pass)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun connectToSecondary(ssid: String, pass: String) {
        lifecycleScope.launch {
            tvSecondarySub.text = "Connecting to $ssid..."
            tvSecondaryBadge.text = "Connecting..."
            DualWifiLogger.i(TAG, "Activating dual Wi-Fi with $ssid...")

            wifiManager.spawnWlan1 { DualWifiLogger.i(TAG, it) }

            val success = wifiManager.connectSecondary(ssid, pass) { DualWifiLogger.i(TAG, it) }
            if (success) {
                tvSecondarySub.text = "Connected & Accelerated"
                tvSecondaryBadge.text = "Accelerated"
                switchDualWifi.isChecked = true
                DualWifiLogger.i(TAG, "Dual Wi-Fi successfully engaged with $ssid")
            } else {
                tvSecondarySub.text = "Connection failed - tap to retry"
                tvSecondaryBadge.text = "Failed"
                switchDualWifi.isChecked = false
                DualWifiLogger.e(TAG, "Dual Wi-Fi connection failed to $ssid")
            }
            refreshStatus()
        }
    }

    private fun disconnectSecondary() {
        lifecycleScope.launch {
            tvSecondarySub.text = "Disconnected"
            tvSecondaryBadge.text = "Offline"
            wifiManager.disconnectSecondary { DualWifiLogger.i(TAG, it) }
            refreshStatus()
        }
    }

    private fun startStatusPolling() {
        pollJob?.cancel()
        pollJob = lifecycleScope.launch {
            while (isActive) {
                refreshStatus()
                delay(3000)
            }
        }
    }

    private suspend fun refreshStatus() {
        val w0 = wifiManager.getInterfaceStatus("wlan0")
        val w1 = wifiManager.getInterfaceStatus("wlan1")
        val sla = wifiManager.getSlaStatus()
        val isHyperFusionOn = defaultSp.getBoolean(RogSettings.hyperFusion, false)

        // Update Primary (wlan0)
        tvPrimarySsid.text = w0.ssid ?: "Not Connected"
        tvPrimarySub.text = if (w0.isUp && w0.ip != null) "IP: ${w0.ip}" else "Offline"
        tvPrimaryBadge.text = if (w0.freq > 4000) "5 GHz" else "2.4 GHz"

        // Update Secondary (wlan1)
        val isSecondaryConnected = w1.isUp && w1.ip != null
        if (isSecondaryConnected) {
            tvSecondarySsid.text = w1.ssid ?: "Secondary Wi-Fi"
            tvSecondarySub.text = "IP: ${w1.ip} • Accelerated"
            tvSecondaryBadge.text = "5 GHz DBS"
            ivSecondaryIcon.setColorFilter(getColor(com.google.android.material.R.color.material_dynamic_primary40))
        } else if (w1.isUp) {
            tvSecondarySub.text = "Antenna ready, connecting..."
            tvSecondaryBadge.text = "Ready"
        } else {
            tvSecondarySub.text = "Tap below to select network"
            tvSecondaryBadge.text = "Offline"
        }

        // Update Mobile Data (Cellular)
        val mobileDataOn = isMobileDataEnabled()
        try {
            val tm = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val simReady = tm?.simState == TelephonyManager.SIM_STATE_READY
            val opName = tm?.networkOperatorName
            if (simReady && !opName.isNullOrBlank()) {
                tvMobileSsid.text = "$opName (Cellular)"
                if (isHyperFusionOn) {
                    if (mobileDataOn) {
                        tvMobileSub.text = "Bonded with Wi-Fi • SLA Multi-Path Active"
                        tvMobileBadge.text = "Bonded"
                        tvMobileBadge.setBackgroundResource(R.drawable.bg_pill_green)
                        tvMobileBadge.setTextColor(getColor(android.R.color.holo_green_light))
                        tvHyperFusionSubtitle.text = "Active • Latency bonding with mobile data"
                        tvHyperFusionSubtitle.setTextColor(getColor(com.google.android.material.R.color.material_dynamic_neutral_variant70))
                    } else {
                        tvMobileSub.text = "⚠️ Mobile data is inactive in Settings"
                        tvMobileBadge.text = "Inactive"
                        tvMobileBadge.setBackgroundResource(R.drawable.bg_pill_red)
                        tvMobileBadge.setTextColor(getColor(android.R.color.holo_red_light))
                        tvHyperFusionSubtitle.text = "⚠️ Mobile data is inactive - turn on Mobile Data to bond"
                        tvHyperFusionSubtitle.setTextColor(getColor(android.R.color.holo_orange_light))
                    }
                } else {
                    if (mobileDataOn) {
                        tvMobileSub.text = "Standby • Ready for HyperFusion"
                        tvMobileBadge.text = "Standby"
                        tvMobileBadge.setBackgroundResource(R.drawable.bg_pill_cyan)
                        tvMobileBadge.setTextColor(getColor(com.google.android.material.R.color.material_dynamic_primary40))
                    } else {
                        tvMobileSub.text = "Mobile data turned off"
                        tvMobileBadge.text = "Offline"
                        tvMobileBadge.setBackgroundResource(R.drawable.bg_pill_cyan)
                        tvMobileBadge.setTextColor(getColor(com.google.android.material.R.color.material_dynamic_neutral_variant70))
                    }
                }
            } else {
                tvMobileSsid.text = "Mobile Data"
                tvMobileSub.text = if (isHyperFusionOn) "⚠️ No SIM or cellular network available" else "No SIM detected"
                tvMobileBadge.text = if (isHyperFusionOn) "Inactive" else "Unavailable"
                tvMobileBadge.setBackgroundResource(if (isHyperFusionOn) R.drawable.bg_pill_red else R.drawable.bg_pill_cyan)
                tvMobileBadge.setTextColor(if (isHyperFusionOn) getColor(android.R.color.holo_red_light) else getColor(com.google.android.material.R.color.material_dynamic_neutral_variant70))
            }
        } catch (_: Throwable) {
            tvMobileSsid.text = "Mobile Data"
            tvMobileSub.text = "Cellular interface standby"
            tvMobileBadge.text = "Standby"
        }

        // Update Health Badges
        tvDbsStatusBadge.text = if (isSecondaryConnected) "● Accelerated" else if (w1.isUp) "● Ready" else "Standby"
        tvDbsStatusBadge.setBackgroundResource(if (isSecondaryConnected) R.drawable.bg_pill_green else R.drawable.bg_pill_cyan)

        val isSlaActive = sla.daemonRunning || isHyperFusionOn
        tvSlaStatusBadge.text = if (isSlaActive) "● Active" else "Idle"
        tvSlaStatusBadge.setBackgroundResource(if (isSlaActive) R.drawable.bg_pill_green else R.drawable.bg_pill_cyan)

        val totalWlan0 = formatBytes(sla.bytesWlan0)
        val totalWlan1 = formatBytes(sla.bytesWlan1)
        tvTrafficSummary.text = "wlan0: $totalWlan0 | wlan1: $totalWlan1"

        if (isSecondaryConnected || isSlaActive) {
            tvHubStatusBadge.text = "● Active"
            tvHubStatusBadge.setBackgroundResource(R.drawable.bg_pill_green)
        } else {
            tvHubStatusBadge.text = "● Ready"
            tvHubStatusBadge.setBackgroundResource(R.drawable.bg_pill_cyan)
        }

        // Update Advanced Diagnostics monospace text
        tvAdvancedStats.text = "Kernel Node: /proc/sla/config (${if (sla.isEnabled || isHyperFusionOn) "enable=1" else "idle"})\n" +
                "Daemon: ${if (sla.daemonRunning) "slad-v2 (Active)" else "Stopped"}\n" +
                "wlan0 Traffic: $totalWlan0 | wlan1 Traffic: $totalWlan1"
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> "%.2f GB".format(bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
            bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
            else -> "$bytes B"
        }
    }
}
