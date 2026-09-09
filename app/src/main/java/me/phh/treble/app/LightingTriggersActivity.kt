package me.phh.treble.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.NotificationManagerCompat

// Entry point for the 4 event-triggered Aura Sync lighting profiles (see
// RogEvents.kt/RogSettings.eventScenarios). Each row's switch just toggles
// that scenario's own "enable" key; tapping the row opens the same
// AuraSyncActivity editor used for the main "Screen on" color, scoped to
// that scenario's own color/mode/speed keys.
class LightingTriggersActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lighting_triggers)
        val sp = PreferenceManager.getDefaultSharedPreferences(this)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        bindRow(R.id.rowRinging, R.id.switchRinging, "ringing")
        bindRow(R.id.rowNotification, R.id.switchNotification, "notification")
        bindRow(R.id.rowMusic, R.id.switchMusic, "music")
        bindRow(R.id.rowCharging, R.id.switchCharging, "charging")

        findViewById<View>(R.id.notifAccessButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        val hasNotificationAccess = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        findViewById<View>(R.id.notifAccessRow).visibility = if (hasNotificationAccess) View.GONE else View.VISIBLE
    }

    private fun bindRow(rowId: Int, switchId: Int, scenario: String) {
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        val keys = RogSettings.keySetFor(scenario)
        val row = findViewById<View>(rowId)
        val switch = findViewById<SwitchCompat>(switchId)

        switch.isChecked = sp.getBoolean(keys.enable, false)
        switch.setOnCheckedChangeListener { _, checked ->
            sp.edit().putBoolean(keys.enable, checked).apply()
        }
        row.setOnClickListener {
            startActivity(Intent(this, AuraSyncActivity::class.java).putExtra(AuraSyncActivity.EXTRA_SCENARIO, scenario))
        }
    }
}
