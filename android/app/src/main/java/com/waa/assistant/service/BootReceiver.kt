package com.waa.assistant.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.waa.assistant.WaaApp
import com.waa.assistant.data.model.RuntimeStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val settings = WaaApp.instance.settingsRepo.get()
                if (settings.runtimeStatus == RuntimeStatus.RUNNING ||
                    settings.runtimeStatus == RuntimeStatus.PAUSED
                ) {
                    AssistantForegroundService.start(context.applicationContext)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
