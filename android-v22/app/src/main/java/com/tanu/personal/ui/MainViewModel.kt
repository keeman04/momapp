package com.tanu.personal.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanu.personal.data.*
import com.tanu.personal.db.TanuDao
import com.tanu.personal.security.OpenAiKeyStore
import com.tanu.personal.service.FloatingAssistantService
import com.tanu.personal.service.RecordingService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: MeetingRepository,
    private val dao: TanuDao,
    val settings: SettingsStore,
    private val openAiKeys: OpenAiKeyStore
) : ViewModel() {
    fun searchMeetings(q: String) = repo.searchMeetings(q)
    val meetings = repo.meetings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val openActions = repo.openActions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val participants = repo.participants().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val currentId = MutableStateFlow<String?>(null)
    val meeting = currentId.flatMapLatest { if (it == null) flowOf(null) else repo.meeting(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val segments = currentId.flatMapLatest { if (it == null) flowOf(emptyList()) else repo.segments(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val mom = currentId.flatMapLatest { if (it == null) flowOf(null) else repo.mom(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val actions = currentId.flatMapLatest { if (it == null) flowOf(emptyList()) else repo.actions(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectMeeting(id: String) {
        currentId.value = id
    }

    fun startMeeting(title: String, participants: String, onStarted: (String) -> Unit) = viewModelScope.launch {
        val meeting = repo.createMeeting(title, participants)
        currentId.value = meeting.id
        val intent = Intent(context, RecordingService::class.java)
            .setAction(RecordingService.ACTION_START)
            .putExtra(RecordingService.EXTRA_ID, meeting.id)
            .putExtra(RecordingService.EXTRA_TITLE, meeting.title)
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        onStarted(meeting.id)
    }

    fun stopRecording() {
        context.startService(Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_STOP))
    }

    fun pause() {
        context.startService(Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_PAUSE))
    }

    fun resume() {
        context.startService(Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_RESUME))
    }

    fun importAudio(source: File, title: String, onCreated: (String) -> Unit) = viewModelScope.launch {
        val meeting = repo.createMeeting(title, "")
        currentId.value = meeting.id
        repo.enqueueImport(meeting.id, source.absolutePath)
        onCreated(meeting.id)
    }

    fun doneAction(id: String, done: Boolean) = viewModelScope.launch { repo.setActionDone(id, done) }
    fun deleteMeeting(id: String) = viewModelScope.launch { repo.deleteMeeting(id) }
    fun saveParticipant(name: String, company: String, phone: String) =
        viewModelScope.launch { repo.saveParticipant(name, company, phone) }

    fun updateMeta(id: String, title: String, participants: String) =
        viewModelScope.launch { dao.updateMeetingMeta(id, title, participants) }

    fun saveMomSummary(id: String, summary: String) = viewModelScope.launch {
        val old = dao.mom(id) ?: return@launch
        dao.upsertMom(old.copy(summary = summary, updatedAt = System.currentTimeMillis()))
    }

    fun toggleFloating(enabled: Boolean) {
        settings.floatingEnabled = enabled
        val intent = Intent(context, FloatingAssistantService::class.java)
        if (enabled) context.startService(intent) else context.stopService(intent)
    }

    fun setRetention(value: String) {
        settings.retention = value
        settings.retentionConfigured = true
    }

    fun setAiMode(value: String) {
        settings.aiMode = value
    }

    fun saveOpenAiKey(value: String) {
        openAiKeys.save(value)
    }

    fun clearOpenAiKey() {
        openAiKeys.clear()
    }

    fun hasOpenAiKey(): Boolean = openAiKeys.hasKey()
}
