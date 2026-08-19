package com.tanu.personal.db

import androidx.room.*
import com.tanu.personal.data.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TanuDao {
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun upsertMeeting(v:MeetingEntity)
    @Query("SELECT * FROM meetings ORDER BY createdAt DESC") fun observeMeetings():Flow<List<MeetingEntity>>
    @Query("""SELECT DISTINCT m.* FROM meetings m
        LEFT JOIN transcript_segments t ON t.meetingId=m.id
        LEFT JOIN actions a ON a.meetingId=m.id
        LEFT JOIN mom mm ON mm.meetingId=m.id
        WHERE :q='' OR m.title LIKE '%' || :q || '%' OR m.participantsCsv LIKE '%' || :q || '%'
          OR t.text LIKE '%' || :q || '%' OR a.title LIKE '%' || :q || '%' OR a.owner LIKE '%' || :q || '%'
          OR mm.summary LIKE '%' || :q || '%' OR mm.decisionsJson LIKE '%' || :q || '%' OR mm.importantNumbersJson LIKE '%' || :q || '%'
        ORDER BY m.createdAt DESC""") fun searchMeetings(q:String):Flow<List<MeetingEntity>>
    @Query("SELECT * FROM meetings WHERE id=:id LIMIT 1") fun observeMeeting(id:String):Flow<MeetingEntity?>
    @Query("SELECT * FROM meetings WHERE id=:id LIMIT 1") suspend fun meeting(id:String):MeetingEntity?
    @Query("UPDATE meetings SET status=:status, endedAt=:endedAt, durationMs=:duration, audioPath=:audioPath, errorMessage=:error WHERE id=:id") suspend fun finishMeeting(id:String,status:String,endedAt:Long?,duration:Long,audioPath:String?,error:String?=null)
    @Query("UPDATE meetings SET status=:status, errorMessage=:error WHERE id=:id") suspend fun setMeetingStatus(id:String,status:String,error:String?=null)
    @Query("UPDATE meetings SET title=:title, participantsCsv=:participants WHERE id=:id") suspend fun updateMeetingMeta(id:String,title:String,participants:String)
    @Query("DELETE FROM meetings WHERE id=:id") suspend fun deleteMeeting(id:String)
    @Query("DELETE FROM chunks WHERE meetingId=:id") suspend fun deleteChunksForMeeting(id:String)
    @Query("DELETE FROM transcript_segments WHERE meetingId=:id") suspend fun deleteSegmentsForMeeting(id:String)
    @Query("DELETE FROM mom WHERE meetingId=:id") suspend fun deleteMomForMeeting(id:String)

    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun insertChunk(v:ChunkEntity):Long
    @Query("SELECT * FROM chunks WHERE id=:id LIMIT 1") suspend fun chunk(id:Long):ChunkEntity?
    @Query("SELECT * FROM chunks WHERE meetingId=:meetingId ORDER BY chunkIndex") suspend fun chunks(meetingId:String):List<ChunkEntity>
    @Query("UPDATE chunks SET status=:status, transcript=:transcript WHERE id=:id") suspend fun updateChunk(id:Long,status:String,transcript:String="")
    @Query("SELECT COUNT(*) FROM chunks WHERE meetingId=:meetingId AND status IN ('PENDING','PROCESSING')") suspend fun pendingChunkCount(meetingId:String):Int
    @Query("SELECT COUNT(*) FROM chunks WHERE meetingId=:meetingId AND status='FAILED'") suspend fun failedChunkCount(meetingId:String):Int
    @Query("SELECT COUNT(*) FROM chunks WHERE meetingId=:meetingId") fun observeTotalChunks(meetingId:String):Flow<Int>
    @Query("SELECT COUNT(*) FROM chunks WHERE meetingId=:meetingId AND status='DONE'") fun observeDoneChunks(meetingId:String):Flow<Int>
    @Query("SELECT COUNT(*) FROM chunks WHERE meetingId=:meetingId AND status='FAILED'") fun observeFailedChunks(meetingId:String):Flow<Int>
    @Query("UPDATE chunks SET status='FAILED' WHERE meetingId=:meetingId AND status IN ('PENDING','PROCESSING')") suspend fun failUnfinishedChunks(meetingId:String)

    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun insertSegment(v:TranscriptSegmentEntity)
    @Query("DELETE FROM transcript_segments WHERE meetingId=:meetingId AND chunkIndex=:chunkIndex") suspend fun deleteSegmentsForChunk(meetingId:String,chunkIndex:Int)
    @Query("SELECT * FROM transcript_segments WHERE meetingId=:meetingId ORDER BY startMs,id") fun observeSegments(meetingId:String):Flow<List<TranscriptSegmentEntity>>
    @Query("SELECT * FROM transcript_segments WHERE meetingId=:meetingId ORDER BY startMs,id") suspend fun segments(meetingId:String):List<TranscriptSegmentEntity>

    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun upsertMom(v:MomEntity)
    @Query("SELECT * FROM mom WHERE meetingId=:meetingId LIMIT 1") fun observeMom(meetingId:String):Flow<MomEntity?>
    @Query("SELECT * FROM mom WHERE meetingId=:meetingId LIMIT 1") suspend fun mom(meetingId:String):MomEntity?

    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun upsertActions(v:List<ActionItemEntity>)
    @Query("SELECT * FROM actions WHERE meetingId=:meetingId ORDER BY status,dueDate") fun observeActions(meetingId:String):Flow<List<ActionItemEntity>>
    @Query("SELECT * FROM actions WHERE status!='done' ORDER BY dueDate") fun observeOpenActions():Flow<List<ActionItemEntity>>
    @Query("SELECT * FROM actions WHERE status!='done' ORDER BY dueDate") suspend fun openActionsList():List<ActionItemEntity>
    @Query("UPDATE actions SET status=:status WHERE id=:id") suspend fun setActionStatus(id:String,status:String)
    @Query("DELETE FROM actions WHERE meetingId=:meetingId") suspend fun deleteActionsForMeeting(meetingId:String)

    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun upsertParticipant(v:ParticipantEntity)
    @Query("SELECT * FROM participants ORDER BY name") fun observeParticipants():Flow<List<ParticipantEntity>>
}
