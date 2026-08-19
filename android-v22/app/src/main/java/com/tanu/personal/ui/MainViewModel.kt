package com.tanu.personal.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanu.personal.data.*
import com.tanu.personal.db.TanuDao
import com.tanu.personal.security.OpenAiKeyStore
import com.tanu.personal.security.SecureTokenStore
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
    @ApplicationContext private val context:Context,
    private val repo:MeetingRepository,
    private val dao:TanuDao,
    val settings:SettingsStore,
    private val tokens:SecureTokenStore,
    private val openAiKeys:OpenAiKeyStore
):ViewModel(){
    fun searchMeetings(q:String)=repo.searchMeetings(q)
    val meetings=repo.meetings().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val openActions=repo.openActions().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val participants=repo.participants().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    private val currentId=MutableStateFlow<String?>(null)
    val meeting=currentId.flatMapLatest{if(it==null)flowOf(null)else repo.meeting(it)}.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),null)
    val segments=currentId.flatMapLatest{if(it==null)flowOf(emptyList())else repo.segments(it)}.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val mom=currentId.flatMapLatest{if(it==null)flowOf(null)else repo.mom(it)}.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),null)
    val actions=currentId.flatMapLatest{if(it==null)flowOf(emptyList())else repo.actions(it)}.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val chunkStats=currentId.flatMapLatest{id->
        if(id==null) flowOf(Triple(0,0,0))
        else combine(repo.doneChunks(id),repo.totalChunks(id),repo.failedChunks(id)){done,total,failed->Triple(done,total,failed)}
    }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),Triple(0,0,0))

    fun selectMeeting(id:String){currentId.value=id}

    fun startMeeting(title:String,participants:String,mode:String=settings.defaultMode,onStarted:(String)->Unit)=viewModelScope.launch{
        val m=repo.createMeeting(title,participants,mode);currentId.value=m.id
        val i=Intent(context,RecordingService::class.java)
            .setAction(RecordingService.ACTION_START)
            .putExtra(RecordingService.EXTRA_ID,m.id)
            .putExtra(RecordingService.EXTRA_TITLE,m.title)
        if(Build.VERSION.SDK_INT>=26)context.startForegroundService(i) else context.startService(i)
        onStarted(m.id)
    }

    fun stopRecording(){context.startService(Intent(context,RecordingService::class.java).setAction(RecordingService.ACTION_STOP))}
    fun pause(){context.startService(Intent(context,RecordingService::class.java).setAction(RecordingService.ACTION_PAUSE))}
    fun resume(){context.startService(Intent(context,RecordingService::class.java).setAction(RecordingService.ACTION_RESUME))}

    fun importAudio(source:File,title:String,onCreated:(String)->Unit)=viewModelScope.launch{
        val m=repo.createMeeting(title,"",settings.defaultMode);currentId.value=m.id
        repo.enqueueImport(m.id,source.absolutePath);onCreated(m.id)
    }

    fun doneAction(id:String,done:Boolean)=viewModelScope.launch{repo.setActionDone(id,done)}
    fun deleteMeeting(id:String)=viewModelScope.launch{repo.deleteMeeting(id)}
    fun saveParticipant(name:String,company:String,phone:String)=viewModelScope.launch{repo.saveParticipant(name,company,phone)}
    fun updateMeta(id:String,title:String,participants:String)=viewModelScope.launch{dao.updateMeetingMeta(id,title,participants)}
    fun saveMomSummary(id:String,summary:String)=viewModelScope.launch{
        val old=dao.mom(id)?:return@launch
        dao.upsertMom(old.copy(summary=summary,updatedAt=System.currentTimeMillis()))
    }

    fun toggleFloating(enabled:Boolean){
        settings.floatingEnabled=enabled
        val i=Intent(context,FloatingAssistantService::class.java)
        if(enabled)context.startService(i)else context.stopService(i)
    }

    fun saveFastServer(url:String,token:String){settings.serverUrl=url;if(token.isNotBlank())tokens.save(token)}
    fun clearFastServerToken(){tokens.save("")}
    fun hasFastServerToken()=tokens.load().isNotBlank()

    fun setRetention(value:String){settings.retention=value;settings.retentionConfigured=true}
    fun setAiMode(value:String){settings.aiMode=value}
    fun saveOpenAiKey(value:String){openAiKeys.save(value)}
    fun clearOpenAiKey(){openAiKeys.clear()}
    fun hasOpenAiKey():Boolean=openAiKeys.hasKey()
}
