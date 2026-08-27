package com.waa.assistant.domain.adapter

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay

/**
 * 用户主动授权的辅助功能服务。
 * 仅用于执行用户确认后的「填入并发送」辅助操作，不破解微信、不读取无关应用。
 *
 * 重要限制：
 * - 微信界面层级会随版本变化，自动发送可能失败
 * - 默认产品模式应优先「辅助回复（人工确认）」
 * - 若无法稳定发送，引擎会把任务留在审核态
 */
class WeChatAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    /**
     * 尽力在当前微信聊天页输入并点击发送。
     * 需要用户先打开对应会话；本方法不做会话导航破解。
     */
    suspend fun trySend(conversationName: String, text: String): Result<Unit> {
        return runCatching {
            // 审核页点击“发送”后，助手会成为前台窗口。重新拉起微信，
            // 通常会恢复到用户之前打开的会话，再由无障碍服务操作输入框。
            var root = rootInActiveWindow
            if (root?.packageName?.toString() != WeChatNotificationListener.WECHAT_PACKAGE) {
                bringWeChatToFront()
                root = waitForWeChatRoot()
            }
            if (root == null) {
                error("无法切换到微信，请先返回微信并进入「$conversationName」会话后重试")
            }
            val wechatRoot = root
            if (wechatRoot.packageName?.toString() != WeChatNotificationListener.WECHAT_PACKAGE) {
                error("当前不是微信窗口，请先打开微信并进入「$conversationName」会话后重试")
            }
            val edit = findEditable(wechatRoot) ?: error("未找到微信输入框，请确认已进入「$conversationName」聊天窗口")
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            if (!edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                    error("写入微信输入框失败")
            }
            delay(250)
            val send = findSendButton(waitForWeChatRoot() ?: wechatRoot)
            if (send != null) {
                if (!send.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    error("点击发送失败")
                }
            } else {
                // 兜底：尝试回车手势不一定可靠，直接失败提示人工
                error("未找到发送按钮，请手动点击发送")
            }
            Unit
        }
    }

    private suspend fun bringWeChatToFront() {
        val launchIntent = packageManager
            .getLaunchIntentForPackage(WeChatNotificationListener.WECHAT_PACKAGE)
            ?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            ?: error("未找到微信应用")
        startActivity(launchIntent)
        delay(700)
    }

    private suspend fun waitForWeChatRoot(): AccessibilityNodeInfo? {
        repeat(8) {
            val root = rootInActiveWindow
            if (root?.packageName?.toString() == WeChatNotificationListener.WECHAT_PACKAGE) {
                return root
            }
            delay(250)
        }
        return null
    }

    private fun findEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditable(child)
            if (found != null) return found
        }
        return null
    }

    private fun findSendButton(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val text = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        if ((text == "发送" || desc == "发送") && (node.isClickable || node.isEnabled)) {
            return node
        }
        for (i in 0 until node.childCount) {
            val found = findSendButton(node.getChild(i))
            if (found != null) return found
        }
        // 常见：发送按钮可能是 ImageButton 无文本，尝试右下角可点击节点
        return findLikelySendByPosition(node)
    }

    private fun findLikelySendByPosition(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        fun walk(n: AccessibilityNodeInfo) {
            if (n.isClickable) {
                val rect = android.graphics.Rect()
                n.getBoundsInScreen(rect)
                if (rect.width() in 80..400 && rect.height() in 60..240) {
                    candidates += n
                }
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it) }
        }
        walk(root)
        // 取最靠右下的可点击小按钮作为候选（启发式，可能失败）
        return candidates.maxByOrNull {
            val r = android.graphics.Rect()
            it.getBoundsInScreen(r)
            r.right + r.bottom
        }
    }

    companion object {
        @Volatile var instance: WeChatAccessibilityService? = null
    }
}

class AccessibilitySendAdapter : WeChatAdapter {
    override val id: String = "accessibility_send"

    override suspend fun start(onMessage: suspend (com.waa.assistant.data.model.IncomingMessage) -> Unit) {
        // 发送专用适配器不负责收消息
    }

    override suspend fun stop() = Unit

    override suspend fun send(conversationId: String, conversationName: String, text: String): Result<Unit> {
        val svc = WeChatAccessibilityService.instance
            ?: return Result.failure(IllegalStateException("辅助功能未开启"))
        return svc.trySend(conversationName, text)
    }

    override suspend fun health(): Pair<Boolean, String> {
        val ok = WeChatAccessibilityService.instance != null
        return ok to if (ok) "辅助功能已连接" else "辅助功能未开启"
    }
}
