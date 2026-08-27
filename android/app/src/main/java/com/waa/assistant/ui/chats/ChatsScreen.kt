package com.waa.assistant.ui.chats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.waa.assistant.ui.AppViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatsScreen(vm: AppViewModel) {
    val chats by vm.conversations.collectAsState()
    var selected by remember { mutableStateOf<String?>(null) }
    val jobs by vm.recentJobs.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("会话", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            TextButton(onClick = { vm.clearAllContext() }) { Text("清空全部上下文") }
        }
        if (chats.isEmpty()) {
            Text("暂无会话。可在首页发送模拟消息。", Modifier.padding(top = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(chats, key = { it.id }) { c ->
                Card(Modifier.fillMaxWidth().clickable { selected = if (selected == c.id) null else c.id }) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(c.name, fontWeight = FontWeight.SemiBold)
                            Text(formatTime(c.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(c.lastMessage, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (c.lastAiReply.isNotBlank()) {
                            Text("AI：${c.lastAiReply}", maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.primary)
                        }
                        if (selected == c.id) {
                            Text("类型：${c.type} · 消息 ${c.messageCount} · 回复 ${c.replyCount}", style = MaterialTheme.typography.bodySmall)
                            val related = jobs.filter { it.conversationId == c.id }.take(5)
                            related.forEach { j ->
                                Text("· [${j.status}] ${j.generatedText.ifBlank { j.incomingText }.take(40)}", style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { vm.clearContext(c.id) }) { Text("清除此会话上下文") }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ts: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
