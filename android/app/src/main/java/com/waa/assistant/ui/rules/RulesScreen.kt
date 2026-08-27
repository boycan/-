package com.waa.assistant.ui.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.waa.assistant.data.model.ReplyMode
import com.waa.assistant.data.prefs.KeywordRule
import com.waa.assistant.ui.AppViewModel

@Composable
fun RulesScreen(vm: AppViewModel) {
    val s by vm.settings.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("自动回复规则", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Text("回复模式", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReplyMode.entries.forEach { mode ->
                FilterChip(
                    selected = s.replyMode == mode,
                    onClick = { vm.setReplyMode(mode) },
                    label = {
                        Text(
                            when (mode) {
                                ReplyMode.AUTO -> "自动回复"
                                ReplyMode.ASSIST -> "辅助回复"
                                ReplyMode.OFF -> "关闭"
                            }
                        )
                    }
                )
            }
        }

        SwitchRow("启用自动回复总开关", s.autoReplyEnabled) {
            vm.updateSettings { it.copy(autoReplyEnabled = it.autoReplyEnabled.not()) }
        }
        SwitchRow("全局自动回复", s.globalEnabled) {
            vm.updateSettings { it.copy(globalEnabled = it.globalEnabled.not()) }
        }
        SwitchRow("仅白名单", s.whitelistOnly) {
            vm.updateSettings { it.copy(whitelistOnly = it.whitelistOnly.not()) }
        }
        SwitchRow("启用工作时间", s.workHoursEnabled) {
            vm.updateSettings { it.copy(workHoursEnabled = it.workHoursEnabled.not()) }
        }

        OutlinedTextField(
            value = s.workStart,
            onValueChange = { v -> vm.updateSettings { it.copy(workStart = v) } },
            label = { Text("工作开始 HH:mm") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = s.workEnd,
            onValueChange = { v -> vm.updateSettings { it.copy(workEnd = v) } },
            label = { Text("工作结束 HH:mm") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = s.cooldownSeconds.toString(),
            onValueChange = { v -> v.toIntOrNull()?.let { n -> vm.updateSettings { it.copy(cooldownSeconds = n) } } },
            label = { Text("回复冷却（秒）") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = s.maxPerMinute.toString(),
            onValueChange = { v -> v.toIntOrNull()?.let { n -> vm.updateSettings { it.copy(maxPerMinute = n) } } },
            label = { Text("每分钟最大回复") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = s.maxPerDay.toString(),
            onValueChange = { v -> v.toIntOrNull()?.let { n -> vm.updateSettings { it.copy(maxPerDay = n) } } },
            label = { Text("每日最大回复") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = s.keywords.joinToString("\n") { it.keyword },
            onValueChange = { v ->
                val list = v.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { KeywordRule(it) }
                vm.updateSettings { it.copy(keywords = list) }
            },
            label = { Text("关键词（可选；留空=监听全部普通文字消息）") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )
        OutlinedTextField(
            value = s.whitelist.joinToString("\n"),
            onValueChange = { v -> vm.updateSettings { it.copy(whitelist = v.lines().map { x -> x.trim() }.filter { x -> x.isNotEmpty() }) } },
            label = { Text("白名单（联系人/群名，每行一个）") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
        OutlinedTextField(
            value = s.blacklist.joinToString("\n"),
            onValueChange = { v -> vm.updateSettings { it.copy(blacklist = v.lines().map { x -> x.trim() }.filter { x -> x.isNotEmpty() }) } },
            label = { Text("黑名单") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
        OutlinedTextField(
            value = s.contactAllow.joinToString("\n"),
            onValueChange = { v -> vm.updateSettings { it.copy(contactAllow = v.lines().map { x -> x.trim() }.filter { x -> x.isNotEmpty() }) } },
            label = { Text("指定联系人（可选）") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
        OutlinedTextField(
            value = s.groupAllow.joinToString("\n"),
            onValueChange = { v -> vm.updateSettings { it.copy(groupAllow = v.lines().map { x -> x.trim() }.filter { x -> x.isNotEmpty() }) } },
            label = { Text("指定群聊（可选）") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}
