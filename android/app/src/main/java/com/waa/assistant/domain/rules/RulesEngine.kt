package com.waa.assistant.domain.rules

import com.waa.assistant.data.model.ConversationType
import com.waa.assistant.data.model.IncomingMessage
import com.waa.assistant.data.model.ReplyMode
import com.waa.assistant.data.prefs.AppSettings
import java.util.Calendar
import java.util.Locale

data class RuleDecision(
    val allow: Boolean,
    val reason: String
)

class RulesEngine {
    fun evaluate(message: IncomingMessage, settings: AppSettings): RuleDecision {
        if (settings.replyMode == ReplyMode.OFF) {
            return RuleDecision(false, "回复模式为关闭")
        }
        if (!settings.autoReplyEnabled) {
            return RuleDecision(false, "自动回复总开关关闭")
        }
        if (message.isSelf) {
            return RuleDecision(false, "跳过自己发出的消息")
        }
        if (message.content.isBlank()) {
            return RuleDecision(false, "空消息")
        }

        val name = message.conversationName.trim()
        val sender = message.senderName.trim()
        val targets = listOf(name, sender).filter { it.isNotEmpty() }

        if (settings.blacklist.any { bl -> targets.any { it.contains(bl, ignoreCase = true) } }) {
            return RuleDecision(false, "命中黑名单")
        }
        if (settings.whitelistOnly) {
            val hit = settings.whitelist.any { wl -> targets.any { it.contains(wl, ignoreCase = true) } }
            if (!hit) return RuleDecision(false, "白名单模式未命中")
        }

        if (!settings.globalEnabled) {
            val contactOk = message.conversationType == ConversationType.CONTACT &&
                settings.contactAllow.any { name.contains(it, ignoreCase = true) || sender.contains(it, ignoreCase = true) }
            val groupOk = message.conversationType == ConversationType.GROUP &&
                settings.groupAllow.any { name.contains(it, ignoreCase = true) }
            if (!contactOk && !groupOk) {
                return RuleDecision(false, "未命中指定联系人/群聊")
            }
        } else {
            // 全局开启时，若配置了指定名单，仍可额外放行；未配置则全量
            val hasContactFilter = settings.contactAllow.isNotEmpty()
            val hasGroupFilter = settings.groupAllow.isNotEmpty()
            if (hasContactFilter || hasGroupFilter) {
                val contactOk = message.conversationType == ConversationType.CONTACT &&
                    (!hasContactFilter || settings.contactAllow.any {
                        name.contains(it, ignoreCase = true) || sender.contains(it, ignoreCase = true)
                    })
                val groupOk = message.conversationType == ConversationType.GROUP &&
                    (!hasGroupFilter || settings.groupAllow.any { name.contains(it, ignoreCase = true) })
                // 若用户配置了名单但当前会话不匹配，仍允许全局；这里保持宽松：全局优先
            }
        }

        val enabledKeywords = settings.keywords.filter { it.enabled }.map { it.keyword }
        if (enabledKeywords.isNotEmpty()) {
            val hit = enabledKeywords.any { message.content.contains(it, ignoreCase = true) }
            if (!hit) return RuleDecision(false, "未命中关键词")
        }

        if (settings.workHoursEnabled) {
            val inWork = inWorkHours(settings.workStart, settings.workEnd)
            val policy = if (inWork) settings.workHoursPolicy else settings.offHoursPolicy
            if (policy == "off") {
                return RuleDecision(false, if (inWork) "工作时间策略为关闭" else "非工作时间策略为关闭")
            }
        }

        return RuleDecision(true, "通过规则")
    }

    private fun inWorkHours(start: String, end: String): Boolean {
        val now = Calendar.getInstance()
        val cur = String.format(Locale.US, "%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
        return if (start <= end) cur in start..end else cur >= start || cur <= end
    }
}
