package com.waa.assistant.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.waa.assistant.R
import com.waa.assistant.WaaApp
import com.waa.assistant.data.model.RuntimeStatus
import com.waa.assistant.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AssistantForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val app = WaaApp.instance
        when (action) {
            ACTION_PAUSE -> app.engine.pause()
            ACTION_RESUME -> app.engine.resume()
            ACTION_STOP -> {
                app.engine.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                scope.launch {
                    val settings = app.settingsRepo.get()
                    app.engine.start(settings)
                }
            }
        }
        startForeground(NOTIFY_ID, buildNotification("运行中"))
        observeJob?.cancel()
        observeJob = scope.launch {
            app.engine.status.collect { st ->
                val text = when (st) {
                    RuntimeStatus.RUNNING -> getString(R.string.fg_text_running)
                    RuntimeStatus.PAUSED -> getString(R.string.fg_text_paused)
                    RuntimeStatus.STOPPED -> "已停止"
                }
                val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                nm.notify(NOTIFY_ID, buildNotification(text))
            }
        }
        return START_STICKY
    }

    private fun buildNotification(content: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pause = PendingIntent.getService(
            this, 1,
            Intent(this, AssistantForegroundService::class.java).setAction(ACTION_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 2,
            Intent(this, AssistantForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, WaaApp.CHANNEL_FG)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.fg_title))
            .setContentText(content)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(0, "暂停", pause)
            .addAction(0, "停止", stop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        observeJob?.cancel()
        super.onDestroy()
    }

    companion object {
        const val NOTIFY_ID = 1001
        const val ACTION_PAUSE = "com.waa.assistant.action.PAUSE"
        const val ACTION_RESUME = "com.waa.assistant.action.RESUME"
        const val ACTION_STOP = "com.waa.assistant.action.STOP"

        fun start(context: Context) {
            val i = Intent(context, AssistantForegroundService::class.java)
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            val i = Intent(context, AssistantForegroundService::class.java).setAction(ACTION_STOP)
            context.startService(i)
        }
    }
}
