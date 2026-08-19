package com.tanu.personal.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tanu.personal.ai.TanuAgent
import com.tanu.personal.data.MeetingRepository
import com.tanu.personal.data.MeetingStatus
import com.tanu.personal.db.TanuDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay

@HiltWorker
class FinalizeMeetingWorker @AssistedInject constructor(
    @Assisted appContext:Context,
    @Assisted params:WorkerParameters,
    private val dao:TanuDao,
    private val agent:TanuAgent,
    private val repo:MeetingRepository
):CoroutineWorker(appContext,params){
    override suspend fun doWork():Result{
        val id=inputData.getString("meetingId")?:return Result.failure()
        val meeting=dao.meeting(id)?:return Result.failure()

        // Rolling transcription should already be nearly complete when Stop is pressed.
        // Keep the tail bounded so the UI can never wait forever.
        var pending=dao.pendingChunkCount(id)
        var seconds=0
        while(pending>0 && seconds<300){
            dao.setMeetingStatus(id,MeetingStatus.TRANSCRIBING,"Finishing your meeting notes…")
            delay(2000)
            seconds+=2
            pending=dao.pendingChunkCount(id)
        }
        if(pending>0) dao.failUnfinishedChunks(id)

        return try{
            val segments=dao.segments(id)
            val failed=dao.failedChunkCount(id)
            if(segments.isEmpty()){
                dao.setMeetingStatus(id,MeetingStatus.FAILED,"No usable speech was detected. Please retry with a clearer recording.")
                return Result.failure()
            }
            dao.setMeetingStatus(id,MeetingStatus.GENERATING_MOM,"Creating your meeting notes…")

            // TanuAgent tries OpenAI (when connected) or the embedded local Qwen engine.
            // Both are optional enhancements: its deterministic engine is always the final fallback.
            val out=agent.build(meeting,segments)
            dao.upsertMom(out.mom)
            dao.deleteActionsForMeeting(id)
            if(out.actions.isNotEmpty())dao.upsertActions(out.actions)

            val warning=if(failed>0)"MOM created from available speech; $failed audio section${if(failed==1)"" else "s"} could not be transcribed." else null
            dao.setMeetingStatus(id,MeetingStatus.READY,warning)
            repo.applyRetention(meeting.audioPath)
            Result.success()
        }catch(e:Exception){
            dao.setMeetingStatus(id,MeetingStatus.FAILED,e.message?:"Meeting notes could not be created")
            Result.failure()
        }
    }
}
