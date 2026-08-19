package com.tanu.personal.transcription

import com.tanu.personal.data.ChunkEntity

data class TranscriptPiece(val startMs:Long,val endMs:Long,val speaker:String="Speaker",val text:String)
interface TranscriptionProvider { suspend fun transcribe(chunk:ChunkEntity,prompt:String):List<TranscriptPiece> }
