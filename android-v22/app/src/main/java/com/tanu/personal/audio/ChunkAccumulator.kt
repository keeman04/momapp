package com.tanu.personal.audio

import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Emits PCM16 mono chunks with overlap. The overlap keeps words that cross
 * chunk boundaries from being lost. A retained overlap by itself is never
 * emitted as a duplicate tail chunk when recording stops.
 */
class ChunkAccumulator(
    private val dir: File,
    private val meetingId: String,
    private val seconds: Int = 20,
    private val overlapSeconds: Int = 1,
    private val sampleRate: Int = 16000
) {
    private val chunkBytes = seconds * sampleRate * 2
    private val overlapBytes = overlapSeconds.coerceAtMost(seconds - 1).coerceAtLeast(0) * sampleRate * 2
    private val advanceMs = (seconds - overlapSeconds.coerceAtMost(seconds - 1).coerceAtLeast(0)) * 1000L
    private val out = ByteArrayOutputStream(chunkBytes + 8192)
    var index = 0
        private set
    var totalBytes = 0L
        private set
    private var nextStartMs = 0L
    private var freshBytesSinceFlush = 0

    data class Chunk(val index: Int, val startMs: Long, val endMs: Long, val file: File)

    fun add(data: ByteArray, len: Int): List<Chunk> {
        require(len in 0..data.size) { "Invalid PCM length" }
        val result = mutableListOf<Chunk>()
        var off = 0
        while (off < len) {
            val n = minOf(chunkBytes - out.size(), len - off)
            out.write(data, off, n)
            off += n
            totalBytes += n
            freshBytesSinceFlush += n
            if (out.size() >= chunkBytes) result += flushInternal(keepOverlap = true)
        }
        return result
    }

    fun flush(): Chunk? {
        val minimumAudioBytes = sampleRate * 2
        if (out.size() < minimumAudioBytes) return null
        if (overlapBytes > 0 && freshBytesSinceFlush <= 0) return null
        return flushInternal(keepOverlap = false)
    }

    private fun flushInternal(keepOverlap: Boolean): Chunk {
        dir.mkdirs()
        val bytes = out.toByteArray()
        val start = nextStartMs
        val duration = bytes.size.toLong() / 2 * 1000L / sampleRate
        val i = index++
        val file = File(dir, "${meetingId}_${i.toString().padStart(5, '0')}.pcm")
        file.writeBytes(bytes)
        out.reset()
        freshBytesSinceFlush = 0
        if (keepOverlap && overlapBytes > 0) {
            val n = minOf(overlapBytes, bytes.size)
            out.write(bytes, bytes.size - n, n)
            nextStartMs += advanceMs
        } else {
            nextStartMs = start + duration
        }
        return Chunk(i, start, start + duration, file)
    }
}
