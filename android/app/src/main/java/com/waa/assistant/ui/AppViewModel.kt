package com.waa.assistant.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.waa.assistant.WaaApp
import com.waa.assistant.data.model.ConversationEntity
import com.waa.assistant.data.model.DashboardStats
import com.waa.assistant.data.model.JobStatus
import com.waa.assistant.data.model.LogEntity
import com.waa.assistant.data.model.ReplyJobEntity
import com.waa.assistant.data.model.ReplyMode
import com.waa.assistant.data.model.RuntimeStatus
import com.waa.assistant.data.prefs.AppSettings
import com.waa.assistant.domain.ai.AiRouter
import com.waa.assistant.service.AssistantForegroundService
import com.waa.assistant.util.DayUtils
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val waa = app as WaaApp
    private val db = waa.db
    private val settingsRepo = waa.settingsRepo
    private val engine = waa.engine
    private val aiRouter = AiRouter()

    val settings: StateFlow<AppSettings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val conversations: StateFlow<List<ConversationEntity>> =
        db.conversations().observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val reviewJobs: StateFlow<List<ReplyJobEntity>> =
        db.jobs().observeByStatus(JobStatus.REVIEW.name)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentJobs: StateFlow<List<ReplyJobEntity>> =
        db.jobs().observeRecent(80)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val logs: StateFlow<List<LogEntity>> =
        db.logs().observeRecent(200)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pendingReviewCount: StateFlow<Int> =
        reviewJobs.map { it.size }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _dashboard = kotlinx.coroutines.flow.MutableStateFlow(DashboardStats())
    val dashboard: StateFlow<DashboardStats> = _dashboard

    init {
        viewModelScope.launch {
            combine(settings, engine.status, conversations, reviewJobs) { s, st, chats, reviews ->
                Quadruple(s, st, chats, reviews)
            }.collect { (s, st, chats, reviews) ->
                val start = DayUtils.startOfToday()
                val received = runCatching { db.messages().countReceivedToday(start) }.getOrDefault(0)
                val ai = runCatching { db.jobs().countAiToday(start) }.getOrDefault(0)
                val sent = runCatching { db.jobs().countSentToday(start) }.getOrDefault(0)
                val (label, ready) = aiRouter.statusLabel(s)
                _dashboard.value = DashboardStats(
                    runtimeStatus = st,
                    modelLabel = label,
                    modelReady = ready,
                    todayReceived = received,
                    todayAiReplies = ai,
                    todayAutoSent = sent,
                    activeConversations = chats.size,
                    pendingReview = reviews.size
                )
            }
        }
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    fun acceptPermissions() = viewModelScope.launch {
        settingsRepo.update { it.copy(permissionIntroAccepted = true) }
    }

    fun start() {
        AssistantForegroundService.start(getApplication())
    }

    fun pause() = engine.pause()

    fun resume() {
        if (engine.status.value == RuntimeStatus.STOPPED) start()
        else engine.resume()
    }

    fun stop() = AssistantForegroundService.stop(getApplication())

    fun updateSettings(transform: (AppSettings) -> AppSettings) = viewModelScope.launch {
        settingsRepo.update(transform)
    }

    fun injectDemo(name: String, content: String, isGroup: Boolean = false) = viewModelScope.launch {
        if (engine.status.value == RuntimeStatus.STOPPED) {
            start()
            kotlinx.coroutines.delay(500)
        }
        engine.injectSimulator(name, content, isGroup)
    }

    fun approve(jobId: String, text: String? = null) = viewModelScope.launch {
        engine.approveJob(jobId, text)
    }

    fun ignore(jobId: String) = viewModelScope.launch { engine.ignoreJob(jobId) }

    fun regenerate(jobId: String) = viewModelScope.launch { engine.regenerateJob(jobId) }

    fun clearContext(conversationId: String) = viewModelScope.launch {
        db.messages().clearConversation(conversationId)
    }

    fun clearAllContext() = viewModelScope.launch { db.messages().clearAll() }

    fun setReplyMode(mode: ReplyMode) = updateSettings { it.copy(replyMode = mode) }
}
