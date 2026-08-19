package com.tanu.personal.transcription

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeWhisper @Inject constructor(@ApplicationContext private val context:Context){
    companion object{init{System.loadLibrary("tanu-whisper")}; @JvmStatic external fun nativeInitModel(assets:android.content.res.AssetManager,name:String):Long; @JvmStatic external fun nativeTranscribe(handle:Long,pcm:ShortArray,prompt:String):String; @JvmStatic external fun nativeFree(handle:Long)}
    @Volatile private var handle:Long=0
    @Synchronized private fun ensure():Long{if(handle==0L)handle=nativeInitModel(context.assets,"models/ggml-tiny-q5_1.bin");return handle}
    @Synchronized fun transcribe(file:File,prompt:String):String{
        val bytes=file.readBytes();val shorts=ShortArray(bytes.size/2);var j=0;for(i in shorts.indices){shorts[i]=((bytes[j].toInt() and 0xff) or (bytes[j+1].toInt() shl 8)).toShort();j+=2}
        if(!hasSpeech(shorts))return ""
        val h=ensure();if(h==0L)throw IllegalStateException("Offline speech model could not load")
        return nativeTranscribe(h,shorts,prompt).trim()
    }
    private fun hasSpeech(a:ShortArray):Boolean{if(a.isEmpty())return false;var sum=0.0;var peaks=0;val step=maxOf(1,a.size/12000);var n=0;for(i in a.indices step step){val v=kotlin.math.abs(a[i].toInt());sum+=v.toDouble()*v;n++;if(v>1000)peaks++};val rms=kotlin.math.sqrt(sum/maxOf(1,n));return rms>140 && peaks>4}
}
