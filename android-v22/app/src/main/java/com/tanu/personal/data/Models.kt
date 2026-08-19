package com.tanu.personal.data

import androidx.room.*

object MeetingStatus {
    const val RECORDING = "RECORDING"
    const val TRANSCRIBING = "TRANSCRIBING"
    const val GENERATING_MOM = "GENERATING_MOM"
    const val READY = "READY"
    const val FAILED = "FAILED"
}

object ChunkStatus {
    const val PENDING = "PENDING"
    const val PROCESSING = "PROCESSING"
    const val DONE = "DONE"
    const val FAILED = "FAILED"
}

object ProcessingMode {
    const val PRIVATE = "PRIVATE"
}

@Entity(tableName = "meetings")
data class MeetingEntity(
    @PrimaryKey val id: String,
    val title: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val durationMs: Long = 0,
    val status: String = MeetingStatus.RECORDING,
    val processingMode: String = ProcessingMode.PRIVATE,
    val audioPath: String? = null,
    val participantsCsv: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val errorMessage: String? = null
)

@Entity(tableName = "chunks", indices = [Index(value = ["meetingId", "chunkIndex"], unique = true)])
data class ChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val meetingId: String,
    val chunkIndex: Int,
    val startMs: Long,
    val endMs: Long,
    val filePath: String,
    val status: String = ChunkStatus.PENDING,
    val transcript: String = ""
)

@Entity(tableName = "transcript_segments", indices = [Index("meetingId")])
data class TranscriptSegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val meetingId: String,
    val chunkIndex: Int,
    val startMs: Long,
    val endMs: Long,
    val speaker: String = "Speaker",
    val text: String
)

@Entity(tableName = "mom")
data class MomEntity(
    @PrimaryKey val meetingId: String,
    val summary: String,
    val discussionPointsJson: String = "[]",
    val decisionsJson: String = "[]",
    val clientCommitmentsJson: String = "[]",
    val myCommitmentsJson: String = "[]",
    val followUpsJson: String = "[]",
    val importantNumbersJson: String = "[]",
    val nextMeeting: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "actions", indices = [Index("meetingId")])
data class ActionItemEntity(
    @PrimaryKey val id: String,
    val meetingId: String,
    val title: String,
    val owner: String = "Unassigned",
    val dueDate: String = "",
    val priority: String = "normal",
    val status: String = "open"
)

@Entity(tableName = "participants", indices = [Index(value = ["name", "phone"], unique = false)])
data class ParticipantEntity(
    @PrimaryKey val id: String,
    val name: String,
    val company: String = "",
    val phone: String = "",
    val email: String = "",
    val whatsapp: String = "",
    val isGuest: Boolean = true
)
