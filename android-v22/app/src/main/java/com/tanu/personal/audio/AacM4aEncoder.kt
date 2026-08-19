package com.tanu.personal.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File

class AacM4aEncoder(
    private val file: File,
    private val sampleRate: Int = 16000,
    private val channels: Int = 1,
    private val bitRate: Int = 24000
) {
    private val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    private val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private var track = -1
    private var muxStarted = false
    private var samples = 0L
    private var closed = false

    init {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels)
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
    }

    fun write(pcm: ByteArray, length: Int) {
        check(!closed) { "AAC encoder is already closed" }
        require(length in 0..pcm.size) { "Invalid PCM length" }
        var off = 0
        while (off < length) {
            val index = codec.dequeueInputBuffer(10_000)
            if (index >= 0) {
                val input = codec.getInputBuffer(index)
                    ?: throw IllegalStateException("AAC input buffer unavailable")
                input.clear()
                val n = minOf(input.remaining(), length - off)
                input.put(pcm, off, n)
                val pts = samples * 1_000_000L / sampleRate
                samples += n / 2 / channels
                codec.queueInputBuffer(index, 0, n, pts, 0)
                off += n
            }
            drain(waitForEos = false)
        }
    }

    private fun drain(waitForEos: Boolean): Boolean {
        val info = MediaCodec.BufferInfo()
        val deadline = if (waitForEos) System.nanoTime() + 2_000_000_000L else 0L
        while (true) {
            val output = codec.dequeueOutputBuffer(info, if (waitForEos) 10_000 else 0)
            when {
                output == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!waitForEos || System.nanoTime() >= deadline) return false
                }
                output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxStarted) {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxStarted = true
                    }
                }
                output >= 0 -> {
                    val buffer = codec.getOutputBuffer(output)
                    if (buffer != null && info.size > 0 && muxStarted) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        muxer.writeSampleData(track, buffer, info)
                    }
                    val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(output, false)
                    if (eos) return true
                }
                else -> if (!waitForEos) return false
            }
        }
    }

    fun close() {
        if (closed) return
        closed = true
        runCatching {
            val deadline = System.nanoTime() + 1_000_000_000L
            var eosQueued = false
            while (!eosQueued && System.nanoTime() < deadline) {
                val index = codec.dequeueInputBuffer(10_000)
                if (index >= 0) {
                    codec.queueInputBuffer(
                        index,
                        0,
                        0,
                        samples * 1_000_000L / sampleRate,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                    eosQueued = true
                } else {
                    drain(waitForEos = false)
                }
            }
            if (eosQueued) drain(waitForEos = true) else drain(waitForEos = false)
        }
        runCatching { codec.stop() }
        runCatching { codec.release() }
        if (muxStarted) runCatching { muxer.stop() }
        runCatching { muxer.release() }
    }
}
