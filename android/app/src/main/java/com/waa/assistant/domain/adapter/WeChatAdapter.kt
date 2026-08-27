package com.waa.assistant.domain.adapter

import com.waa.assistant.data.model.IncomingMessage

/**
 * 微信接入层独立 Adapter。
 * 禁止破解/注入微信；仅使用系统授权能力或模拟源。
 */
interface WeChatAdapter {
    val id: String
    suspend fun start(onMessage: suspend (IncomingMessage) -> Unit)
    suspend fun stop()
    suspend fun send(conversationId: String, conversationName: String, text: String): Result<Unit>
    suspend fun health(): Pair<Boolean, String>
}
