package com.tanu.personal.audio

import android.media.*
import java.io.File
import java.nio.ByteBuffer

class AacM4aEncoder(private val file:File, private val sampleRate:Int=16000, private val channels:Int=1, private val bitRate:Int=24000){
    private val codec=MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    private val muxer=MediaMuxer(file.absolutePath,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private var track=-1; private var muxStarted=false; private var samples=0L
    init{
        val f=MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC,sampleRate,channels)
        f.setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        f.setInteger(MediaFormat.KEY_BIT_RATE,bitRate)
        f.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,16384)
        codec.configure(f,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE); codec.start()
    }
    fun write(pcm:ByteArray,length:Int){
        var off=0
        while(off<length){
            val idx=codec.dequeueInputBuffer(10000)
            if(idx>=0){
                val b=codec.getInputBuffer(idx)?:continue; b.clear(); val n=minOf(b.remaining(),length-off); b.put(pcm,off,n)
                val pts=samples*1_000_000L/sampleRate; samples+=n/2/channels
                codec.queueInputBuffer(idx,0,n,pts,0); off+=n
            }
            drain(false)
        }
    }
    private fun drain(end:Boolean){
        val info=MediaCodec.BufferInfo()
        while(true){
            val out=codec.dequeueOutputBuffer(info,if(end)10000 else 0)
            when{
                out==MediaCodec.INFO_TRY_AGAIN_LATER -> return
                out==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { if(!muxStarted){track=muxer.addTrack(codec.outputFormat);muxer.start();muxStarted=true} }
                out>=0 -> {
                    val b=codec.getOutputBuffer(out)
                    if(b!=null && info.size>0 && muxStarted){ b.position(info.offset);b.limit(info.offset+info.size);muxer.writeSampleData(track,b,info) }
                    val eos=(info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0
                    codec.releaseOutputBuffer(out,false); if(eos)return
                }
                else -> return
            }
        }
    }
    fun close(){
        runCatching{
            val idx=codec.dequeueInputBuffer(10000); if(idx>=0) codec.queueInputBuffer(idx,0,0,samples*1_000_000L/sampleRate,MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            drain(true)
        }
        runCatching{codec.stop()};runCatching{codec.release()};if(muxStarted){runCatching{muxer.stop()}};runCatching{muxer.release()}
    }
}
