package com.tanu.personal.audio

import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

object PcmSpeechGate {
    fun hasLikelySpeech(file:File):Boolean {
        if(!file.exists() || file.length()<3200)return false
        val bytes=file.readBytes()
        var sum=0.0;var count=0;var peaks=0
        val samples=bytes.size/2
        val step=maxOf(1,samples/12000)
        var i=0
        while(i<samples){
            val j=i*2
            val v=((bytes[j].toInt() and 0xff) or (bytes[j+1].toInt() shl 8)).toShort().toInt()
            val a=abs(v);sum+=a.toDouble()*a;count++;if(a>900)peaks++;i+=step
        }
        val rms=sqrt(sum/maxOf(1,count))
        return rms>125 && peaks>4
    }
}
