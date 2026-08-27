package com.waa.assistant.domain.context

import com.waa.assistant.data.db.MessageDao
import com.waa.assistant.data.model.IncomingMessage
import com.waa.assistant.data.model.MessageEntity

class ContextManager(private val messageDao: MessageDao) {
    suspend fun load(conversationId: String, size: Int): List<IncomingMessage> {
        if (size <= 0) return emptyList()
        return messageDao.latest(conversationId, size)
            .sortedBy { it.timestamp }
            .map { it.toIncoming() }
    }

    suspend fun clear(conversationId: String) = messageDao.clearConversation(conversationId)

    suspend fun clearAll() = messageDao.clearAll()
}

fun MessageEntity.toIncoming(): IncomingMessage = IncomingMessage(
    id = id,
    conversationId = conversationId,
    conversationName = senderName,
    senderId = senderId,
    senderName = senderName,
    content = content,
    timestamp = timestamp,
    isSelf = isSelf,
    fingerprint = fingerprint
)
