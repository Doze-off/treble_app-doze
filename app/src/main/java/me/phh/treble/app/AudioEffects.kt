package me.phh.treble.app

import android.app.Fragment
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.audiofx.AudioEffect
import android.os.Bundle
import android.preference.Preference
import android.preference.PreferenceCategory
import android.preference.PreferenceFragment
import android.preference.SwitchPreference
import android.view.View
import android.widget.ListView
import java.util.UUID


import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding


class AudioEffectsFragment : PreferenceFragment() {
    val effects = AudioEffect.queryEffects()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferenceScreen = preferenceManager.createPreferenceScreen(activity)

        // Cria a categoria GENERAL com layout custom
        val generalCategory = PreferenceCategory(activity).apply {
            title = "GENERAL"
            layoutResource = R.layout.preference_category_custom
        }
        preferenceScreen.addPreference(generalCategory)

        // Aviso fixo dentro da categoria GENERAL com layout custom
        generalCategory.addPreference(Preference(activity).apply {
            title = "Restart media app to apply change"
            summary = "Currently effects are applied exclusively on media output"
            layoutResource = R.layout.preference_custom
        })

        // Adiciona os switches dos efeitos dentro da categoria GENERAL, com layout custom
        effects.forEach {
            val isSupported = it.connectMode == AudioEffect.EFFECT_INSERT
            android.util.Log.d("PHH", "Effect ${it.name} is supported: $isSupported, connectMode ${it.connectMode}")

            if (!isSupported) return@forEach

                val pref = SwitchPreference(activity).apply {
                    title = it.name
                    key = "audio_effect_" + it.uuid.toString()
                    summary = "By ${it.implementor}"
                    layoutResource = R.layout.preference_switch_custom
                }
                generalCategory.addPreference(pref)
        }
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
}

object AudioEffects : SharedPreferences.OnSharedPreferenceChangeListener {
    val takenEffects = mutableMapOf<UUID, Any>()
    val effects = AudioEffect.queryEffects()
    val effectNull = AudioEffect::class.java.getField("EFFECT_TYPE_NULL").get(null) as UUID

    fun startup(context: Context) {
        val sp = android.preference.PreferenceManager.getDefaultSharedPreferences(context)
        effects.forEach {
            onSharedPreferenceChanged(sp, "audio_effect_" + it.uuid.toString())
        }
        sp.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences, pref: String) {
        android.util.Log.e("PHH", "Clicked on preference $pref")
        val effect = effects.find { "audio_effect_" + it.uuid.toString() == pref }
        if (effect == null) {
            android.util.Log.d("PHH", "No effect found for key $pref")
            return
        }

        val enabled = sp.getBoolean(pref, false)
        if (enabled) {
            android.util.Log.e("PHH", "Creating effect ${effect.uuid} ${effect.name}")
            val o = Class.forName("android.media.audiofx.StreamDefaultEffect")
            .getConstructor(UUID::class.java, UUID::class.java, Int::class.java, Int::class.java)
            .newInstance(effectNull, effect.uuid, 0, AudioAttributes.USAGE_MEDIA) as Any
            takenEffects[effect.uuid] = o
            android.util.Log.e("PHH", "Succeeded")
        } else {
            val o = takenEffects[effect.uuid]
            if (o == null) {
                android.util.Log.e("PHH", "No taken effect found for key ${effect.uuid}")
                return
            }
            o.javaClass.getMethod("release").invoke(o)
            takenEffects.remove(effect.uuid)
        }
    }
}
