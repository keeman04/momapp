package com.tanu.personal.worker

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
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
class ImportAudioWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repo: MeetingRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val meetingId = inputData.getString("meetingId") ?: return Result.failure()
        val path = inputData.getString("path") ?: return Result.failure()
        val file = File(path)
        if (!file.exists() || file.length() == 0L) {
            repo.markFailed(meetingId, "The selected audio file could not be opened.")
            return Result.failure()
        }

        return try {
            val duration = decode(file, meetingId)
            repo.markStopped(meetingId, path, duration)
            Result.success()
        } catch (e: Exception) {
            repo.markFailed(meetingId, e.message?.take(200) ?: "This audio format could not be imported.")
            repo.cleanupChunkFiles(meetingId)
            Result.failure()
        }
    }

    private suspend fun decode(file: File, meetingId: String): Long {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(i)
                val mime = candidate.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    track = i
                    format = candidate
                    break
                }
            }
            require(track >= 0 && format != null) { "No supported audio track was found." }
            extractor.selectTrack(track)

            val inputFormat = format!!
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)!!
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            var channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            var rate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(8000)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            val acc = ChunkAccumulator(File(applicationContext.filesDir, "chunks"), meetingId)
            var lastMs = 0L

            while (!outputDone && !isStopped) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val input = codec.getInputBuffer(inputIndex) ?: error("Audio decoder input buffer unavailable")
                        val n = extractor.readSampleData(input, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(info, 10_000)
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val outputFormat = codec.outputFormat
                    channels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                    rate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(8000)
                } else if (outputIndex >= 0) {
                    val output = codec.getOutputBuffer(outputIndex)
                    if (output != null && info.size > 0) {
                        output.position(info.offset)
                        output.limit(info.offset + info.size)
                        output.order(ByteOrder.LITTLE_ENDIAN)
                        val shorts = output.asShortBuffer()
                        val mono = ShortArray(shorts.remaining() / channels)
                        var m = 0
                        while (shorts.remaining() >= channels) {
                            var sum = 0
                            repeat(channels) { sum += shorts.get().toInt() }
                            mono[m++] = (sum / channels).toShort()
                        }
                        val pcm16 = if (rate == 16000) mono else resample(mono, rate)
                        val bytes = ByteArray(pcm16.size * 2)
                        var j = 0
                        pcm16.forEach { sample ->
                            bytes[j++] = (sample.toInt() and 0xff).toByte()
                            bytes[j++] = ((sample.toInt() shr 8) and 0xff).toByte()
                        }
                        acc.add(bytes, bytes.size).forEach { chunk ->
                            repo.insertChunk(meetingId, chunk.index, chunk.startMs, chunk.endMs, chunk.file)
                            lastMs = chunk.endMs
                        }
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }

            if (isStopped) throw IllegalStateException("Audio import was cancelled")
            acc.flush()?.let { chunk ->
                repo.insertChunk(meetingId, chunk.index, chunk.startMs, chunk.endMs, chunk.file)
                lastMs = chunk.endMs
            }
            require(lastMs > 0L) { "No usable audio was decoded." }
            return lastMs
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun resample(raw: ShortArray, rate: Int): ShortArray {
        if (raw.isEmpty()) return raw
        val n = (raw.size.toLong() * 16000 / rate).toInt().coerceAtLeast(1)
        val out = ShortArray(n)
        for (i in out.indices) {
            val src = i * (rate / 16000f)
            val a = src.toInt().coerceIn(0, raw.lastIndex)
            val b = (a + 1).coerceAtMost(raw.lastIndex)
            val t = src - a
            out[i] = (raw[a] * (1 - t) + raw[b] * t).toInt().toShort()
        }
        return out
    }
}
