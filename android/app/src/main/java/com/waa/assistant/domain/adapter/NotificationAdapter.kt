package com.waa.assistant.domain.adapter

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.waa.assistant.data.model.ConversationType
import com.waa.assistant.data.model.IncomingMessage
import com.waa.assistant.util.AppIds
import com.waa.assistant.util.Fingerprint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/**
 * 通过系统通知监听读取微信新消息（需用户主动授权）。
 * 不读取其他应用；不破解微信。
 *
 * 注意：通知内容受系统与微信通知样式限制，群聊发送人解析可能不完整。
 * 自动发送不在此实现，发送走 Accessibility / 人工确认。
 */
class WeChatNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        listenerConnected.set(true)
    }

    override fun onListenerDisconnected() {
        listenerConnected.set(false)
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (sbn.packageName != WECHAT_PACKAGE) return
        if (sbn.isOngoing) return
        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = sequenceOf(
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
            // 部分 Android/微信版本把消息放在通知的多行字段中
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                ?.joinToString("\n") { it.toString() },
            // 部分版本使用 MessagingStyle
            extras.getParcelableArray(Notification.EXTRA_MESSAGES)
                ?.mapNotNull { item ->
                    // MessagingStyle 的 Bundle 文本键在 Android SDK 中没有公开便捷解析方法，
                    // 直接读取标准 text 字段，避免依赖不存在的 getMessageFromBundle。
                    (item as? android.os.Bundle)
                        ?.getCharSequence("text")
                        ?.toString()
                }?.lastOrNull()
        ).firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()

        if (title.isBlank() || text.isBlank()) return
        // 过滤微信内部提示类通知
        if (title.contains("微信") && text.contains("登录")) return

        val isGroup = title.contains("]") || title.contains("群") ||
            text.contains(":") || text.contains("：")
        val senderName: String
        val content: String
        val separatorIndex = listOf(text.indexOf(':'), text.indexOf('：'))
            .filter { it >= 0 }
            .minOrNull() ?: -1
        if (isGroup && separatorIndex >= 0) {
            val idx = separatorIndex
            senderName = text.substring(0, idx).trim().ifBlank { title }
            content = text.substring(idx + 1).trim()
        } else {
            senderName = title
            content = text
        }
        if (content.isBlank()) return

        val conversationId = "wx_" + title.hashCode().toUInt().toString(16)
        val msg = IncomingMessage(
            id = AppIds.newId("msg"),
            conversationId = conversationId,
            // 一对一通知标题通常就是联系人；群聊标题是群名
            conversationName = title.ifBlank { senderName },
            conversationType = if (isGroup) ConversationType.GROUP else ConversationType.CONTACT,
            senderId = senderName.hashCode().toUInt().toString(16),
            senderName = senderName,
            content = content,
            timestamp = sbn.postTime.takeIf { it > 0 } ?: System.currentTimeMillis(),
            isSelf = false
        ).let { it.copy(fingerprint = Fingerprint.of(it)) }

        sink.get()?.invoke(msg)
    }

    companion object {
        const val WECHAT_PACKAGE = "com.tencent.mm"
        private val sink = AtomicReference<((IncomingMessage) -> Unit)?>(null)
        private val listenerConnected = java.util.concurrent.atomic.AtomicBoolean(false)

        fun setSink(handler: ((IncomingMessage) -> Unit)?) {
            sink.set(handler)
        }

        fun isEnabled(context: Context): Boolean {
            val cn = ComponentName(context, WeChatNotificationListener::class.java)
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            return flat.split(":").any {
                ComponentName.unflattenFromString(it)?.equals(cn) == true
            }
        }

        fun isConnected(): Boolean = listenerConnected.get()
    }
}

/**
 * 通知接入 Adapter：把 NotificationListener 收到的消息喂给引擎。
 * send() 默认不自动发送，返回失败并提示走辅助回复/无障碍发送。
 */
class NotificationAdapter(
    private val appContext: Context
) : WeChatAdapter {
    override val id: String = "notification"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var handler: (suspend (IncomingMessage) -> Unit)? = null

    override suspend fun start(onMessage: suspend (IncomingMessage) -> Unit) {
        handler = onMessage
        WeChatNotificationListener.setSink { msg ->
            scope.launch { handler?.invoke(msg) }
        }
    }

    override suspend fun stop() {
        WeChatNotificationListener.setSink(null)
        handler = null
    }

    override suspend fun send(conversationId: String, conversationName: String, text: String): Result<Unit> {
        // 通知监听无法可靠发送；交由 AccessibilitySendAdapter / 人工确认
        val accessibility = WeChatAccessibilityService.instance
        return if (accessibility != null) {
            accessibility.fillInput(conversationName, text)
        } else {
            Result.failure(
                IllegalStateException("未开启辅助功能，无法自动发送。请使用「辅助回复」手动确认，或授权辅助功能。")
            )
        }
    }

    override suspend fun health(): Pair<Boolean, String> {
        val ok = WeChatNotificationListener.isEnabled(appContext)
        return ok to if (ok) "通知监听已授权" else "未授权通知读取权限"
    }
}
