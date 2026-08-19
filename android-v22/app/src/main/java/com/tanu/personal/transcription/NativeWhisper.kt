package com.tanu.personal.transcription

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeWhisper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        init { System.loadLibrary("tanu-whisper") }

        @JvmStatic external fun nativeInitModel(assets: android.content.res.AssetManager, name: String): Long
        @JvmStatic external fun nativeTranscribe(handle: Long, pcm: ShortArray, prompt: String): String
        @JvmStatic external fun nativeFree(handle: Long)
    }

    @Volatile private var handle: Long = 0

    @Synchronized
    private fun ensure(): Long {
        if (handle == 0L) handle = nativeInitModel(context.assets, "models/ggml-tiny-q5_1.bin")
        return handle
    }

    @Synchronized
    fun transcribe(file: File, prompt: String): String {
        require(file.exists() && file.length() >= 2L) { "PCM chunk is missing or empty" }
        val bytes = file.readBytes()
        require(bytes.size % 2 == 0) { "PCM chunk has an invalid byte length" }
        val shorts = ShortArray(bytes.size / 2)
        var j = 0
        for (i in shorts.indices) {
            shorts[i] = ((bytes[j].toInt() and 0xff) or (bytes[j + 1].toInt() shl 8)).toShort()
            j += 2
        }
        val model = ensure()
        if (model == 0L) throw IllegalStateException("Offline speech model could not load")
        return nativeTranscribe(model, shorts, prompt).trim()
    }
}
