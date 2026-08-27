package com.waa.assistant.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.waa.assistant.data.model.AiProviderType
import com.waa.assistant.data.model.ReplyStyle
import com.waa.assistant.domain.adapter.WeChatNotificationListener
import com.waa.assistant.domain.ai.FreeModelCatalog
import com.waa.assistant.domain.ai.applyFreePreset
import com.waa.assistant.ui.AppViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s by vm.settings.collectAsState()
    val context = LocalContext.current
    val currentPreset = FreeModelCatalog.byProviderTypeName(s.aiProvider.name)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("AI 与系统设置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Text("消息接入", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = s.adapterType == "simulator",
                onClick = { vm.changeAdapter("simulator") },
                label = { Text("模拟器") }
            )
            FilterChip(
                selected = s.adapterType == "notification",
                onClick = { vm.changeAdapter("notification") },
                label = { Text("通知监听") }
            )
        }

        Text("免费精选模型（推荐）", fontWeight = FontWeight.SemiBold)
        Text(
            "默认 DeepSeek。源码不含任何 Key，请自行申请免费额度后填入。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                AiProviderType.DEEPSEEK to "DeepSeek（默认）",
                AiProviderType.GEMINI to "Gemini",
                AiProviderType.SILICONFLOW to "硅基流动",
                AiProviderType.OPENROUTER to "OpenRouter"
            ).forEach { (type, label) ->
                FilterChip(
                    selected = s.aiProvider == type,
                    onClick = { vm.updateSettings { applyFreePreset(it, type) } },
                    label = { Text(label) }
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = s.aiProvider == AiProviderType.OLLAMA,
                onClick = { vm.updateSettings { it.copy(aiProvider = AiProviderType.OLLAMA) } },
                label = { Text("Ollama 本地") }
            )
            FilterChip(
                selected = s.aiProvider == AiProviderType.OPENAI_COMPATIBLE,
                onClick = { vm.updateSettings { it.copy(aiProvider = AiProviderType.OPENAI_COMPATIBLE) } },
                label = { Text("自定义 API") }
            )
            FilterChip(
                selected = s.aiProvider == AiProviderType.OFFLINE_FALLBACK,
                onClick = { vm.updateSettings { it.copy(aiProvider = AiProviderType.OFFLINE_FALLBACK) } },
                label = { Text("离线兜底") }
            )
        }

        if (currentPreset != null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(currentPreset.displayName, fontWeight = FontWeight.SemiBold)
                    Text(currentPreset.hint, style = MaterialTheme.typography.bodySmall)
                    Text("模型：${s.openaiModel}", style = MaterialTheme.typography.bodySmall)
                    Text("接口：${s.openaiBaseUrl}", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentPreset.signupUrl)))
                    }) { Text("打开免费申请页") }
                }
            }

            val keyValue = when (s.aiProvider) {
                AiProviderType.DEEPSEEK -> s.deepseekApiKey
                AiProviderType.GEMINI -> s.geminiApiKey
                AiProviderType.SILICONFLOW -> s.siliconflowApiKey
                AiProviderType.OPENROUTER -> s.openrouterApiKey
                else -> s.openaiApiKey
            }
            OutlinedTextField(
                value = keyValue,
                onValueChange = { v ->
                    vm.updateSettings { cur ->
                        when (cur.aiProvider) {
                            AiProviderType.DEEPSEEK -> cur.copy(deepseekApiKey = v, openaiApiKey = v)
                            AiProviderType.GEMINI -> cur.copy(geminiApiKey = v, openaiApiKey = v)
                            AiProviderType.SILICONFLOW -> cur.copy(siliconflowApiKey = v, openaiApiKey = v)
                            AiProviderType.OPENROUTER -> cur.copy(openrouterApiKey = v, openaiApiKey = v)
                            else -> cur.copy(openaiApiKey = v)
                        }
                    }
                },
                label = { Text("${currentPreset.displayName} API Key（仅本地保存）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = s.openaiModel,
                onValueChange = { v -> vm.updateSettings { it.copy(openaiModel = v) } },
                label = { Text("模型名（可改）") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = s.openaiBaseUrl,
                onValueChange = { v -> vm.updateSettings { it.copy(openaiBaseUrl = v) } },
                label = { Text("Base URL（可改）") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (s.aiProvider == AiProviderType.OLLAMA) {
            OutlinedTextField(
                value = s.ollamaBaseUrl,
                onValueChange = { v -> vm.updateSettings { it.copy(ollamaBaseUrl = v) } },
                label = { Text("Ollama Base URL") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = s.ollamaModel,
                onValueChange = { v -> vm.updateSettings { it.copy(ollamaModel = v) } },
                label = { Text("Ollama Model，如 qwen2.5:7b") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (s.aiProvider == AiProviderType.OPENAI_COMPATIBLE) {
            OutlinedTextField(
                value = s.openaiBaseUrl,
                onValueChange = { v -> vm.updateSettings { it.copy(openaiBaseUrl = v) } },
                label = { Text("OpenAI Compatible Base URL") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = s.openaiModel,
                onValueChange = { v -> vm.updateSettings { it.copy(openaiModel = v) } },
                label = { Text("Model") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = s.openaiApiKey,
                onValueChange = { v -> vm.updateSettings { it.copy(openaiApiKey = v) } },
                label = { Text("API Key（可选）") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("云端失败时使用离线兜底")
                Text("无 Key/无网时仍能生成简单回复", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = s.offlineFallbackEnabled,
                onCheckedChange = { checked -> vm.updateSettings { it.copy(offlineFallbackEnabled = checked) } }
            )
        }

        Text("回复风格", fontWeight = FontWeight.SemiBold)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                listOf(ReplyStyle.NATURAL to "自然", ReplyStyle.CONCISE to "简洁", ReplyStyle.FORMAL to "正式"),
                listOf(ReplyStyle.WARM to "热情", ReplyStyle.PROFESSIONAL to "专业", ReplyStyle.HUMOROUS to "幽默")
            ).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (style, label) ->
                        FilterChip(
                            selected = s.style == style,
                            onClick = { vm.updateSettings { it.copy(style = style) } },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }

        Text("Temperature：${"%.2f".format(s.temperature)}")
        Slider(
            value = s.temperature,
            onValueChange = { v -> vm.updateSettings { it.copy(temperature = v) } },
            valueRange = 0f..1.2f
        )

        OutlinedTextField(
            value = s.maxReplyLength.toString(),
            onValueChange = { v -> v.toIntOrNull()?.let { n -> vm.updateSettings { it.copy(maxReplyLength = n) } } },
            label = { Text("回复长度") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = s.contextSize.toString(),
            onValueChange = { v -> v.toIntOrNull()?.let { n -> vm.updateSettings { it.copy(contextSize = n) } } },
            label = { Text("上下文条数") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = s.systemPrompt,
            onValueChange = { v -> vm.updateSettings { it.copy(systemPrompt = v) } },
            label = { Text("自定义系统 Prompt") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        Text("权限与后台", fontWeight = FontWeight.SemiBold)
        val notifOk = WeChatNotificationListener.isEnabled(context)
        val notifConnected = WeChatNotificationListener.isConnected()
        Text(
            "通知监听：${when {
                notifConnected -> "已连接"
                notifOk -> "已授权但未连接，请重启监听"
                else -> "未授权"
            }}"
        )
        Button(onClick = {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }) { Text("打开通知使用权设置") }

        OutlinedButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }) { Text("打开辅助功能设置") }

        OutlinedButton(onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}")
                )
                runCatching { context.startActivity(intent) }
            }
        }) { Text("申请忽略电池优化") }

        OutlinedButton(onClick = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            )
        }) { Text("应用详情 / 后台运行权限") }

        Text(
            "说明：免费模型需自行申请 API Key，Key 仅保存在本机。默认辅助回复，避免误发。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
