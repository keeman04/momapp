package com.tanu.personal.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tanu.personal.audio.PcmSpeechGate
import com.tanu.personal.data.*
import com.tanu.personal.db.TanuDao
import com.tanu.personal.transcription.LocalProvider
import com.tanu.personal.transcription.TranscriptOverlap
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

@HiltWorker
class ChunkTranscriptionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dao: TanuDao,
    private val settings: SettingsStore,
    private val local: LocalProvider
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getLong("chunkId", 0L)
        val chunk = dao.chunk(id) ?: return Result.success()
        if (chunk.status == ChunkStatus.DONE || chunk.status == ChunkStatus.FAILED) return Result.success()

        val file = File(chunk.filePath)
        if (!file.exists()) {
            dao.updateChunk(id, ChunkStatus.FAILED)
            return Result.failure()
        }

        dao.updateChunk(id, ChunkStatus.PROCESSING)

        if (!PcmSpeechGate.hasLikelySpeech(file)) {
            dao.updateChunk(id, ChunkStatus.DONE, "")
            runCatching { file.delete() }
            return Result.success()
        }

        val meeting = dao.meeting(chunk.meetingId)
        if (meeting == null) {
            runCatching { file.delete() }
            return Result.success()
        }
        val prompt = listOf(meeting.participantsCsv, settings.customVocabulary)
            .filter { it.isNotBlank() }
            .joinToString(", ")

        return try {
            val pieces = local.transcribe(chunk, prompt)
            val previous = dao.segments(chunk.meetingId)
                .filter { it.chunkIndex < chunk.chunkIndex }
                .lastOrNull()
                ?.text
                .orEmpty()
            val cleaned = pieces.mapIndexed { idx, piece ->
                if (idx == 0) piece.copy(text = TranscriptOverlap.remove(previous, piece.text)) else piece
            }.filter { it.text.isNotBlank() }

            cleaned.forEach { piece ->
                dao.insertSegment(
                    TranscriptSegmentEntity(
                        meetingId = chunk.meetingId,
                        chunkIndex = chunk.chunkIndex,
                        startMs = piece.startMs,
                        endMs = piece.endMs,
                        speaker = piece.speaker,
                        text = piece.text
                    )
                )
            }
            dao.updateChunk(id, ChunkStatus.DONE, cleaned.joinToString(" ") { it.text })
            runCatching { file.delete() }
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 2) {
                dao.updateChunk(id, ChunkStatus.PENDING)
                Result.retry()
            } else {
                dao.updateChunk(id, ChunkStatus.FAILED)
                runCatching { file.delete() }
                Result.failure()
            }
        }
    }
}
