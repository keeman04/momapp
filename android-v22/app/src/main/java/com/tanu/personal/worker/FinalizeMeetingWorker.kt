package com.tanu.personal.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tanu.personal.data.*
import com.tanu.personal.db.TanuDao
import com.tanu.personal.domain.MomEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay

@HiltWorker
class FinalizeMeetingWorker @AssistedInject constructor(
    @Assisted appContext:Context,
    @Assisted params:WorkerParameters,
    private val dao:TanuDao,
    private val engine:MomEngine,
    private val repo:MeetingRepository
):CoroutineWorker(appContext,params){
    override suspend fun doWork():Result{
        val id=inputData.getString("meetingId")?:return Result.failure()
        val meeting=dao.meeting(id)?:return Result.failure()

        // Never leave the user on an endless spinner. Rolling chunks normally finish
        // during the meeting; after Stop we wait a bounded 5 minutes for any tail.
        var pending=dao.pendingChunkCount(id)
        var seconds=0
        while(pending>0 && seconds<300){
            dao.setMeetingStatus(id,MeetingStatus.TRANSCRIBING,"Finishing $pending remaining audio chunk${if(pending==1)"" else "s"}…")
            delay(2000)
            seconds+=2
            pending=dao.pendingChunkCount(id)
        }
        if(pending>0){
            dao.failUnfinishedChunks(id)
        }

        return try{
            val segments=dao.segments(id)
            val failed=dao.failedChunkCount(id)
            if(segments.isEmpty()){
                dao.setMeetingStatus(id,MeetingStatus.FAILED,"No usable speech transcript was produced. You can retry with Fast mode or a clearer recording.")
                return Result.failure()
            }
            dao.setMeetingStatus(id,MeetingStatus.GENERATING_MOM,"Transcript complete. Building English MOM…")
            val out=engine.build(meeting,segments)
            dao.upsertMom(out.mom)
            dao.deleteActionsForMeeting(id)
            if(out.actions.isNotEmpty())dao.upsertActions(out.actions)
            val warning=if(failed>0)"MOM created from available speech; $failed chunk${if(failed==1)"" else "s"} could not be transcribed." else null
            dao.setMeetingStatus(id,MeetingStatus.READY,warning)
            repo.applyRetention(meeting.audioPath)
            Result.success()
        }catch(e:Exception){
            dao.setMeetingStatus(id,MeetingStatus.FAILED,e.message?:"MOM generation failed")
            Result.failure()
        }
    }
}
