package com.tanu.personal.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.tanu.personal.data.*

@Database(entities=[MeetingEntity::class,ChunkEntity::class,TranscriptSegmentEntity::class,MomEntity::class,ActionItemEntity::class,ParticipantEntity::class], version=1, exportSchema=false)
abstract class TanuDatabase:RoomDatabase(){ abstract fun dao():TanuDao }
