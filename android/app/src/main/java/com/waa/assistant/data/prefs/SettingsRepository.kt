package com.waa.assistant.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.waa.assistant.data.model.AiProviderType
import com.waa.assistant.data.model.ReplyMode
import com.waa.assistant.data.model.ReplyStyle
import com.waa.assistant.data.model.RuntimeStatus
import com.waa.assistant.domain.ai.FreeModelCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("waa_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val runtimeStatus = stringPreferencesKey("runtime_status")
        val lastStartedAt = longPreferencesKey("last_started_at")
        val lastStoppedAt = longPreferencesKey("last_stopped_at")
        val lastError = stringPreferencesKey("last_error")

        val aiProvider = stringPreferencesKey("ai_provider")
        val style = stringPreferencesKey("style")
        val maxReplyLength = intPreferencesKey("max_reply_length")
        val contextSize = intPreferencesKey("context_size")
        val temperature = floatPreferencesKey("temperature")
        val systemPrompt = stringPreferencesKey("system_prompt")

        val ollamaBaseUrl = stringPreferencesKey("ollama_base_url")
        val ollamaModel = stringPreferencesKey("ollama_model")
        val openaiBaseUrl = stringPreferencesKey("openai_base_url")
        val openaiApiKey = stringPreferencesKey("openai_api_key")
        val openaiModel = stringPreferencesKey("openai_model")

        val deepseekApiKey = stringPreferencesKey("deepseek_api_key")
        val geminiApiKey = stringPreferencesKey("gemini_api_key")
        val siliconflowApiKey = stringPreferencesKey("siliconflow_api_key")
        val openrouterApiKey = stringPreferencesKey("openrouter_api_key")
        val offlineFallbackEnabled = booleanPreferencesKey("offline_fallback_enabled")

        val replyMode = stringPreferencesKey("reply_mode")
        val autoReplyEnabled = booleanPreferencesKey("auto_reply_enabled")
        val cooldownSeconds = intPreferencesKey("cooldown_seconds")
        val maxPerMinute = intPreferencesKey("max_per_minute")
        val maxPerDay = intPreferencesKey("max_per_day")
        val mergeWindowSeconds = intPreferencesKey("merge_window_seconds")
        val workHoursEnabled = booleanPreferencesKey("work_hours_enabled")
        val workStart = stringPreferencesKey("work_start")
        val workEnd = stringPreferencesKey("work_end")
        val workHoursPolicy = stringPreferencesKey("work_hours_policy")
        val offHoursPolicy = stringPreferencesKey("off_hours_policy")
        val globalEnabled = booleanPreferencesKey("global_enabled")
        val useContext = booleanPreferencesKey("use_context")
        val autoPauseOnError = booleanPreferencesKey("auto_pause_on_error")
        val errorPauseThreshold = intPreferencesKey("error_pause_threshold")

        val blacklist = stringPreferencesKey("blacklist")
        val whitelist = stringPreferencesKey("whitelist")
        val whitelistOnly = booleanPreferencesKey("whitelist_only")
        val contactAllow = stringPreferencesKey("contact_allow")
        val groupAllow = stringPreferencesKey("group_allow")
        val keywords = stringPreferencesKey("keywords")

        val adapterType = stringPreferencesKey("adapter_type")
        val permissionIntroAccepted = booleanPreferencesKey("permission_intro_accepted")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun get(): AppSettings = settingsFlow.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        val current = get()
        val next = transform(current)
        context.dataStore.edit { prefs ->
            prefs[Keys.runtimeStatus] = next.runtimeStatus.name
            next.lastStartedAt?.let { prefs[Keys.lastStartedAt] = it } ?: prefs.remove(Keys.lastStartedAt)
            next.lastStoppedAt?.let { prefs[Keys.lastStoppedAt] = it } ?: prefs.remove(Keys.lastStoppedAt)
            next.lastError?.let { prefs[Keys.lastError] = it } ?: prefs.remove(Keys.lastError)

            prefs[Keys.aiProvider] = next.aiProvider.name
            prefs[Keys.style] = next.style.name
            prefs[Keys.maxReplyLength] = next.maxReplyLength
            prefs[Keys.contextSize] = next.contextSize
            prefs[Keys.temperature] = next.temperature
            prefs[Keys.systemPrompt] = next.systemPrompt

            prefs[Keys.ollamaBaseUrl] = next.ollamaBaseUrl
            prefs[Keys.ollamaModel] = next.ollamaModel
            prefs[Keys.openaiBaseUrl] = next.openaiBaseUrl
            prefs[Keys.openaiApiKey] = next.openaiApiKey
            prefs[Keys.openaiModel] = next.openaiModel

            prefs[Keys.deepseekApiKey] = next.deepseekApiKey
            prefs[Keys.geminiApiKey] = next.geminiApiKey
            prefs[Keys.siliconflowApiKey] = next.siliconflowApiKey
            prefs[Keys.openrouterApiKey] = next.openrouterApiKey
            prefs[Keys.offlineFallbackEnabled] = next.offlineFallbackEnabled

            prefs[Keys.replyMode] = next.replyMode.name
            prefs[Keys.autoReplyEnabled] = next.autoReplyEnabled
            prefs[Keys.cooldownSeconds] = next.cooldownSeconds
            prefs[Keys.maxPerMinute] = next.maxPerMinute
            prefs[Keys.maxPerDay] = next.maxPerDay
            prefs[Keys.mergeWindowSeconds] = next.mergeWindowSeconds
            prefs[Keys.workHoursEnabled] = next.workHoursEnabled
            prefs[Keys.workStart] = next.workStart
            prefs[Keys.workEnd] = next.workEnd
            prefs[Keys.workHoursPolicy] = next.workHoursPolicy
            prefs[Keys.offHoursPolicy] = next.offHoursPolicy
            prefs[Keys.globalEnabled] = next.globalEnabled
            prefs[Keys.useContext] = next.useContext
            prefs[Keys.autoPauseOnError] = next.autoPauseOnError
            prefs[Keys.errorPauseThreshold] = next.errorPauseThreshold

            prefs[Keys.blacklist] = next.blacklist.joinToString("\n")
            prefs[Keys.whitelist] = next.whitelist.joinToString("\n")
            prefs[Keys.whitelistOnly] = next.whitelistOnly
            prefs[Keys.contactAllow] = next.contactAllow.joinToString("\n")
            prefs[Keys.groupAllow] = next.groupAllow.joinToString("\n")
            prefs[Keys.keywords] = next.keywords.joinToString("\n") { "${it.keyword}|${it.enabled}" }

            prefs[Keys.adapterType] = next.adapterType
            prefs[Keys.permissionIntroAccepted] = next.permissionIntroAccepted
        }
    }

    private fun Preferences.toSettings(): AppSettings {
        fun listOf(key: Preferences.Key<String>) =
            this[key]?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

        val keywordRaw = this[Keys.keywords]
        val keywords = if (keywordRaw.isNullOrBlank()) {
            emptyList()
        } else {
            keywordRaw.lines().mapNotNull { line ->
                val parts = line.split("|")
                val kw = parts.getOrNull(0)?.trim().orEmpty()
                if (kw.isEmpty()) null
                else KeywordRule(kw, parts.getOrNull(1)?.toBooleanStrictOrNull() ?: true)
            }
        }

        val rawProvider = this[Keys.aiProvider]
        // 旧版 builtin / 空配置 → 统一默认 DeepSeek
        val provider = when (rawProvider) {
            null, "", "BUILTIN" -> AiProviderType.DEEPSEEK
            else -> rawProvider.let { runCatching { AiProviderType.valueOf(it) }.getOrNull() }
                ?: AiProviderType.DEEPSEEK
        }

        val defaultBase = FreeModelCatalog.DEEPSEEK.baseUrl
        val defaultModel = FreeModelCatalog.DEEPSEEK.model
        val storedBase = this[Keys.openaiBaseUrl]
        val storedModel = this[Keys.openaiModel]
        // 若仍是旧本地占位地址，纠正为 DeepSeek
        val openaiBaseUrl = when {
            storedBase.isNullOrBlank() -> defaultBase
            storedBase.contains("127.0.0.1") || storedBase.contains("localhost") ||
                storedBase.startsWith("local://") -> defaultBase
            else -> storedBase
        }
        val openaiModel = when {
            storedModel.isNullOrBlank() || storedModel == "local-model" ||
                storedModel.startsWith("builtin") -> defaultModel
            else -> storedModel
        }

        return AppSettings(
            runtimeStatus = this[Keys.runtimeStatus]?.let { runCatching { RuntimeStatus.valueOf(it) }.getOrNull() }
                ?: RuntimeStatus.STOPPED,
            lastStartedAt = this[Keys.lastStartedAt],
            lastStoppedAt = this[Keys.lastStoppedAt],
            lastError = this[Keys.lastError],
            aiProvider = provider,
            style = this[Keys.style]?.let { runCatching { ReplyStyle.valueOf(it) }.getOrNull() }
                ?: ReplyStyle.NATURAL,
            maxReplyLength = this[Keys.maxReplyLength] ?: 80,
            contextSize = this[Keys.contextSize] ?: 8,
            temperature = this[Keys.temperature] ?: 0.7f,
            systemPrompt = this[Keys.systemPrompt] ?: AppSettings.DEFAULT_SYSTEM_PROMPT,
            ollamaBaseUrl = this[Keys.ollamaBaseUrl] ?: "http://127.0.0.1:11434",
            ollamaModel = this[Keys.ollamaModel] ?: "qwen2.5:7b",
            openaiBaseUrl = openaiBaseUrl,
            openaiApiKey = this[Keys.openaiApiKey] ?: "",
            openaiModel = openaiModel,
            deepseekApiKey = this[Keys.deepseekApiKey] ?: "",
            geminiApiKey = this[Keys.geminiApiKey] ?: "",
            siliconflowApiKey = this[Keys.siliconflowApiKey] ?: "",
            openrouterApiKey = this[Keys.openrouterApiKey] ?: "",
            offlineFallbackEnabled = this[Keys.offlineFallbackEnabled] ?: true,
            replyMode = this[Keys.replyMode]?.let { runCatching { ReplyMode.valueOf(it) }.getOrNull() }
                ?: ReplyMode.ASSIST,
            autoReplyEnabled = this[Keys.autoReplyEnabled] ?: true,
            cooldownSeconds = this[Keys.cooldownSeconds] ?: 20,
            maxPerMinute = this[Keys.maxPerMinute] ?: 8,
            maxPerDay = this[Keys.maxPerDay] ?: 200,
            mergeWindowSeconds = this[Keys.mergeWindowSeconds] ?: 8,
            workHoursEnabled = this[Keys.workHoursEnabled] ?: false,
            workStart = this[Keys.workStart] ?: "09:00",
            workEnd = this[Keys.workEnd] ?: "18:00",
            workHoursPolicy = this[Keys.workHoursPolicy] ?: "work",
            offHoursPolicy = this[Keys.offHoursPolicy] ?: "off",
            globalEnabled = this[Keys.globalEnabled] ?: true,
            useContext = this[Keys.useContext] ?: true,
            autoPauseOnError = this[Keys.autoPauseOnError] ?: true,
            errorPauseThreshold = this[Keys.errorPauseThreshold] ?: 3,
            blacklist = listOf(Keys.blacklist),
            whitelist = listOf(Keys.whitelist),
            whitelistOnly = this[Keys.whitelistOnly] ?: false,
            contactAllow = listOf(Keys.contactAllow),
            groupAllow = listOf(Keys.groupAllow),
            keywords = keywords,
            adapterType = this[Keys.adapterType] ?: "simulator",
            permissionIntroAccepted = this[Keys.permissionIntroAccepted] ?: false
        )
    }
}
