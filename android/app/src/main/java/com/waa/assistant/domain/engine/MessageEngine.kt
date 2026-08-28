package com.waa.assistant.domain.engine

import com.waa.assistant.data.db.AppDatabase
import com.waa.assistant.data.model.ConversationEntity
import com.waa.assistant.data.model.IncomingMessage
import com.waa.assistant.data.model.JobStatus
import com.waa.assistant.data.model.LogEntity
import com.waa.assistant.data.model.MessageEntity
import com.waa.assistant.data.model.ReplyJobEntity
import com.waa.assistant.data.model.ReplyMode
import com.waa.assistant.data.model.RuntimeStatus
import com.waa.assistant.data.prefs.AppSettings
import com.waa.assistant.data.prefs.SettingsRepository
import com.waa.assistant.domain.adapter.NotificationAdapter
import com.waa.assistant.domain.adapter.SimulatorAdapter
import com.waa.assistant.domain.adapter.WeChatAdapter
import com.waa.assistant.domain.ai.AiRouter
import com.waa.assistant.domain.context.ContextManager
import com.waa.assistant.domain.rules.RulesEngine
import com.waa.assistant.util.AppIds
import com.waa.assistant.util.DayUtils
import com.waa.assistant.util.Fingerprint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 消息处理引擎：
 * 入站 → 去重 → 规则 → 上下文 → AI → 审核/自动发送 → 日志
 */
class MessageEngine(
    private val db: AppDatabase,
    private val settingsRepo: SettingsRepository,
    private val aiRouter: AiRouter = AiRouter(),
    private val rulesEngine: RulesEngine = RulesEngine()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val queue = Channel<IncomingMessage>(Channel.UNLIMITED)
    private val mutex = Mutex()
    private val cooldownUntil = ConcurrentHashMap<String, Long>()
    private val recentMerge = ConcurrentHashMap<String, Pair<Long, String>>()
    private val consecutiveErrors = AtomicInteger(0)

    private var worker: Job? = null
    private var adapter: WeChatAdapter? = null
    val simulator = SimulatorAdapter()

    private val _status = MutableStateFlow(RuntimeStatus.STOPPED)
    val status: StateFlow<RuntimeStatus> = _status.asStateFlow()

    private val contextManager = ContextManager(db.messages())

    fun start(settings: AppSettings) {
        if (_status.value == RuntimeStatus.RUNNING) return
        _status.value = RuntimeStatus.RUNNING
        adapter = when (settings.adapterType) {
            "notification" -> NotificationAdapter(com.waa.assistant.WaaApp.instance)
            else -> simulator
        }
        worker = scope.launch {
            for (msg in queue) {
                try {
                    process(msg)
                } catch (t: Throwable) {
                    log("error", "engine", "处理异常：${t.message}")
                    onError()
                }
            }
        }
        scope.launch {
            adapter?.start { enqueue(it) }
            settingsRepo.update {
                it.copy(
                    runtimeStatus = RuntimeStatus.RUNNING,
                    lastStartedAt = System.currentTimeMillis(),
                    lastError = null
                )
            }
            log("info", "runtime", "引擎已启动，adapter=${adapter?.id}")
        }
    }

    fun pause() {
        if (_status.value != RuntimeStatus.RUNNING) return
        _status.value = RuntimeStatus.PAUSED
        scope.launch {
            settingsRepo.update { it.copy(runtimeStatus = RuntimeStatus.PAUSED) }
            log("warn", "runtime", "引擎已暂停")
        }
    }

    fun resume() {
        if (_status.value != RuntimeStatus.PAUSED) return
        _status.value = RuntimeStatus.RUNNING
        consecutiveErrors.set(0)
        scope.launch {
            settingsRepo.update { it.copy(runtimeStatus = RuntimeStatus.RUNNING, lastError = null) }
            log("info", "runtime", "引擎已恢复")
        }
    }

    fun stop() {
        _status.value = RuntimeStatus.STOPPED
        worker?.cancel()
        worker = null
        scope.launch {
            adapter?.stop()
            adapter = null
            settingsRepo.update {
                it.copy(runtimeStatus = RuntimeStatus.STOPPED, lastStoppedAt = System.currentTimeMillis())
            }
            log("info", "runtime", "引擎已停止")
        }
    }

    fun enqueue(message: IncomingMessage) {
        if (_status.value == RuntimeStatus.STOPPED) return
        queue.trySend(message)
    }

    suspend fun injectSimulator(name: String, content: String, isGroup: Boolean = false) {
        val cid = (if (isGroup) "g_" else "c_") + name.hashCode().toUInt().toString(16)
        val msg = IncomingMessage(
            id = AppIds.newId("msg"),
            conversationId = cid,
            conversationName = name,
            conversationType = if (isGroup) {
                com.waa.assistant.data.model.ConversationType.GROUP
            } else {
                com.waa.assistant.data.model.ConversationType.CONTACT
            },
            senderId = cid,
            senderName = name,
            content = content,
            timestamp = System.currentTimeMillis(),
            isSelf = false
        ).let { it.copy(fingerprint = Fingerprint.of(it)) }

        // 直接入队，避免依赖 adapter 回调时序
        if (_status.value == RuntimeStatus.STOPPED) {
            // 允许在未启动时仍写入并处理一次（便于演示）
            _status.value = RuntimeStatus.RUNNING
            if (worker == null) {
                worker = scope.launch {
                    for (item in queue) {
                        try {
                            process(item)
                        } catch (t: Throwable) {
                            log("error", "engine", "处理异常：${t.message}")
                            onError()
                        }
                    }
                }
            }
            if (adapter == null) adapter = simulator
        }
        enqueue(msg)
    }

    private suspend fun process(raw: IncomingMessage) = mutex.withLock {
        val settings = settingsRepo.get()
        if (_status.value == RuntimeStatus.PAUSED) {
            log("info", "engine", "暂停中，跳过消息")
            return
        }
        val message = if (raw.fingerprint.isBlank()) raw.copy(fingerprint = Fingerprint.of(raw)) else raw

        log("info", "message", "检测到消息：${message.conversationName} - ${message.content.take(60)}")

        if (db.messages().countByFingerprint(message.fingerprint) > 0) {
            log("info", "guard", "去重命中，跳过")
            return
        }

        // 合并同一会话短时间连续消息
        val mergeKey = message.conversationId
        val prev = recentMerge[mergeKey]
        val now = System.currentTimeMillis()
        val mergedContent = if (prev != null && now - prev.first <= settings.mergeWindowSeconds * 1000L) {
            prev.second + "\n" + message.content
        } else message.content
        recentMerge[mergeKey] = now to mergedContent
        val workMsg = message.copy(content = mergedContent)

        persistIncoming(workMsg)

        val decision = rulesEngine.evaluate(workMsg, settings)
        if (!decision.allow) {
            log("info", "rules", "规则拦截：${decision.reason}")
            return
        }

        // 防护：冷却 / 频率
        val cool = cooldownUntil[workMsg.conversationId] ?: 0L
        if (now < cool) {
            log("warn", "guard", "会话冷却中，跳过")
            return
        }
        val sentToday = db.jobs().countSentToday(DayUtils.startOfToday())
        if (sentToday >= settings.maxPerDay) {
            log("warn", "guard", "达到每日最大回复次数")
            return
        }
        val sentMinute = db.jobs().countSentSince(now - 60_000)
        if (sentMinute >= settings.maxPerMinute) {
            log("warn", "guard", "达到每分钟最大回复次数")
            return
        }

        val job = ReplyJobEntity(
            id = AppIds.newId("job"),
            conversationId = workMsg.conversationId,
            conversationName = workMsg.conversationName,
            messageId = workMsg.id,
            incomingText = workMsg.content,
            status = JobStatus.GENERATING.name,
            createdAt = now,
            updatedAt = now
        )
        db.jobs().upsert(job)

        val ctx = if (settings.useContext) {
            contextManager.load(workMsg.conversationId, settings.contextSize)
                .filter { it.id != workMsg.id }
        } else emptyList()

        val ai = try {
            aiRouter.generate(workMsg, ctx, settings, db.knowledge().all())
        } catch (t: Throwable) {
            db.jobs().upsert(
                job.copy(
                    status = JobStatus.FAILED.name,
                    error = t.message.orEmpty(),
                    updatedAt = System.currentTimeMillis()
                )
            )
            log("error", "ai", "生成失败：${t.message}")
            onError()
            return
        }

        consecutiveErrors.set(0)
        var updated = job.copy(
            generatedText = ai.text,
            editedText = ai.text,
            provider = ai.provider,
            intent = ai.intent.orEmpty(),
            updatedAt = System.currentTimeMillis()
        )

        when (settings.replyMode) {
            ReplyMode.OFF -> {
                db.jobs().upsert(updated.copy(status = JobStatus.SKIPPED.name))
            }
            ReplyMode.ASSIST -> {
                db.jobs().upsert(updated.copy(status = JobStatus.REVIEW.name))
                log("info", "review", "待人工审核：${workMsg.conversationName}")
            }
            ReplyMode.AUTO -> {
                db.jobs().upsert(updated.copy(status = JobStatus.SENDING.name))
                val sendResult = adapter?.send(workMsg.conversationId, workMsg.conversationName, ai.text)
                    ?: Result.failure(IllegalStateException("无可用发送适配器"))
                if (sendResult.isSuccess) {
                    val onlyFilled = adapter?.id == "notification"
                    if (!onlyFilled) {
                        persistSelfReply(workMsg, ai.text)
                        cooldownUntil[workMsg.conversationId] =
                            System.currentTimeMillis() + settings.cooldownSeconds * 1000L
                    }
                    db.jobs().upsert(
                        updated.copy(
                            status = if (onlyFilled) JobStatus.FILLED.name else JobStatus.SENT.name,
                            repliedAt = if (onlyFilled) null else System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    log(
                        "info",
                        "send",
                        if (onlyFilled) "已填入微信输入框，等待用户点击发送：${workMsg.conversationName}"
                        else "自动回复成功：${workMsg.conversationName}"
                    )
                } else {
                    db.jobs().upsert(
                        updated.copy(
                            status = JobStatus.REVIEW.name,
                            error = sendResult.exceptionOrNull()?.message.orEmpty(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    log("warn", "send", "自动发送失败，转入审核：${sendResult.exceptionOrNull()?.message}")
                }
            }
        }
    }

    suspend fun approveJob(jobId: String, text: String? = null) {
        val job = db.jobs().get(jobId) ?: return
        val finalText = (text ?: job.editedText.ifBlank { job.generatedText }).trim()
        if (finalText.isBlank()) return
        db.jobs().upsert(job.copy(status = JobStatus.SENDING.name, editedText = finalText, updatedAt = System.currentTimeMillis()))
        val settings = settingsRepo.get()
        val result = adapter?.send(job.conversationId, job.conversationName, finalText)
            ?: Result.failure(IllegalStateException("引擎未启动或无适配器"))
        if (result.isSuccess) {
            val onlyFilled = adapter?.id == "notification"
            if (!onlyFilled) {
                persistSelfReply(
                    IncomingMessage(
                        id = job.messageId,
                        conversationId = job.conversationId,
                        conversationName = job.conversationName,
                        senderId = "self",
                        senderName = "我",
                        content = job.incomingText,
                        timestamp = job.createdAt
                    ),
                    finalText
                )
                cooldownUntil[job.conversationId] =
                    System.currentTimeMillis() + settings.cooldownSeconds * 1000L
            }
            db.jobs().upsert(
                job.copy(
                    status = if (onlyFilled) JobStatus.FILLED.name else JobStatus.SENT.name,
                    editedText = finalText,
                    repliedAt = if (onlyFilled) null else System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    error = ""
                )
            )
            log(
                "info",
                "send",
                if (adapter?.id == "notification") {
                    "已填入微信输入框，等待用户点击发送：${job.conversationName}"
                } else {
                    "人工确认发送成功：${job.conversationName}"
                }
            )
        } else {
            db.jobs().upsert(
                job.copy(
                    status = JobStatus.REVIEW.name,
                    editedText = finalText,
                    error = result.exceptionOrNull()?.message.orEmpty(),
                    updatedAt = System.currentTimeMillis()
                )
            )
            log("error", "send", "发送失败：${result.exceptionOrNull()?.message}")
        }
    }

    suspend fun ignoreJob(jobId: String) {
        val job = db.jobs().get(jobId) ?: return
        db.jobs().upsert(job.copy(status = JobStatus.IGNORED.name, updatedAt = System.currentTimeMillis()))
        log("info", "review", "已忽略：${job.conversationName}")
    }

    suspend fun regenerateJob(jobId: String) {
        val job = db.jobs().get(jobId) ?: return
        val settings = settingsRepo.get()
        val ctx = contextManager.load(job.conversationId, settings.contextSize)
        val msg = IncomingMessage(
            id = job.messageId,
            conversationId = job.conversationId,
            conversationName = job.conversationName,
            senderId = job.conversationId,
            senderName = job.conversationName,
            content = job.incomingText,
            timestamp = job.createdAt
        )
        db.jobs().upsert(job.copy(status = JobStatus.GENERATING.name, updatedAt = System.currentTimeMillis()))
        try {
            val ai = aiRouter.generate(msg, ctx, settings, db.knowledge().all())
            db.jobs().upsert(
                job.copy(
                    generatedText = ai.text,
                    editedText = ai.text,
                    provider = ai.provider,
                    intent = ai.intent.orEmpty(),
                    status = JobStatus.REVIEW.name,
                    updatedAt = System.currentTimeMillis(),
                    error = ""
                )
            )
        } catch (t: Throwable) {
            db.jobs().upsert(
                job.copy(status = JobStatus.FAILED.name, error = t.message.orEmpty(), updatedAt = System.currentTimeMillis())
            )
        }
    }

    private suspend fun persistIncoming(message: IncomingMessage) {
        db.messages().insert(
            MessageEntity(
                id = message.id,
                conversationId = message.conversationId,
                senderId = message.senderId,
                senderName = message.senderName,
                content = message.content,
                timestamp = message.timestamp,
                isSelf = message.isSelf,
                fingerprint = message.fingerprint
            )
        )
        val old = db.conversations().get(message.conversationId)
        db.conversations().upsert(
            ConversationEntity(
                id = message.conversationId,
                name = message.conversationName,
                type = message.conversationType.name,
                lastMessage = message.content,
                lastAiReply = old?.lastAiReply.orEmpty(),
                updatedAt = System.currentTimeMillis(),
                messageCount = (old?.messageCount ?: 0) + 1,
                replyCount = old?.replyCount ?: 0
            )
        )
    }

    private suspend fun persistSelfReply(source: IncomingMessage, text: String) {
        val self = IncomingMessage(
            id = AppIds.newId("msg"),
            conversationId = source.conversationId,
            conversationName = source.conversationName,
            conversationType = source.conversationType,
            senderId = "self",
            senderName = "我",
            content = text,
            timestamp = System.currentTimeMillis(),
            isSelf = true
        ).let { it.copy(fingerprint = Fingerprint.of(it)) }
        db.messages().insert(
            MessageEntity(
                id = self.id,
                conversationId = self.conversationId,
                senderId = self.senderId,
                senderName = self.senderName,
                content = self.content,
                timestamp = self.timestamp,
                isSelf = true,
                fingerprint = self.fingerprint
            )
        )
        val old = db.conversations().get(source.conversationId)
        if (old != null) {
            db.conversations().upsert(
                old.copy(
                    lastAiReply = text,
                    lastMessage = text,
                    updatedAt = System.currentTimeMillis(),
                    replyCount = old.replyCount + 1
                )
            )
        }
    }

    private suspend fun onError() {
        val settings = settingsRepo.get()
        val n = consecutiveErrors.incrementAndGet()
        if (settings.autoPauseOnError && n >= settings.errorPauseThreshold) {
            pause()
            settingsRepo.update { it.copy(lastError = "连续失败 ${n} 次，已自动暂停") }
            log("error", "runtime", "连续失败达到阈值，自动暂停")
        }
    }

    private suspend fun log(level: String, category: String, message: String) {
        db.logs().insert(
            LogEntity(
                id = AppIds.newId("log"),
                level = level,
                category = category,
                message = message
            )
        )
    }
}
