package com.kairos.app

import android.app.Application
import com.kairos.app.system.Notifier

class KairosApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannels(this)
    }
}
