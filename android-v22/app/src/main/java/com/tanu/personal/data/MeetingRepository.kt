package com.tanu.personal.data

import android.content.Context
import androidx.work.*
import com.tanu.personal.db.TanuDao
import com.tanu.personal.worker.ChunkTranscriptionWorker
import com.tanu.personal.worker.FinalizeMeetingWorker
import com.tanu.personal.worker.ImportAudioWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeetingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: TanuDao,
    private val work: WorkManager,
    private val settings: SettingsStore
) {
    fun meetings() = dao.observeMeetings()
    fun searchMeetings(q: String) = dao.searchMeetings(q.trim())
    fun meeting(id: String) = dao.observeMeeting(id)
    fun segments(id: String) = dao.observeSegments(id)
    fun mom(id: String) = dao.observeMom(id)
    fun actions(id: String) = dao.observeActions(id)
    fun totalChunks(id: String) = dao.observeTotalChunks(id)
    fun doneChunks(id: String) = dao.observeDoneChunks(id)
    fun failedChunks(id: String) = dao.observeFailedChunks(id)
    fun openActions() = dao.observeOpenActions()
    fun participants() = dao.observeParticipants()

    suspend fun createMeeting(title: String, participants: String): MeetingEntity {
        val id = UUID.randomUUID().toString()
        val m = MeetingEntity(
            id = id,
            title = title.ifBlank { "Untitled meeting" },
            startedAt = System.currentTimeMillis(),
            processingMode = ProcessingMode.PRIVATE,
            participantsCsv = participants
        )
        dao.upsertMeeting(m)
        return m
    }

    suspend fun insertChunk(meetingId: String, index: Int, startMs: Long, endMs: Long, file: File): Long {
        val id = dao.insertChunk(
            ChunkEntity(
                meetingId = meetingId,
                chunkIndex = index,
                startMs = startMs,
                endMs = endMs,
                filePath = file.absolutePath
            )
        )
        enqueueChunk(id, meetingId, index)
        return id
    }

    private fun enqueueChunk(chunkId: Long, meetingId: String, index: Int) {
        val request = OneTimeWorkRequestBuilder<ChunkTranscriptionWorker>()
            .setInputData(workDataOf("chunkId" to chunkId))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, java.util.concurrent.TimeUnit.SECONDS)
            .addTag("meeting:$meetingId")
            .addTag("meeting-chunk:$meetingId")
            .build()
        // Each chunk has independent retry/failure state. NativeWhisper itself serializes
        // inference, so a failed chunk can never block every later chunk in a dependency chain.
        work.enqueueUniqueWork(
            "tanu-transcribe-$meetingId-$index",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun enqueueImport(meetingId: String, path: String) {
        val req = OneTimeWorkRequestBuilder<ImportAudioWorker>()
            .setInputData(workDataOf("meetingId" to meetingId, "path" to path))
            .addTag("meeting:$meetingId")
            .build()
        work.enqueueUniqueWork("tanu-import-$meetingId", ExistingWorkPolicy.REPLACE, req)
    }

    fun enqueueFinalize(meetingId: String) {
        val req = OneTimeWorkRequestBuilder<FinalizeMeetingWorker>()
            .setInputData(workDataOf("meetingId" to meetingId))
            .setInitialDelay(1, java.util.concurrent.TimeUnit.SECONDS)
            .addTag("meeting:$meetingId")
            .addTag("meeting-final:$meetingId")
            .build()
        work.enqueueUniqueWork("tanu-final-$meetingId", ExistingWorkPolicy.REPLACE, req)
    }

    suspend fun markStopped(meetingId: String, audioPath: String?, duration: Long) {
        val usablePath = audioPath?.takeIf { path -> File(path).let { it.exists() && it.length() > 0L } }
        dao.finishMeeting(
            meetingId,
            MeetingStatus.TRANSCRIBING,
            System.currentTimeMillis(),
            duration,
            usablePath
        )
        enqueueFinalize(meetingId)
    }

    suspend fun markFailed(meetingId: String, message: String) {
        dao.setMeetingStatus(meetingId, MeetingStatus.FAILED, message.take(240))
    }

    suspend fun setActionDone(id: String, done: Boolean) =
        dao.setActionStatus(id, if (done) "done" else "open")

    suspend fun deleteMeeting(id: String) {
        work.cancelAllWorkByTag("meeting:$id")
        work.cancelUniqueWork("tanu-import-$id")
        work.cancelUniqueWork("tanu-final-$id")

        val meeting = dao.meeting(id)
        val chunks = dao.chunks(id)
        chunks.forEach { runCatching { File(it.filePath).delete() } }
        File(context.filesDir, "chunks").listFiles()
            ?.filter { it.isFile && it.name.startsWith("${id}_") }
            ?.forEach { runCatching { it.delete() } }

        dao.deleteActionsForMeeting(id)
        dao.deleteSegmentsForMeeting(id)
        dao.deleteChunksForMeeting(id)
        dao.deleteMomForMeeting(id)
        dao.deleteMeeting(id)

        meeting?.audioPath?.takeIf { it.isNotBlank() }?.let { runCatching { File(it).delete() } }
    }

    suspend fun cleanupChunkFiles(meetingId: String) {
        dao.chunks(meetingId).forEach { runCatching { File(it.filePath).delete() } }
        File(context.filesDir, "chunks").listFiles()
            ?.filter { it.isFile && it.name.startsWith("${meetingId}_") }
            ?.forEach { runCatching { it.delete() } }
    }

    suspend fun saveParticipant(
        name: String,
        whatsapp: String,
        email: String = "",
        company: String = ""
    ) {
        if (!ParticipantRules.canSave(name, whatsapp, email)) return
        val normalized = ParticipantRules.normalizeWhatsapp(whatsapp)
        dao.upsertParticipant(
            ParticipantEntity(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                company = company.trim(),
                phone = normalized,
                email = email.trim(),
                whatsapp = normalized,
                isGuest = true
            )
        )
    }

    suspend fun applyRetention(audioPath: String?) {
        if (audioPath.isNullOrBlank()) return
        if (settings.retention == "after_mom") runCatching { File(audioPath).delete() }
    }
}
