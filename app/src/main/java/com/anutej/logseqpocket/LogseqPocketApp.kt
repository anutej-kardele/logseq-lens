package com.anutej.logseqpocket

import android.app.Application
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.anutej.logseqpocket.ui.scratchpad.FloatingScratchpadActivity
import com.anutej.logseqpocket.widget.scratchpadIntent

class LogseqPocketApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        publishShortcuts()
    }

    /** Long-press launcher shortcuts. Dynamic so they work for both debug and release package ids. */
    private fun publishShortcuts() {
        val icon = IconCompat.createWithResource(this, R.drawable.ic_shortcut_scratchpad)
        val shortcuts = listOf(
            ShortcutInfoCompat.Builder(this, "scratchpad")
                .setShortLabel(getString(R.string.scratchpad))
                .setIcon(icon)
                .setIntent(scratchpadIntent(this))
                .build(),
            ShortcutInfoCompat.Builder(this, "scratchpad_new_item")
                .setShortLabel("New to-do")
                .setLongLabel("New Scratchpad item")
                .setIcon(icon)
                .setIntent(FloatingScratchpadActivity.intent(this, newItem = true).setAction(Intent.ACTION_VIEW))
                .build(),
        )
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(this, shortcuts) }
    }
}
