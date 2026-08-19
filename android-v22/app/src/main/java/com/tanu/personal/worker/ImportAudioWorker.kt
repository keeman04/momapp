package com.tanu.personal.worker

import android.content.Context
import android.media.*
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tanu.personal.audio.ChunkAccumulator
import com.tanu.personal.data.MeetingRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.nio.ByteOrder

@HiltWorker
class ImportAudioWorker @AssistedInject constructor(@Assisted appContext:Context,@Assisted params:WorkerParameters,private val repo:MeetingRepository):CoroutineWorker(appContext,params){
    override suspend fun doWork():Result{
        val meetingId=inputData.getString("meetingId")?:return Result.failure();val path=inputData.getString("path")?:return Result.failure();val file=File(path);if(!file.exists())return Result.failure()
        return try{val duration=decode(file,meetingId);repo.markStopped(meetingId,path,duration);Result.success()}catch(e:Exception){Result.failure()}
    }
    private suspend fun decode(file:File,meetingId:String):Long{
        val ex=MediaExtractor();ex.setDataSource(file.absolutePath);var track=-1;var format:MediaFormat?=null
        for(i in 0 until ex.trackCount){val f=ex.getTrackFormat(i);val mime=f.getString(MediaFormat.KEY_MIME);if(mime?.startsWith("audio/")==true){track=i;format=f;break}}
        require(track>=0&&format!=null){"No audio track"};ex.selectTrack(track);val mime=format!!.getString(MediaFormat.KEY_MIME)!!;val codec=MediaCodec.createDecoderByType(mime);codec.configure(format,null,null,0);codec.start()
        var channels=format!!.getInteger(MediaFormat.KEY_CHANNEL_COUNT);var rate=format!!.getInteger(MediaFormat.KEY_SAMPLE_RATE);val info=MediaCodec.BufferInfo();var inDone=false;var outDone=false
        val acc=ChunkAccumulator(File(applicationContext.filesDir,"chunks"),meetingId);var lastMs=0L
        while(!outDone){
            if(!inDone){val ix=codec.dequeueInputBuffer(10000);if(ix>=0){val ib=codec.getInputBuffer(ix)!!;val n=ex.readSampleData(ib,0);if(n<0){codec.queueInputBuffer(ix,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inDone=true}else{codec.queueInputBuffer(ix,0,n,ex.sampleTime,0);ex.advance()}}}
            val ox=codec.dequeueOutputBuffer(info,10000)
            if(ox==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){val o=codec.outputFormat;channels=o.getInteger(MediaFormat.KEY_CHANNEL_COUNT);rate=o.getInteger(MediaFormat.KEY_SAMPLE_RATE)}
            else if(ox>=0){val ob=codec.getOutputBuffer(ox);if(ob!=null&&info.size>0){ob.position(info.offset);ob.limit(info.offset+info.size);ob.order(ByteOrder.LITTLE_ENDIAN);val sb=ob.asShortBuffer();val mono=ShortArray(sb.remaining()/channels);var m=0;while(sb.remaining()>=channels){var sum=0;repeat(channels){sum+=sb.get().toInt()};mono[m++]=(sum/channels).toShort()};val pcm16=if(rate==16000)mono else resample(mono,rate);val bytes=ByteArray(pcm16.size*2);var j=0;pcm16.forEach{s->bytes[j++]=(s.toInt() and 0xff).toByte();bytes[j++]=((s.toInt() shr 8) and 0xff).toByte()};acc.add(bytes,bytes.size).forEach{c->repo.insertChunk(meetingId,c.index,c.startMs,c.endMs,c.file);lastMs=c.endMs}}
                codec.releaseOutputBuffer(ox,false);if(info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0)outDone=true}
        }
        acc.flush()?.let{c->repo.insertChunk(meetingId,c.index,c.startMs,c.endMs,c.file);lastMs=c.endMs};codec.stop();codec.release();ex.release();return lastMs
    }
    private fun resample(raw:ShortArray,rate:Int):ShortArray{val n=(raw.size.toLong()*16000/rate).toInt();val out=ShortArray(n);for(i in out.indices){val src=i*(rate/16000f);val a=src.toInt().coerceAtMost(raw.lastIndex);val b=(a+1).coerceAtMost(raw.lastIndex);val t=src-a;out[i]=(raw[a]*(1-t)+raw[b]*t).toInt().toShort()};return out}
}
