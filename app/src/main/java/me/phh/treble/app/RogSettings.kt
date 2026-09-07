package me.phh.treble.app

import java.io.File

object RogSettings : Settings {
    // AeroActive Cooler 5 (detachable accessory, HID MCU "rog5_inbox")
    val coolerFanEnable = "rog_cooler_fan_enable"
    val coolerFanSpeed = "rog_cooler_fan_speed"

    // Aura Sync RGB - phone body / side rail / back cover / cooler, all
    // driven the same way (separate MS51/rog5_inbox MCUs, same attr set)
    val auraEnable = "rog_aura_enable"
    val auraMode = "rog_aura_mode"
    val auraRed = "rog_aura_red"
    val auraGreen = "rog_aura_green"
    val auraBlue = "rog_aura_blue"

    // ASUS_I005D/ASUS_I005_1 is the one shared device fingerprint for both
    // ROG Phone 5 (ZS673KS) and ROG Phone 5s (ZS676KS) - confirmed via real
    // stock build.prop, not assumed.
    override fun enabled() = Tools.vendorFpLow.contains("i005d")

    // AeroActive Cooler 5 is detachable - its "rog5_inbox" HID MCU is only
    // enumerated in sysfs while physically attached, so gate its controls on
    // that rather than just on enabled().
    fun coolerPresent() = File("/sys/class/leds/aura_inbox/fan_enable").exists()
}

class RogSettingsFragment : SettingsFragment() {
    override val preferencesResId = R.xml.pref_rog
}
