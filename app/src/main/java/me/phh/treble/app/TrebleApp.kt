package me.phh.treble.app

import android.app.Application
import com.google.android.material.color.DynamicColors

class TrebleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
