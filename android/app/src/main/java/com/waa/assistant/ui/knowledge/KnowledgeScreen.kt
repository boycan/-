package com.waa.assistant.ui.knowledge

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.waa.assistant.ui.AppViewModel

@Composable
fun KnowledgeScreen(vm: AppViewModel) {
    val entries by vm.knowledge.collectAsState()
    val appContext = androidx.compose.ui.platform.LocalContext.current
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val imported = readText(appContext, uri)
        if (imported.isNotBlank()) {
            vm.importKnowledge(uri.lastPathSegment.orEmpty(), imported)
        }
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("知识库 / 话术库", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "导入 TXT、Markdown 或 JSONL 话术。AI 会先检索相关内容，再结合上下文改写，不会机械复制。",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("话术标题 / 关键词") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            label = { Text("话术内容") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                vm.importKnowledge(title, content, "manual")
                title = ""
                content = ""
            }) { Text("添加话术") }
            OutlinedButton(onClick = {
                picker.launch(arrayOf("text/plain", "text/markdown", "application/json", "application/jsonl"))
            }) { Text("导入文件") }
            OutlinedButton(onClick = { vm.clearKnowledge() }) { Text("清空") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries, key = { it.id }) { entry ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(entry.title.ifBlank { "未命名话术" }, fontWeight = FontWeight.SemiBold)
                        Text(entry.content, maxLines = 5)
                        Text("来源：${entry.source}", style = MaterialTheme.typography.labelSmall)
                        Text(
                            "删除",
                            modifier = Modifier.padding(top = 4.dp),
                            color = MaterialTheme.colorScheme.error
                        )
                        OutlinedButton(onClick = { vm.deleteKnowledge(entry.id) }) { Text("删除此条") }
                    }
                }
            }
        }
    }
}

private fun readText(context: Context, uri: Uri): String =
    runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty() }
        .getOrDefault("")
