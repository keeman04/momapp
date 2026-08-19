package com.tanu.personal.transcription

import com.tanu.personal.data.ChunkEntity
import com.tanu.personal.data.SettingsStore
import com.tanu.personal.security.SecureTokenStore
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FastServerProvider @Inject constructor(private val settings:SettingsStore, private val tokens:SecureTokenStore):TranscriptionProvider{
    fun configured()=settings.serverUrl.startsWith("https://")
    override suspend fun transcribe(chunk:ChunkEntity,prompt:String)=withContext(Dispatchers.IO){
        val base=settings.serverUrl;if(!configured())throw IllegalStateException("Fast server is not configured")
        val c=(URL("$base/v1/transcribe/chunk").openConnection() as HttpURLConnection).apply{
            requestMethod="POST";doOutput=true;connectTimeout=8000;readTimeout=30000;setRequestProperty("Content-Type","application/octet-stream");setRequestProperty("Content-Encoding","gzip");setRequestProperty("X-TANU-Meeting",chunk.meetingId);setRequestProperty("X-TANU-Chunk",chunk.chunkIndex.toString());setRequestProperty("X-TANU-Sample-Rate","16000");setRequestProperty("X-TANU-Prompt-B64",Base64.encodeToString(prompt.take(1200).toByteArray(Charsets.UTF_8),Base64.NO_WRAP));val token=tokens.load();if(token.isNotBlank())setRequestProperty("X-TANU-Token",token)
        }
        File(chunk.filePath).inputStream().use{input->GZIPOutputStream(c.outputStream).use{out->input.copyTo(out)}}
        val code=c.responseCode;if(code !in 200..299)throw IllegalStateException("Fast transcription server returned $code")
        val raw=c.inputStream.bufferedReader().readText();c.disconnect();val root=JSONObject(raw);val arr=root.optJSONArray("segments")
        if(arr==null)return@withContext emptyList<TranscriptPiece>()
        buildList{for(i in 0 until arr.length()){val o=arr.getJSONObject(i);add(TranscriptPiece(chunk.startMs+o.optLong("start_ms"),chunk.startMs+o.optLong("end_ms"),o.optString("speaker","Speaker"),o.optString("text").trim()))}}
    }
}
