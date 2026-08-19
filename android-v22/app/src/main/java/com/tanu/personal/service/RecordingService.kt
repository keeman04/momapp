package com.tanu.personal.service

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.media.*
import android.media.audiofx.*
import android.os.*
import androidx.core.app.NotificationCompat
import com.tanu.personal.MainActivity
import com.tanu.personal.R
import com.tanu.personal.audio.*
import com.tanu.personal.data.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class RecordingService:Service(){
    @Inject lateinit var repo:MeetingRepository
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private var record:AudioRecord?=null; private var thread:Thread?=null; private var encoder:AacM4aEncoder?=null; private var acc:ChunkAccumulator?=null
    private var chunkQueue:Channel<ChunkAccumulator.Chunk>?=null; private var chunkJob:Job?=null
    private var wake:PowerManager.WakeLock?=null; private var ns:NoiseSuppressor?=null; private var aec:AcousticEchoCanceler?=null; private var agc:AutomaticGainControl?=null
    private var meetingId=""; private var title=""; private var started=0L; @Volatile private var running=false; @Volatile private var paused=false
    companion object{
        const val ACTION_START="com.tanu.personal.START"
        const val ACTION_STOP="com.tanu.personal.STOP"
        const val ACTION_PAUSE="com.tanu.personal.PAUSE"
        const val ACTION_RESUME="com.tanu.personal.RESUME"
        const val EXTRA_ID="meetingId"
        const val EXTRA_TITLE="title"
        @Volatile var activeMeetingId:String?=null; @Volatile var isRecording=false; @Volatile var isPaused=false; @Volatile var startedAt=0L
    }
    override fun onCreate(){super.onCreate();createChannel()}
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
        when(intent?.action){
            ACTION_START->if(!running) startCapture(intent.getStringExtra(EXTRA_ID).orEmpty(),intent.getStringExtra(EXTRA_TITLE).orEmpty())
            ACTION_STOP->stopCapture(); ACTION_PAUSE->{paused=true;isPaused=true}; ACTION_RESUME->{paused=false;isPaused=false}
        };return START_NOT_STICKY
    }
    private fun startCapture(id:String,t:String){
        meetingId=id;title=t.ifBlank{"Meeting"};started=System.currentTimeMillis();startedAt=started;activeMeetingId=id
        val min=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
        val r=AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,maxOf(min,8192))
        record=r
        if(NoiseSuppressor.isAvailable())ns=NoiseSuppressor.create(r.audioSessionId)
        if(AcousticEchoCanceler.isAvailable())aec=AcousticEchoCanceler.create(r.audioSessionId)
        if(AutomaticGainControl.isAvailable())agc=AutomaticGainControl.create(r.audioSessionId)
        ns?.enabled=true;aec?.enabled=true;agc?.enabled=true
        val audioDir=File(filesDir,"audio").apply{mkdirs()};val chunkDir=File(filesDir,"chunks").apply{mkdirs()}
        val master=File(audioDir,"$id.m4a");encoder=AacM4aEncoder(master);acc=ChunkAccumulator(chunkDir,id)
        chunkQueue=Channel(Channel.UNLIMITED)
        chunkJob=scope.launch{val q=chunkQueue?:return@launch;for(c in q)repo.insertChunk(meetingId,c.index,c.startMs,c.endMs,c.file)}
        val notification=notification(title)
        if(Build.VERSION.SDK_INT>=29)startForeground(2201,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE) else startForeground(2201,notification)
        wake=(getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"TANU::Recording").apply{setReferenceCounted(false);acquire()}
        running=true;isRecording=true;r.startRecording()
        thread=Thread{
            val buf=ByteArray(8192)
            while(running){
                if(paused){Thread.sleep(50);continue}
                val n=r.read(buf,0,buf.size)
                if(n>0){
                    encoder?.write(buf,n)
                    acc?.add(buf,n)?.forEach{c->chunkQueue?.trySend(c)}
                }
            }
        }.apply{name="TANU-AudioCapture";start()}
    }
    private fun stopCapture(){
        if(!running){stopSelf();return};running=false;runCatching{thread?.join(1500)};thread=null
        runCatching{record?.stop()};runCatching{record?.release()};record=null
        acc?.flush()?.let{c->chunkQueue?.trySend(c)}
        chunkQueue?.close();runBlocking{chunkJob?.join()};chunkJob=null;chunkQueue=null
        val master=File(filesDir,"audio/$meetingId.m4a");runCatching{encoder?.close()};encoder=null
        ns?.release();aec?.release();agc?.release();ns=null;aec=null;agc=null
        val duration=System.currentTimeMillis()-started
        runBlocking{repo.markStopped(meetingId,master.absolutePath,duration)}
        isRecording=false;isPaused=false;activeMeetingId=null;startedAt=0;wake?.let{if(it.isHeld)it.release()};wake=null
        stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()
    }
    override fun onDestroy(){if(running)stopCapture();scope.cancel();super.onDestroy()}
    override fun onBind(intent:Intent?)=null
    private fun createChannel(){if(Build.VERSION.SDK_INT>=26){val c=NotificationChannel("tanu_recording","Meeting recording",NotificationManager.IMPORTANCE_LOW);c.setSound(null,null);getSystemService(NotificationManager::class.java).createNotificationChannel(c)}}
    private fun notification(t:String):Notification{
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this,"tanu_recording").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("TANU is recording").setContentText("$t · rolling transcription is active").setOngoing(true).setContentIntent(open).build()
    }
}
