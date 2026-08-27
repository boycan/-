package com.waa.assistant.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ConversationType { CONTACT, GROUP }
enum class ContentType { TEXT, IMAGE, OTHER }
enum class ReplyMode { AUTO, ASSIST, OFF }
enum class RuntimeStatus { STOPPED, RUNNING, PAUSED }
enum class JobStatus { PENDING, GENERATING, REVIEW, SENDING, SENT, IGNORED, FAILED, SKIPPED }
enum class AiProviderType {
    /** 默认：DeepSeek 免费精选 */
    DEEPSEEK,
    /** Google Gemini 免费额度 */
    GEMINI,
    /** 硅基流动（国内，Qwen/DeepSeek 等） */
    SILICONFLOW,
    /** OpenRouter 免费路由 */
    OPENROUTER,
    /** 本机 Ollama */
    OLLAMA,
    /** 自定义 OpenAI Compatible */
    OPENAI_COMPATIBLE,
    /** 离线模板兜底（无网/无 Key 时） */
    OFFLINE_FALLBACK
}
enum class ReplyStyle { NATURAL, CONCISE, FORMAL, WARM, PROFESSIONAL, HUMOROUS }

data class IncomingMessage(
    val id: String,
    val conversationId: String,
    val conversationName: String,
    val conversationType: ConversationType = ConversationType.CONTACT,
    val senderId: String,
    val senderName: String,
    val content: String,
    val contentType: ContentType = ContentType.TEXT,
    val timestamp: Long,
    val isSelf: Boolean = false,
    val fingerprint: String = "",
    val raw: Map<String, String> = emptyMap()
)

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val lastMessage: String = "",
    val lastAiReply: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val messageCount: Int = 0,
    val replyCount: Int = 0
)

@Entity(
    tableName = "messages",
    indices = [
        Index("conversationId"),
        Index("timestamp"),
        Index(value = ["fingerprint"], unique = false)
    ]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val contentType: String = ContentType.TEXT.name,
    val timestamp: Long,
    val isSelf: Boolean = false,
    val fingerprint: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "jobs",
    indices = [Index("status"), Index("createdAt"), Index("conversationId")]
)
data class ReplyJobEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val conversationName: String,
    val messageId: String,
    val incomingText: String,
    val generatedText: String = "",
    val editedText: String = "",
    val status: String = JobStatus.PENDING.name,
    val provider: String = "",
    val intent: String = "",
    val error: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val repliedAt: Long? = null
)

@Entity(
    tableName = "logs",
    indices = [Index("ts"), Index("level"), Index("category")]
)
data class LogEntity(
    @PrimaryKey val id: String,
    val ts: Long = System.currentTimeMillis(),
    val level: String,
    val category: String,
    val message: String,
    val extra: String? = null
)

data class AiGenerateResult(
    val provider: String,
    val model: String,
    val text: String,
    val intent: String? = null
)

data class DashboardStats(
    val runtimeStatus: RuntimeStatus = RuntimeStatus.STOPPED,
    val modelLabel: String = "DeepSeek · 待配置 Key",
    val modelReady: Boolean = true,
    val todayReceived: Int = 0,
    val todayAiReplies: Int = 0,
    val todayAutoSent: Int = 0,
    val activeConversations: Int = 0,
    val pendingReview: Int = 0
)
