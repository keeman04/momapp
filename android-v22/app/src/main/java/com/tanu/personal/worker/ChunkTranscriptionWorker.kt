package com.tanu.personal.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tanu.personal.data.*
import com.tanu.personal.audio.PcmSpeechGate
import com.tanu.personal.db.TanuDao
import com.tanu.personal.transcription.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

@HiltWorker
class ChunkTranscriptionWorker @AssistedInject constructor(
    @Assisted appContext:Context,@Assisted params:WorkerParameters,
    private val dao:TanuDao, private val settings:SettingsStore, private val fast:FastServerProvider, private val local:LocalProvider
):CoroutineWorker(appContext,params){
    override suspend fun doWork():Result{
        val id=inputData.getLong("chunkId",0);val c=dao.chunk(id)?:return Result.success();if(c.status==ChunkStatus.DONE)return Result.success()
        dao.updateChunk(id,ChunkStatus.PROCESSING)
        val file=File(c.filePath)
        if(!PcmSpeechGate.hasLikelySpeech(file)){dao.updateChunk(id,ChunkStatus.DONE,"");runCatching{file.delete()};return Result.success()}
        val meeting=dao.meeting(c.meetingId)?:return Result.failure();val prompt=listOf(meeting.participantsCsv,settings.customVocabulary).filter{it.isNotBlank()}.joinToString(", ")
        return try{
            val pieces=if(meeting.processingMode==ProcessingMode.FAST&&fast.configured()&&runAttemptCount<2){runCatching{fast.transcribe(c,prompt)}.getOrElse{local.transcribe(c,prompt)}}else local.transcribe(c,prompt)
            val previous=dao.segments(c.meetingId).filter{it.chunkIndex<c.chunkIndex}.lastOrNull()?.text.orEmpty()
            val cleaned=pieces.mapIndexed{idx,p->if(idx==0)p.copy(text=removeOverlap(previous,p.text)) else p}.filter{it.text.isNotBlank()}
            cleaned.forEach{dao.insertSegment(TranscriptSegmentEntity(meetingId=c.meetingId,chunkIndex=c.chunkIndex,startMs=it.startMs,endMs=it.endMs,speaker=it.speaker,text=it.text))}
            dao.updateChunk(id,ChunkStatus.DONE,cleaned.joinToString(" "){it.text});runCatching{File(c.filePath).delete()};Result.success()
        }catch(e:Exception){
            if(runAttemptCount<2){
                dao.updateChunk(id,ChunkStatus.PENDING)
                dao.setMeetingStatus(c.meetingId,MeetingStatus.TRANSCRIBING,"Retrying audio chunk ${c.chunkIndex+1}…")
                Result.retry()
            }else{
                dao.updateChunk(id,ChunkStatus.FAILED)
                dao.setMeetingStatus(c.meetingId,MeetingStatus.TRANSCRIBING,"One audio chunk could not be transcribed. TANU will finish from the available speech.")
                Result.failure()
            }
        }
    }
    private fun removeOverlap(previous:String,current:String):String{
        if(previous.isBlank()||current.isBlank())return current.trim()
        val a=previous.trim().split(Regex("\\s+")).takeLast(14)
        val b=current.trim().split(Regex("\\s+"))
        val max=minOf(a.size,b.size,12)
        for(n in max downTo 2){
            if(a.takeLast(n).map{it.lowercase().trim(',', '.', '!', '?') }==b.take(n).map{it.lowercase().trim(',', '.', '!', '?') })return b.drop(n).joinToString(" ").trim()
        }
        return current.trim()
    }
}
