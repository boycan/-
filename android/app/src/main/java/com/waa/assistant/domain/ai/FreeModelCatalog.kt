package com.waa.assistant.domain.ai

/**
 * 市面免费/高性价比模型精选。
 * 注意：源码不内置任何 API Key，需用户自行申请免费额度后填入。
 */
data class FreeModelPreset(
    val id: String,
    val displayName: String,
    val providerTypeName: String,
    val baseUrl: String,
    val model: String,
    val signupUrl: String,
    val hint: String,
    val openaiCompatible: Boolean = true
)

object FreeModelCatalog {
    val DEEPSEEK = FreeModelPreset(
        id = "deepseek",
        displayName = "DeepSeek",
        providerTypeName = "DEEPSEEK",
        baseUrl = "https://api.deepseek.com/v1",
        model = "deepseek-chat",
        signupUrl = "https://platform.deepseek.com/",
        hint = "中文很强，有免费额度/高性价比。申请 Key 后即可使用。"
    )

    val GEMINI = FreeModelPreset(
        id = "gemini",
        displayName = "Gemini",
        providerTypeName = "GEMINI",
        // OpenAI 兼容代理形态（官方 Google AI Studio Key 走 generativelanguage 时另有协议；
        // 这里默认走常见兼容网关写法，用户也可改成自己的代理地址）
        baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
        model = "gemini-2.0-flash",
        signupUrl = "https://aistudio.google.com/apikey",
        hint = "Google 免费额度较大。推荐使用 Gemini API Key。"
    )

    val SILICONFLOW = FreeModelPreset(
        id = "siliconflow",
        displayName = "硅基流动",
        providerTypeName = "SILICONFLOW",
        baseUrl = "https://api.siliconflow.cn/v1",
        model = "Qwen/Qwen2.5-7B-Instruct",
        signupUrl = "https://cloud.siliconflow.cn/",
        hint = "国内可达，常有免费/低价开源模型（Qwen、DeepSeek 等）。"
    )

    val OPENROUTER = FreeModelPreset(
        id = "openrouter",
        displayName = "OpenRouter",
        providerTypeName = "OPENROUTER",
        baseUrl = "https://openrouter.ai/api/v1",
        model = "deepseek/deepseek-chat-v3-0324:free",
        signupUrl = "https://openrouter.ai/",
        hint = "聚合多家模型，可选 :free 免费路由。"
    )

    val ALL = listOf(DEEPSEEK, GEMINI, SILICONFLOW, OPENROUTER)

    fun byProviderTypeName(name: String): FreeModelPreset? =
        ALL.firstOrNull { it.providerTypeName == name }

    fun applyTo(
        baseUrlSetter: (String) -> Unit,
        modelSetter: (String) -> Unit,
        preset: FreeModelPreset
    ) {
        baseUrlSetter(preset.baseUrl)
        modelSetter(preset.model)
    }
}
