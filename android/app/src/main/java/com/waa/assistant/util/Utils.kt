package com.waa.assistant.util

import com.waa.assistant.data.model.IncomingMessage
import java.util.Calendar
import java.util.UUID

object AppIds {
    fun newId(prefix: String = "id"): String =
        "${prefix}_${System.currentTimeMillis().toString(36)}_${UUID.randomUUID().toString().take(8)}"
}

object Fingerprint {
    fun of(msg: IncomingMessage): String {
        val raw = listOf(msg.conversationId, msg.senderId, msg.timestamp, msg.content).joinToString("|")
        var h = 5381
        for (c in raw) h = ((h shl 5) + h) xor c.code
        return Integer.toHexString(h)
    }
}

object DayUtils {
    fun startOfToday(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }
}
