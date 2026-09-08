package me.phh.treble.app

import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.slider.Slider

// Live color-picker screen for ROG5/5s Aura Sync, styled after ASUS's own
// Armoury Crate "Aura Lighting" UI (HSV wheel + brightness/rate sliders)
// rather than the raw 0-255 EditTextPreference fields this replaced.
// Values are written through the existing RogSettings SharedPreferences
// keys, so Rog.kt's already-registered listener still owns the actual
// sysfs writes - no hardware-access logic duplicated here.
//
// Mode values 0-4 are confirmed to apply without error (apply_state=0) on
// ASUS_I005D/ASUS_I005_1 firmware 33.0210.0210.200-0, but no named presets
// are used for it - what each raw index actually does visually is still
// unverified, and ASUS never published this. "Rate" maps to the MCU's real
// speed register, which only accepts 0/1/2/254/255 (see Rog.kt) - only
// 0/1/2 are exposed here since 254/255's meaning isn't documented either.
class AuraSyncActivity : AppCompatActivity() {
    private lateinit var sp: SharedPreferences
    private val debounceHandler = Handler(Looper.getMainLooper())
    private var pendingColorWrite: Runnable? = null

    private lateinit var colorWheel: ColorWheelView
    private lateinit var centerSwatch: View
    private lateinit var hexLabel: TextView
    private lateinit var switchEnable: SwitchCompat
    private lateinit var sliderBrightness: Slider
    private lateinit var sliderRate: Slider
    private lateinit var modeValue: TextView
    private lateinit var modeRow: LinearLayout

    private val modeRange = 0..20
    private val rateLabels = arrayOf("Slow", "Medium", "Fast")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_aura_sync)
        sp = PreferenceManager.getDefaultSharedPreferences(this)

        colorWheel = findViewById(R.id.colorWheel)
        centerSwatch = findViewById(R.id.centerSwatch)
        hexLabel = findViewById(R.id.hexLabel)
        switchEnable = findViewById(R.id.switchEnable)
        sliderBrightness = findViewById(R.id.sliderBrightness)
        sliderRate = findViewById(R.id.sliderRate)
        modeValue = findViewById(R.id.modeValue)
        modeRow = findViewById(R.id.modeRow)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnReset).setOnClickListener { resetToDefaults() }

        val startRed = sp.getString(RogSettings.auraRed, "255")?.toIntOrNull() ?: 255
        val startGreen = sp.getString(RogSettings.auraGreen, "255")?.toIntOrNull() ?: 255
        val startBlue = sp.getString(RogSettings.auraBlue, "255")?.toIntOrNull() ?: 255
        val startMode = sp.getString(RogSettings.auraMode, "0")?.toIntOrNull() ?: 0
        val startRate = sp.getString(RogSettings.auraSpeed, "1")?.toIntOrNull()?.takeIf { it in 0..2 } ?: 1

        val hsv = FloatArray(3)
        Color.RGBToHSV(startRed, startGreen, startBlue, hsv)

        switchEnable.isChecked = sp.getBoolean(RogSettings.auraEnable, false)
        colorWheel.setHueSat(hsv[0], hsv[1])
        colorWheel.setValue(hsv[2])
        sliderBrightness.value = (hsv[2] * 100f)
        sliderRate.value = startRate.toFloat()
        updateModeLabel(startMode)
        updatePreview(startRed, startGreen, startBlue)

        switchEnable.setOnCheckedChangeListener { _, checked ->
            sp.edit().putBoolean(RogSettings.auraEnable, checked).apply()
        }

        colorWheel.onColorChange = { _, _ ->
            applyWheelColor()
        }

        sliderBrightness.addOnChangeListener { _, value, _ ->
            colorWheel.setValue(value / 100f)
            applyWheelColor()
        }

        sliderRate.addOnChangeListener { _, value, _ ->
            sp.edit().putString(RogSettings.auraSpeed, value.toInt().toString()).apply()
        }

        modeRow.setOnClickListener { showModeMenu() }

        buildPresets()
    }

    private fun applyWheelColor() {
        val color = colorWheel.currentColor()
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        updatePreview(r, g, b)
        scheduleColorWrite {
            sp.edit()
                .putString(RogSettings.auraRed, r.toString())
                .putString(RogSettings.auraGreen, g.toString())
                .putString(RogSettings.auraBlue, b.toString())
                .apply()
        }
    }

    private fun showModeMenu() {
        val popup = PopupMenu(this, modeRow)
        for (m in modeRange) popup.menu.add(0, m, m, "Mode $m")
        popup.setOnMenuItemClickListener { item ->
            setMode(item.itemId)
            true
        }
        popup.show()
    }

    private fun setMode(mode: Int) {
        updateModeLabel(mode)
        sp.edit().putString(RogSettings.auraMode, mode.toString()).apply()
    }

    private fun updateModeLabel(mode: Int) {
        modeValue.text = "Mode $mode"
    }

    private fun updatePreview(r: Int, g: Int, b: Int) {
        val color = Color.rgb(r, g, b)
        (centerSwatch.background as? GradientDrawable)?.setColor(color)
        hexLabel.text = String.format("#%02X%02X%02X", r, g, b)
        hexLabel.setTextColor(if (isDark(r, g, b)) Color.WHITE else Color.BLACK)
    }

    private fun isDark(r: Int, g: Int, b: Int): Boolean {
        val luminance = 0.299 * r + 0.587 * g + 0.114 * b
        return luminance < 140
    }

    // Sysfs writes go through a real i2c transaction per attribute (see
    // Rog.kt/applyAura) - coalescing rapid wheel/brightness drags to one
    // write every 80ms avoids hammering the MCU with a write per pixel of
    // drag motion.
    private fun scheduleColorWrite(block: () -> Unit) {
        pendingColorWrite?.let { debounceHandler.removeCallbacks(it) }
        val r = Runnable { block() }
        pendingColorWrite = r
        debounceHandler.postDelayed(r, 80)
    }

    private fun resetToDefaults() {
        colorWheel.setHueSat(0f, 0f)
        colorWheel.setValue(1f)
        sliderBrightness.value = 100f
        sliderRate.value = 1f
        setMode(0)
        updatePreview(255, 255, 255)
        sp.edit()
            .putString(RogSettings.auraRed, "255")
            .putString(RogSettings.auraGreen, "255")
            .putString(RogSettings.auraBlue, "255")
            .putString(RogSettings.auraSpeed, "1")
            .apply()
    }

    private fun buildPresets() {
        val row = findViewById<LinearLayout>(R.id.presetRow)
        val presets = listOf(
            "Red" to Triple(255, 0, 0),
            "Green" to Triple(0, 255, 0),
            "Blue" to Triple(0, 0, 255),
            "White" to Triple(255, 255, 255),
            "Purple" to Triple(160, 32, 240),
            "Cyan" to Triple(0, 255, 255),
            "Off" to Triple(0, 0, 0)
        )
        val sizePx = (48 * resources.displayMetrics.density).toInt()
        val marginPx = (10 * resources.displayMetrics.density).toInt()
        for ((name, rgb) in presets) {
            val (r, g, b) = rgb
            val swatch = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.rgb(r, g, b))
                    setStroke((1 * resources.displayMetrics.density).toInt(), Color.parseColor("#55FFFFFF"))
                }
                contentDescription = name
                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                    marginEnd = marginPx
                }
                setOnClickListener {
                    val hsv = FloatArray(3)
                    Color.RGBToHSV(r, g, b, hsv)
                    colorWheel.setHueSat(hsv[0], hsv[1])
                    colorWheel.setValue(hsv[2])
                    sliderBrightness.value = hsv[2] * 100f
                    updatePreview(r, g, b)
                    if (!switchEnable.isChecked) switchEnable.isChecked = true
                    sp.edit()
                        .putString(RogSettings.auraRed, r.toString())
                        .putString(RogSettings.auraGreen, g.toString())
                        .putString(RogSettings.auraBlue, b.toString())
                        .apply()
                }
            }
            row.addView(swatch)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pendingColorWrite?.let { debounceHandler.removeCallbacks(it) }
    }
}
