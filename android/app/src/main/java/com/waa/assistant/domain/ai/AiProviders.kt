package com.waa.assistant.domain.ai

import com.waa.assistant.data.model.AiGenerateResult
import com.waa.assistant.data.model.AiProviderType
import com.waa.assistant.data.model.IncomingMessage
import com.waa.assistant.data.model.ReplyStyle
import com.waa.assistant.data.prefs.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.abs

interface AiProvider {
    suspend fun generate(
        message: IncomingMessage,
        context: List<IncomingMessage>,
        settings: AppSettings
    ): AiGenerateResult
}

/**
 * 离线模板兜底：无 Key / 无网时使用。质量弱于云端大模型，仅作保底。
 */
class OfflineFallbackProvider : AiProvider {
    override suspend fun generate(
        message: IncomingMessage,
        context: List<IncomingMessage>,
        settings: AppSettings
    ): AiGenerateResult {
        delay(120L + (0..180).random())
        val intent = classify(message.content)
        val text = compose(intent, message, context, settings.style, settings.maxReplyLength, settings.temperature)
        return AiGenerateResult(
            provider = "offline-fallback",
            model = "offline-zh-chat",
            text = text,
            intent = intent
        )
    }

    private fun classify(text: String): String {
        val rules = listOf(
            "greeting" to Regex("(你好|在吗|在么|嗨|哈喽|hello|hi|早上好|晚上好|下午好|在不在)", RegexOption.IGNORE_CASE),
            "thanks" to Regex("(谢谢|感谢|多谢|辛苦)"),
            "bye" to Regex("(再见|拜拜|晚安|先这样|回头聊)"),
            "ask_time" to Regex("(几点|什么时候|哪天|明天|后天|周末)"),
            "ask_ok" to Regex("(方便|可以吗|行不行|能不能|是否)"),
            "confirm" to Regex("(好的|收到|嗯嗯|ok|OK|可以|没问题|行)"),
            "location" to Regex("(地址|在哪|位置|怎么走)"),
            "price" to Regex("(价格|多少钱|费用|报价)"),
            "meeting" to Regex("(开会|会议|见面|约一下)"),
            "wait" to Regex("(等一下|稍后|稍后回|忙)"),
            "question" to Regex("(吗|么|？|\\?|怎么|为什么|如何)")
        )
        return rules.firstOrNull { it.second.containsMatchIn(text) }?.first ?: "chat"
    }

    private fun compose(
        intent: String,
        msg: IncomingMessage,
        ctx: List<IncomingMessage>,
        style: ReplyStyle,
        maxLen: Int,
        temperature: Float
    ): String {
        val seed = seedFrom(msg.content + intent + style.name, temperature)
        val banks = mapOf(
            "greeting" to mapOf(
                ReplyStyle.NATURAL to listOf("你好呀，在的，有什么事情可以直接跟我说～", "在的在的，刚看到消息。"),
                ReplyStyle.FORMAL to listOf("您好，我在，请讲。"),
                ReplyStyle.CONCISE to listOf("在的，请说。"),
                ReplyStyle.WARM to listOf("你好呀～我在的，随时说～"),
                ReplyStyle.PROFESSIONAL to listOf("您好，我在线，请直接说明需求。"),
                ReplyStyle.HUMOROUS to listOf("在的在的，信号满格。")
            ),
            "thanks" to mapOf(
                ReplyStyle.NATURAL to listOf("不客气，有需要再叫我。"),
                ReplyStyle.FORMAL to listOf("不客气，这是应该的。"),
                ReplyStyle.CONCISE to listOf("不客气。"),
                ReplyStyle.WARM to listOf("太客气啦～举手之劳。"),
                ReplyStyle.PROFESSIONAL to listOf("不客气，后续我继续跟进。"),
                ReplyStyle.HUMOROUS to listOf("谢什么谢，都是自己人。")
            ),
            "bye" to mapOf(
                ReplyStyle.NATURAL to listOf("好，回头聊。"),
                ReplyStyle.FORMAL to listOf("好的，再见。"),
                ReplyStyle.CONCISE to listOf("好，再见。"),
                ReplyStyle.WARM to listOf("好呀，注意休息～"),
                ReplyStyle.PROFESSIONAL to listOf("好的，我们保持同步。"),
                ReplyStyle.HUMOROUS to listOf("行，我先去充电了。")
            ),
            "confirm" to mapOf(
                ReplyStyle.NATURAL to listOf("好的，我记下了。", "嗯嗯，收到。"),
                ReplyStyle.FORMAL to listOf("好的，已确认。"),
                ReplyStyle.CONCISE to listOf("收到。"),
                ReplyStyle.WARM to listOf("好嘞～我知道啦。"),
                ReplyStyle.PROFESSIONAL to listOf("收到，我这边按这个推进。"),
                ReplyStyle.HUMOROUS to listOf("收到收到，已存档到大脑C盘。")
            ),
            "chat" to mapOf(
                ReplyStyle.NATURAL to listOf("嗯嗯，我明白了。", "收到，我这边看着办。"),
                ReplyStyle.FORMAL to listOf("已了解，我会妥善处理。"),
                ReplyStyle.CONCISE to listOf("明白。"),
                ReplyStyle.WARM to listOf("嗯嗯我懂啦～"),
                ReplyStyle.PROFESSIONAL to listOf("信息已收到，我继续处理。"),
                ReplyStyle.HUMOROUS to listOf("懂了懂了，脑内已加载。")
            )
        )
        val styleBank = banks[intent]?.get(style) ?: banks.getValue("chat").getValue(ReplyStyle.NATURAL)
        var reply = styleBank[abs(seed) % styleBank.size]
        if (intent == "chat" && msg.content.length > 8 && temperature >= 0.45f) {
            val echo = msg.content.replace(Regex("[。！？!?～~]+$"), "").take(18)
            reply = when (style) {
                ReplyStyle.CONCISE -> "「$echo」收到。"
                ReplyStyle.FORMAL -> "关于「$echo」，我已了解。"
                ReplyStyle.WARM -> "「$echo」我看到啦～"
                ReplyStyle.PROFESSIONAL -> "「$echo」已记录，我继续处理。"
                ReplyStyle.HUMOROUS -> "「$echo」这题我记下了，不跑丢。"
                else -> "嗯，关于「$echo」，我知道了。"
            }
        }
        val lastSelf = ctx.lastOrNull { it.isSelf }?.content.orEmpty()
        if (lastSelf.contains(Regex("哪会儿|什么时候|方便吗")) &&
            msg.content.contains(Regex("明天|后天|今晚|周末"))
        ) {
            reply = when (style) {
                ReplyStyle.CONCISE -> "好，就按你说的。"
                ReplyStyle.FORMAL -> "好的，按这个时间安排。"
                else -> "行，那就按你说的来～"
            }
        }
        if (style == ReplyStyle.WARM && !reply.contains(Regex("[～呀哈]"))) reply += "～"
        return reply.take(maxLen.coerceAtLeast(8))
    }

    private fun seedFrom(text: String, temperature: Float): Int {
        val bucket = if (temperature > 0.8f) 1000L else 15000L
        val base = if (temperature < 0.2f) text else "$text|${System.currentTimeMillis() / bucket}"
        return base.fold(5381) { h, c -> ((h shl 5) + h) xor c.code }
    }
}

class OllamaAiProvider(
    private val client: OkHttpClient = defaultClient()
) : AiProvider {
    override suspend fun generate(
        message: IncomingMessage,
        context: List<IncomingMessage>,
        settings: AppSettings
    ): AiGenerateResult = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("model", settings.ollamaModel)
            .put("stream", false)
            .put(
                "options",
                JSONObject()
                    .put("temperature", settings.temperature)
                    .put("num_predict", settings.maxReplyLength.coerceAtLeast(32) * 2)
            )
            .put("messages", buildMessages(message, context, settings))
            .toString()
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url(settings.ollamaBaseUrl.trimEnd('/') + "/api/chat")
            .post(body)
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Ollama HTTP ${resp.code}")
            val json = JSONObject(resp.body?.string().orEmpty())
            val text = json.optJSONObject("message")?.optString("content")
                ?: json.optString("response")
            require(text.isNotBlank()) { "Ollama 返回空内容" }
            AiGenerateResult("ollama", settings.ollamaModel, sanitizeReply(text, settings.maxReplyLength))
        }
    }
}

/**
 * OpenAI Chat Completions 兼容调用。
 * DeepSeek / 硅基流动 / OpenRouter / 多数 Gemini OpenAI 兼容层均可复用。
 */
class OpenAiCompatibleProvider(
    private val client: OkHttpClient = defaultClient()
) : AiProvider {
    override suspend fun generate(
        message: IncomingMessage,
        context: List<IncomingMessage>,
        settings: AppSettings
    ): AiGenerateResult = withContext(Dispatchers.IO) {
        val endpoint = resolveEndpoint(settings)
        val model = resolveModel(settings)
        val apiKey = settings.apiKeyForCurrentProvider()
        require(endpoint.baseUrl.isNotBlank()) { "未配置模型地址" }
        require(apiKey.isNotBlank() || settings.aiProvider == AiProviderType.OPENAI_COMPATIBLE) {
            "请先在设置中填写 ${endpoint.displayName} 的 API Key（免费申请）"
        }

        val body = JSONObject()
            .put("model", model)
            .put("temperature", settings.temperature.toDouble())
            .put("max_tokens", settings.maxReplyLength.coerceIn(32, 512))
            .put("messages", buildMessages(message, context, settings))
            .toString()
            .toRequestBody("application/json".toMediaType())

        val builder = Request.Builder()
            .url(endpoint.baseUrl.trimEnd('/') + "/chat/completions")
            .post(body)
            .header("Content-Type", "application/json")

        if (apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer $apiKey")
            // OpenRouter 建议带这些头，无也不影响多数场景
            if (settings.aiProvider == AiProviderType.OPENROUTER) {
                builder.header("HTTP-Referer", "https://waa.local")
                builder.header("X-Title", "WeChat AI Assistant")
            }
        }

        client.newCall(builder.build()).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val err = runCatching { JSONObject(raw).optJSONObject("error")?.optString("message") }.getOrNull()
                error("${endpoint.displayName} HTTP ${resp.code}" + if (!err.isNullOrBlank()) "：$err" else "")
            }
            val json = JSONObject(raw)
            val text = json.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
            require(text.isNotBlank()) { "${endpoint.displayName} 返回空内容" }
            AiGenerateResult(endpoint.id, model, sanitizeReply(text, settings.maxReplyLength))
        }
    }

    private data class Endpoint(val id: String, val displayName: String, val baseUrl: String)

    private fun resolveEndpoint(settings: AppSettings): Endpoint {
        val preset = FreeModelCatalog.byProviderTypeName(settings.aiProvider.name)
        return when {
            preset != null -> Endpoint(preset.id, preset.displayName, settings.openaiBaseUrl.ifBlank { preset.baseUrl })
            else -> Endpoint("openai-compatible", "自定义 API", settings.openaiBaseUrl)
        }
    }

    private fun resolveModel(settings: AppSettings): String {
        val preset = FreeModelCatalog.byProviderTypeName(settings.aiProvider.name)
        return settings.openaiModel.ifBlank { preset?.model ?: "local-model" }
    }
}

class AiRouter(
    private val offline: OfflineFallbackProvider = OfflineFallbackProvider(),
    private val ollama: OllamaAiProvider = OllamaAiProvider(),
    private val openai: OpenAiCompatibleProvider = OpenAiCompatibleProvider()
) {
    suspend fun generate(
        message: IncomingMessage,
        context: List<IncomingMessage>,
        settings: AppSettings
    ): AiGenerateResult {
        if (settings.aiProvider == AiProviderType.OFFLINE_FALLBACK) {
            return offline.generate(message, context, settings)
        }
        if (settings.aiProvider == AiProviderType.OLLAMA) {
            return retryGenerate { ollama.generate(message, context, settings) }
        }

        // 云端精选 / 自定义
        return try {
            retryGenerate { openai.generate(message, context, settings) }
        } catch (t: Throwable) {
            if (settings.offlineFallbackEnabled) {
                val fallback = offline.generate(message, context, settings)
                fallback.copy(
                    intent = (fallback.intent ?: "") + "|fallback_from=${settings.aiProvider.name}",
                    text = fallback.text
                )
            } else {
                throw t
            }
        }
    }

    private suspend fun retryGenerate(block: suspend () -> AiGenerateResult): AiGenerateResult {
        var last: Throwable? = null
        repeat(2) { attempt ->
            try {
                return block()
            } catch (t: Throwable) {
                last = t
                if (attempt == 0) delay(600)
            }
        }
        throw last ?: IllegalStateException("AI 生成失败")
    }

    fun statusLabel(settings: AppSettings): Pair<String, Boolean> {
        return when (settings.aiProvider) {
            AiProviderType.DEEPSEEK -> {
                val ready = settings.apiKeyForCurrentProvider().isNotBlank()
                "DeepSeek · ${settings.openaiModel}" + if (ready) " · 就绪" else " · 待填 Key" to ready
            }
            AiProviderType.GEMINI -> {
                val ready = settings.apiKeyForCurrentProvider().isNotBlank()
                "Gemini · ${settings.openaiModel}" + if (ready) " · 就绪" else " · 待填 Key" to ready
            }
            AiProviderType.SILICONFLOW -> {
                val ready = settings.apiKeyForCurrentProvider().isNotBlank()
                "硅基流动 · ${settings.openaiModel}" + if (ready) " · 就绪" else " · 待填 Key" to ready
            }
            AiProviderType.OPENROUTER -> {
                val ready = settings.apiKeyForCurrentProvider().isNotBlank()
                "OpenRouter · ${settings.openaiModel}" + if (ready) " · 就绪" else " · 待填 Key" to ready
            }
            AiProviderType.OLLAMA -> "Ollama · ${settings.ollamaModel}" to true
            AiProviderType.OPENAI_COMPATIBLE -> {
                val ready = settings.openaiBaseUrl.isNotBlank()
                "自定义 API · ${settings.openaiModel}" to ready
            }
            AiProviderType.OFFLINE_FALLBACK -> "离线兜底模板 · 可用" to true
        }
    }
}

fun applyFreePreset(settings: AppSettings, provider: AiProviderType): AppSettings {
    val preset = FreeModelCatalog.byProviderTypeName(provider.name) ?: return settings.copy(aiProvider = provider)
    val key = when (provider) {
        AiProviderType.DEEPSEEK -> settings.deepseekApiKey
        AiProviderType.GEMINI -> settings.geminiApiKey
        AiProviderType.SILICONFLOW -> settings.siliconflowApiKey
        AiProviderType.OPENROUTER -> settings.openrouterApiKey
        else -> settings.openaiApiKey
    }
    return settings.copy(
        aiProvider = provider,
        openaiBaseUrl = preset.baseUrl,
        openaiModel = preset.model,
        openaiApiKey = key
    )
}

private fun sanitizeReply(text: String, maxLen: Int): String =
    text.replace(Regex("\\s+"), " ").trim().take(maxLen.coerceAtLeast(8))

private fun buildMessages(
    message: IncomingMessage,
    context: List<IncomingMessage>,
    settings: AppSettings
): JSONArray {
    val styleHint = when (settings.style) {
        ReplyStyle.FORMAL -> "用语正式，少用口语。"
        ReplyStyle.CONCISE -> "能一句说完就一句。"
        ReplyStyle.WARM -> "主动一点，带点温度。"
        ReplyStyle.PROFESSIONAL -> "信息清楚，不闲聊。"
        ReplyStyle.HUMOROUS -> "可以轻微玩笑，但不油腻。"
        ReplyStyle.NATURAL -> "像朋友聊天。"
    }
    val arr = JSONArray()
    arr.put(
        JSONObject()
            .put("role", "system")
            .put(
                "content",
                settings.systemPrompt +
                    " 回复风格：$styleHint 请用中文，尽量不超过${settings.maxReplyLength}字，不要使用 Markdown，不要解释你是模型。"
            )
    )
    context.forEach { m ->
        arr.put(
            JSONObject()
                .put("role", if (m.isSelf) "assistant" else "user")
                .put("content", (if (m.senderName.isNotBlank()) "${m.senderName}：" else "") + m.content)
        )
    }
    arr.put(
        JSONObject()
            .put("role", "user")
            .put("content", (if (message.senderName.isNotBlank()) "${message.senderName}：" else "") + message.content)
    )
    return arr
}

private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(45, TimeUnit.SECONDS)
    .writeTimeout(45, TimeUnit.SECONDS)
    .build()
