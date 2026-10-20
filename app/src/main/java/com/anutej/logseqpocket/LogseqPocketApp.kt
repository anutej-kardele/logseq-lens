package com.anutej.logseqpocket

import android.app.Application

class LogseqPocketApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
