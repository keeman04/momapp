package com.tanu.personal.data

import android.content.Context
import androidx.work.*
import com.tanu.personal.db.TanuDao
import com.tanu.personal.worker.ChunkTranscriptionWorker
import com.tanu.personal.worker.FinalizeMeetingWorker
import com.tanu.personal.worker.ImportAudioWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeetingRepository @Inject constructor(
    @ApplicationContext private val context:Context,
    private val dao:TanuDao,
    private val work:WorkManager,
    private val settings:SettingsStore
){
    fun meetings()=dao.observeMeetings()
    fun searchMeetings(q:String)=dao.searchMeetings(q.trim())
    fun meeting(id:String)=dao.observeMeeting(id)
    fun segments(id:String)=dao.observeSegments(id)
    fun mom(id:String)=dao.observeMom(id)
    fun actions(id:String)=dao.observeActions(id)
    fun totalChunks(id:String)=dao.observeTotalChunks(id)
    fun doneChunks(id:String)=dao.observeDoneChunks(id)
    fun failedChunks(id:String)=dao.observeFailedChunks(id)
    fun openActions()=dao.observeOpenActions()
    fun participants()=dao.observeParticipants()

    suspend fun createMeeting(title:String,participants:String,mode:String):MeetingEntity{
        val id=UUID.randomUUID().toString()
        val m=MeetingEntity(id=id,title=title.ifBlank{"Untitled meeting"},startedAt=System.currentTimeMillis(),processingMode=mode,participantsCsv=participants)
        dao.upsertMeeting(m); return m
    }
    suspend fun insertChunk(meetingId:String,index:Int,startMs:Long,endMs:Long,file:File):Long{
        val id=dao.insertChunk(ChunkEntity(meetingId=meetingId,chunkIndex=index,startMs=startMs,endMs=endMs,filePath=file.absolutePath))
        val m=dao.meeting(meetingId)
        enqueueChunk(id,meetingId,index,m?.processingMode==ProcessingMode.FAST && settings.serverUrl.startsWith("https://"))
        return id
    }
    fun enqueueChunk(chunkId:Long,meetingId:String,index:Int,network:Boolean){
        val b=OneTimeWorkRequestBuilder<ChunkTranscriptionWorker>()
            .setInputData(workDataOf("chunkId" to chunkId))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,10,java.util.concurrent.TimeUnit.SECONDS)
            .addTag("meeting:$meetingId")
        if(network)b.setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        work.beginUniqueWork("tanu-transcribe-$meetingId",ExistingWorkPolicy.APPEND_OR_REPLACE,b.build()).enqueue()
    }
    fun enqueueImport(meetingId:String,path:String){
        val req=OneTimeWorkRequestBuilder<ImportAudioWorker>().setInputData(workDataOf("meetingId" to meetingId,"path" to path)).build()
        work.enqueueUniqueWork("tanu-import-$meetingId",ExistingWorkPolicy.REPLACE,req)
    }
    fun enqueueFinalize(meetingId:String){
        val req=OneTimeWorkRequestBuilder<FinalizeMeetingWorker>()
            .setInputData(workDataOf("meetingId" to meetingId))
            .setInitialDelay(1,java.util.concurrent.TimeUnit.SECONDS)
            .addTag("meeting-final:$meetingId")
            .build()
        work.enqueueUniqueWork("tanu-final-$meetingId",ExistingWorkPolicy.REPLACE,req)
    }
    suspend fun markStopped(meetingId:String,audioPath:String?,duration:Long){
        dao.finishMeeting(meetingId,MeetingStatus.TRANSCRIBING,System.currentTimeMillis(),duration,audioPath)
        enqueueFinalize(meetingId)
    }
    suspend fun setActionDone(id:String,done:Boolean)=dao.setActionStatus(id,if(done)"done" else "open")
    suspend fun deleteMeeting(id:String){
        val meeting=dao.meeting(id)
        dao.deleteActionsForMeeting(id);dao.deleteSegmentsForMeeting(id);dao.deleteChunksForMeeting(id);dao.deleteMomForMeeting(id);dao.deleteMeeting(id)
        meeting?.audioPath?.takeIf{it.isNotBlank()}?.let{runCatching{File(it).delete()}}
    }
    suspend fun saveParticipant(name:String,company:String="",phone:String=""){
        dao.upsertParticipant(ParticipantEntity(UUID.randomUUID().toString(),name=name,company=company,phone=phone,isGuest=true))
    }
    suspend fun applyRetention(audioPath:String?){
        if(audioPath.isNullOrBlank())return
        if(settings.retention=="after_mom") runCatching{ File(audioPath).delete() }
    }
}
