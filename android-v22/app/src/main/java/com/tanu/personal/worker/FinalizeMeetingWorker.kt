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
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dao: TanuDao,
    private val agent: TanuAgent,
    private val repo: MeetingRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString("meetingId") ?: return Result.failure()
        val meeting = dao.meeting(id) ?: return Result.success()

        return try {
            // Entry-level phones can build a backlog during multi-hour meetings.
            // Never discard unfinished audio just because transcription is slower than real time.
            var pending = dao.pendingChunkCount(id)
            var seconds = 0
            while (pending > 0 && seconds < 120 && !isStopped) {
                dao.setMeetingStatus(
                    id,
                    MeetingStatus.TRANSCRIBING,
                    "Recording is safe. TANU is finishing the remaining audio."
                )
                delay(2000)
                seconds += 2
                pending = dao.pendingChunkCount(id)
            }

            if (isStopped || pending > 0) {
                // WorkManager will retry later. Keep PCM chunks until every available section
                // has either completed transcription or genuinely failed in its own worker.
                return Result.retry()
            }

            val segments = dao.segments(id)
            val failed = dao.failedChunkCount(id)
            if (segments.isEmpty()) {
                dao.setMeetingStatus(
                    id,
                    MeetingStatus.FAILED,
                    "No usable speech was detected. Please retry with a clearer recording."
                )
                repo.cleanupChunkFiles(id)
                return Result.failure()
            }

            dao.setMeetingStatus(id, MeetingStatus.GENERATING_MOM, null)
            val out = agent.build(meeting, segments)
            dao.upsertMom(out.mom)
            dao.deleteActionsForMeeting(id)
            if (out.actions.isNotEmpty()) dao.upsertActions(out.actions)

            val warning = if (failed > 0) {
                "MOM created from available speech; $failed audio section${if (failed == 1) "" else "s"} could not be transcribed."
            } else null
            dao.setMeetingStatus(id, MeetingStatus.READY, warning)
            repo.applyRetention(meeting.audioPath)
            repo.cleanupChunkFiles(id)
            Result.success()
        } catch (e: Exception) {
            dao.setMeetingStatus(
                id,
                MeetingStatus.FAILED,
                e.message?.take(240) ?: "Meeting notes could not be created"
            )
            Result.failure()
        }
    }
}
