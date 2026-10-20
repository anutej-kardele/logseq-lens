package com.anutej.logseqpocket

import android.content.Context

/** Manual dependency wiring; one instance per process. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
}
