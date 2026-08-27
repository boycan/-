package com.waa.assistant.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.waa.assistant.ui.AppViewModel

@Composable
fun ReviewScreen(vm: AppViewModel) {
    val jobs by vm.reviewJobs.collectAsState()
    val drafts = remember { mutableStateMapOf<String, String>() }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("辅助回复 / 人工审核", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("AI 生成后由你确认发送，避免误发。", color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (jobs.isEmpty()) {
            Text("当前没有待审核回复。", Modifier.padding(top = 24.dp))
            return
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(jobs, key = { it.id }) { job ->
                val text = drafts[job.id] ?: job.editedText.ifBlank { job.generatedText }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(job.conversationName, fontWeight = FontWeight.SemiBold)
                        Text("对方：${job.incomingText}")
                        OutlinedTextField(
                            value = text,
                            onValueChange = { drafts[job.id] = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("AI 回复（可编辑）") }
                        )
                        if (job.error.isNotBlank()) {
                            Text("上次发送失败：${job.error}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { vm.approve(job.id, text) }) { Text("发送") }
                            OutlinedButton(onClick = { vm.regenerate(job.id) }) { Text("重新生成") }
                            OutlinedButton(onClick = { vm.ignore(job.id) }) { Text("忽略") }
                        }
                    }
                }
            }
        }
    }
}
