package com.waa.assistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.waa.assistant.data.db.AppDatabase
import com.waa.assistant.data.prefs.SettingsRepository
import com.waa.assistant.domain.engine.MessageEngine

class WaaApp : Application() {
    lateinit var db: AppDatabase
        private set
    lateinit var settingsRepo: SettingsRepository
        private set
    lateinit var engine: MessageEngine
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        db = AppDatabase.get(this)
        settingsRepo = SettingsRepository(this)
        engine = MessageEngine(db, settingsRepo)
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_FG,
                getString(R.string.fg_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.fg_channel_desc)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_FG = "waa_fg"
        lateinit var instance: WaaApp
            private set
    }
}
