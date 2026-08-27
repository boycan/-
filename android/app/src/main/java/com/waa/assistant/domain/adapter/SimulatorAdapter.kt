package com.waa.assistant.domain.adapter

import com.waa.assistant.data.model.ConversationType
import com.waa.assistant.data.model.IncomingMessage
import com.waa.assistant.util.AppIds
import com.waa.assistant.util.Fingerprint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 开发/演示用模拟消息源，不接触微信。
 */
class SimulatorAdapter : WeChatAdapter {
    override val id: String = "simulator"

    private val running = AtomicBoolean(false)
    private var scope: CoroutineScope? = null
    private var job: Job? = null
    private var onMessage: (suspend (IncomingMessage) -> Unit)? = null

    private val outbox = MutableSharedFlow<Pair<String, String>>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val sentFlow: SharedFlow<Pair<String, String>> = outbox.asSharedFlow()

    private val demos = listOf(
        Triple("c_zhangsan", "张三", "你好，在吗？"),
        Triple("c_lisi", "李四", "明天会议几点开始？"),
        Triple("g_project", "项目沟通群", "报价大概多少钱？"),
        Triple("c_wangwu", "王五", "地址发我一下"),
        Triple("c_zhangsan", "张三", "好的，那我等你消息")
    )

    override suspend fun start(onMessage: suspend (IncomingMessage) -> Unit) {
        if (!running.compareAndSet(false, true)) return
        this.onMessage = onMessage
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = s
        job = s.launch {
            var i = 0
            while (isActive && running.get()) {
                delay(12_000)
                if (!running.get()) break
                val (cid, name, content) = demos[i % demos.size]
                i++
                val type = if (cid.startsWith("g_")) ConversationType.GROUP else ConversationType.CONTACT
                val msg = IncomingMessage(
                    id = AppIds.newId("msg"),
                    conversationId = cid,
                    conversationName = name,
                    conversationType = type,
                    senderId = cid,
                    senderName = if (type == ConversationType.GROUP) "群友${i % 3 + 1}" else name,
                    content = content,
                    timestamp = System.currentTimeMillis(),
                    isSelf = false
                ).let { it.copy(fingerprint = Fingerprint.of(it)) }
                onMessage(msg)
            }
        }
    }

    /** 手动注入一条模拟消息（UI 模拟器使用） */
    suspend fun inject(
        conversationName: String,
        content: String,
        isGroup: Boolean = false
    ) {
        val handler = onMessage ?: return
        val cid = "c_" + conversationName.hashCode().toUInt().toString(16)
        val msg = IncomingMessage(
            id = AppIds.newId("msg"),
            conversationId = if (isGroup) "g_" + conversationName.hashCode().toUInt().toString(16) else cid,
            conversationName = conversationName,
            conversationType = if (isGroup) ConversationType.GROUP else ConversationType.CONTACT,
            senderId = cid,
            senderName = conversationName,
            content = content,
            timestamp = System.currentTimeMillis(),
            isSelf = false
        ).let { it.copy(fingerprint = Fingerprint.of(it)) }
        handler(msg)
    }

    override suspend fun stop() {
        running.set(false)
        job?.cancel()
        scope?.cancel()
        job = null
        scope = null
        onMessage = null
    }

    override suspend fun send(conversationId: String, conversationName: String, text: String): Result<Unit> {
        outbox.tryEmit(conversationName to text)
        return Result.success(Unit)
    }

    override suspend fun health(): Pair<Boolean, String> = true to "模拟消息源就绪"
}
