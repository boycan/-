package com.waa.assistant.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.waa.assistant.data.model.RuntimeStatus
import com.waa.assistant.ui.AppViewModel

@Composable
fun HomeScreen(vm: AppViewModel) {
    val dash by vm.dashboard.collectAsState()
    val settings by vm.settings.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("微信 AI 智能回复助手", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("优先使用系统授权能力，不破解微信", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val color = when (dash.runtimeStatus) {
                        RuntimeStatus.RUNNING -> Color(0xFF16A34A)
                        RuntimeStatus.PAUSED -> Color(0xFFD97706)
                        RuntimeStatus.STOPPED -> Color(0xFF94A3B8)
                    }
                    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        when (dash.runtimeStatus) {
                            RuntimeStatus.RUNNING -> "运行中"
                            RuntimeStatus.PAUSED -> "已暂停"
                            RuntimeStatus.STOPPED -> "已停止"
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text("AI 模型：${dash.modelLabel}")
                Text("默认云端模型：DeepSeek（免费额度）", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("回复模式：${settings.replyMode.name} · 接入：${settings.adapterType}")
                if (!dash.modelReady && settings.aiProvider.name !in setOf("OLLAMA", "OFFLINE_FALLBACK")) {
                    Text(
                        "请到「设置」填写 DeepSeek API Key 后即可生成高质量回复。",
                        color = Color(0xFF9A3412)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.start() }) { Text("启动") }
                    OutlinedButton(onClick = {
                        if (dash.runtimeStatus == RuntimeStatus.PAUSED) vm.resume() else vm.pause()
                    }) {
                        Text(if (dash.runtimeStatus == RuntimeStatus.PAUSED) "恢复" else "暂停")
                    }
                    OutlinedButton(onClick = { vm.stop() }) { Text("停止") }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("今日消息", "${dash.todayReceived}", Modifier.weight(1f))
            StatCard("AI 回复", "${dash.todayAiReplies}", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("自动发送", "${dash.todayAutoSent}", Modifier.weight(1f))
            StatCard("会话数", "${dash.activeConversations}", Modifier.weight(1f))
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("快速验证（模拟消息）", fontWeight = FontWeight.SemiBold)
                Text("不接触微信，走完整：检测 → 规则 → AI → 审核/发送", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { vm.injectDemo("张三", "你好，在吗？") }) { Text("模拟：你好，在吗？") }
                TextButton(onClick = { vm.injectDemo("李四", "明天会议几点？") }) { Text("模拟：明天会议几点？") }
                TextButton(onClick = { vm.injectDemo("项目沟通群", "报价大概多少钱？", isGroup = true) }) {
                    Text("模拟群聊：报价")
                }
            }
        }

        if (dash.pendingReview > 0) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED))) {
                Text(
                    "有 ${dash.pendingReview} 条回复待审核，请到「审核」页处理。",
                    Modifier.padding(16.dp),
                    color = Color(0xFF9A3412)
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}
