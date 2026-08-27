package com.waa.assistant.ui.logs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.waa.assistant.ui.AppViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogsScreen(vm: AppViewModel) {
    val logs by vm.logs.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("运行日志", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("消息检测 / AI 处理 / 发送结果 / 错误", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(
            Modifier.padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(logs, key = { it.id }) { log ->
                val color = when (log.level) {
                    "error" -> MaterialTheme.colorScheme.error
                    "warn" -> Color(0xFFD97706)
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "${format(log.ts)} · ${log.level.uppercase()} · ${log.category}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(log.message, color = color)
                    }
                }
            }
        }
    }
}

private fun format(ts: Long): String =
    SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(ts))
