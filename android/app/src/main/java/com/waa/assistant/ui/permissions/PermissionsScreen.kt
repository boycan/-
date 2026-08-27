package com.waa.assistant.ui.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PermissionsScreen(onAccepted: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("权限与合规说明", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("在继续前，请仔细阅读。所有权限均需你主动授权。")

        PermissionCard(
            "通知读取权限",
            "用于检测微信新消息的通知内容（发送人/会话名/文本），默认所有普通文字消息都会进入审核。不会读取你未授权的其他应用数据。"
        )
        PermissionCard(
            "辅助功能权限（可选）",
            "仅用于执行你确认后的辅助发送操作。默认推荐「辅助回复」由你点击发送。不会破解或注入微信。"
        )
        PermissionCard(
            "网络权限",
            "默认使用 DeepSeek 云端模型（免费额度，需自行申请 API Key）。也可切换 Gemini / 硅基流动 / OpenRouter / Ollama。无网时可离线兜底。"
        )
        PermissionCard(
            "默认 AI：DeepSeek",
            "首次使用请到设置页填写 DeepSeek API Key（仅本地保存）。申请地址：platform.deepseek.com"
        )
        PermissionCard(
            "前台服务 / 电池优化",
            "用于锁屏后继续监听与处理。若系统限制后台，请按应用内提示开启相关权限。"
        )

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("禁止事项（本应用不会做）", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                Text("· 破解微信 / 修改微信客户端")
                Text("· 注入恶意代码 / 窃取账号凭证")
                Text("· 绕过微信安全机制")
            }
        }

        Button(onClick = onAccepted, modifier = Modifier.fillMaxWidth()) {
            Text("我已了解并继续")
        }
    }
}

@Composable
private fun PermissionCard(title: String, body: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
