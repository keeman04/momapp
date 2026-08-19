package com.tanu.personal.transcription

import com.tanu.personal.data.ChunkEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalProvider @Inject constructor(private val whisper:NativeWhisper):TranscriptionProvider{
    override suspend fun transcribe(chunk:ChunkEntity,prompt:String)=withContext(Dispatchers.IO){
        val text=whisper.transcribe(File(chunk.filePath),prompt);if(text.isBlank()) emptyList() else listOf(TranscriptPiece(chunk.startMs,chunk.endMs,"Speaker",text))
    }
}
