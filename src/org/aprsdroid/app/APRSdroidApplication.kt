package org.aprsdroid.app

import android.app.Application

class APRSdroidApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceNotifier.instance.setupChannels(this)
        MapModes.initialize(this)
    }
}
