package com.waa.assistant.data.prefs

import com.waa.assistant.data.model.AiProviderType
import com.waa.assistant.data.model.ReplyMode
import com.waa.assistant.data.model.ReplyStyle
import com.waa.assistant.data.model.RuntimeStatus
import com.waa.assistant.domain.ai.FreeModelCatalog

data class KeywordRule(
    val keyword: String,
    val enabled: Boolean = true
)

data class AppSettings(
    val runtimeStatus: RuntimeStatus = RuntimeStatus.STOPPED,
    val lastStartedAt: Long? = null,
    val lastStoppedAt: Long? = null,
    val lastError: String? = null,

    /** 默认使用 DeepSeek（市面免费精选） */
    val aiProvider: AiProviderType = AiProviderType.DEEPSEEK,
    val style: ReplyStyle = ReplyStyle.NATURAL,
    val maxReplyLength: Int = 80,
    val contextSize: Int = 8,
    val temperature: Float = 0.7f,
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,

    val ollamaBaseUrl: String = "http://127.0.0.1:11434",
    val ollamaModel: String = "qwen2.5:7b",

    /** 当前云端/兼容接口配置（随精选模型切换自动填充） */
    val openaiBaseUrl: String = FreeModelCatalog.DEEPSEEK.baseUrl,
    val openaiApiKey: String = "",
    val openaiModel: String = FreeModelCatalog.DEEPSEEK.model,

    /** 各精选平台独立保存 Key，切换时自动带出 */
    val deepseekApiKey: String = "",
    val geminiApiKey: String = "",
    val siliconflowApiKey: String = "",
    val openrouterApiKey: String = "",

    /** 云端失败时是否回退到离线模板 */
    val offlineFallbackEnabled: Boolean = true,

    val replyMode: ReplyMode = ReplyMode.ASSIST,
    val autoReplyEnabled: Boolean = true,
    val cooldownSeconds: Int = 20,
    val maxPerMinute: Int = 8,
    val maxPerDay: Int = 200,
    val mergeWindowSeconds: Int = 8,
    val workHoursEnabled: Boolean = false,
    val workStart: String = "09:00",
    val workEnd: String = "18:00",
    val workHoursPolicy: String = "work",
    val offHoursPolicy: String = "off",
    val globalEnabled: Boolean = true,
    val useContext: Boolean = true,
    val autoPauseOnError: Boolean = true,
    val errorPauseThreshold: Int = 3,

    val blacklist: List<String> = emptyList(),
    val whitelist: List<String> = emptyList(),
    val whitelistOnly: Boolean = false,
    val contactAllow: List<String> = emptyList(),
    val groupAllow: List<String> = emptyList(),
    val keywords: List<KeywordRule> = listOf(
        KeywordRule("在吗"),
        KeywordRule("你好")
    ),

    /** simulator | notification | accessibility_send */
    val adapterType: String = "simulator",
    val permissionIntroAccepted: Boolean = false
) {
    fun apiKeyForCurrentProvider(): String = when (aiProvider) {
        AiProviderType.DEEPSEEK -> deepseekApiKey.ifBlank { openaiApiKey }
        AiProviderType.GEMINI -> geminiApiKey.ifBlank { openaiApiKey }
        AiProviderType.SILICONFLOW -> siliconflowApiKey.ifBlank { openaiApiKey }
        AiProviderType.OPENROUTER -> openrouterApiKey.ifBlank { openaiApiKey }
        AiProviderType.OPENAI_COMPATIBLE -> openaiApiKey
        else -> openaiApiKey
    }

    companion object {
        const val DEFAULT_SYSTEM_PROMPT =
            "你是微信聊天助手，用自然口语回复，像真人一样简短、礼貌、有温度，不要机械套话，不要暴露自己是 AI。"
    }
}
