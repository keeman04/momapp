package com.tanu.personal.service

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.media.*
import android.media.audiofx.*
import android.os.*
import androidx.core.app.NotificationCompat
import com.tanu.personal.MainActivity
import com.tanu.personal.audio.*
import com.tanu.personal.data.MeetingRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class RecordingService : Service() {
    @Inject lateinit var repo: MeetingRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private var encoder: AacM4aEncoder? = null
    private var acc: ChunkAccumulator? = null
    private var chunkQueue: Channel<ChunkAccumulator.Chunk>? = null
    private var chunkJob: Job? = null
    private var wake: PowerManager.WakeLock? = null
    private var ns: NoiseSuppressor? = null
    private var aec: AcousticEchoCanceler? = null
    private var agc: AutomaticGainControl? = null
    private var meetingId = ""
    private var title = ""
    private var started = 0L

    @Volatile private var running = false
    @Volatile private var paused = false

    companion object {
        const val ACTION_START = "com.tanu.personal.START"
        const val ACTION_STOP = "com.tanu.personal.STOP"
        const val ACTION_PAUSE = "com.tanu.personal.PAUSE"
        const val ACTION_RESUME = "com.tanu.personal.RESUME"
        const val EXTRA_ID = "meetingId"
        const val EXTRA_TITLE = "title"

        @Volatile var activeMeetingId: String? = null
        @Volatile var isRecording = false
        @Volatile var isPaused = false
        @Volatile var startedAt = 0L
        @Volatile private var pausedAt = 0L
        @Volatile private var totalPausedMs = 0L

        fun elapsedMs(now: Long = System.currentTimeMillis()): Long {
            if (startedAt <= 0L) return 0L
            val activePause = if (isPaused && pausedAt > 0L) now - pausedAt else 0L
            return (now - startedAt - totalPausedMs - activePause).coerceAtLeast(0L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> if (!running) {
                startCapture(intent.getStringExtra(EXTRA_ID).orEmpty(), intent.getStringExtra(EXTRA_TITLE).orEmpty())
            }
            ACTION_STOP -> stopCapture()
            ACTION_PAUSE -> if (running && !paused) {
                paused = true
                isPaused = true
                pausedAt = System.currentTimeMillis()
            }
            ACTION_RESUME -> if (running && paused) {
                val now = System.currentTimeMillis()
                if (pausedAt > 0L) totalPausedMs += now - pausedAt
                pausedAt = 0L
                paused = false
                isPaused = false
            }
        }
        return START_NOT_STICKY
    }

    private fun startCapture(id: String, t: String) {
        if (id.isBlank()) {
            stopSelf()
            return
        }
        meetingId = id
        title = t.ifBlank { "Meeting" }
        started = System.currentTimeMillis()
        startedAt = started
        pausedAt = 0L
        totalPausedMs = 0L
        activeMeetingId = id

        try {
            val min = AudioRecord.getMinBufferSize(
                16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            require(min > 0) { "Microphone buffer is unavailable" }
            val r = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(min, 8192)
            )
            require(r.state == AudioRecord.STATE_INITIALIZED) { "Microphone could not be initialized" }
            record = r

            ns = if (NoiseSuppressor.isAvailable()) runCatching { NoiseSuppressor.create(r.audioSessionId) }.getOrNull() else null
            aec = if (AcousticEchoCanceler.isAvailable()) runCatching { AcousticEchoCanceler.create(r.audioSessionId) }.getOrNull() else null
            agc = if (AutomaticGainControl.isAvailable()) runCatching { AutomaticGainControl.create(r.audioSessionId) }.getOrNull() else null
            runCatching { ns?.enabled = true }
            runCatching { aec?.enabled = true }
            runCatching { agc?.enabled = true }

            val audioDir = File(filesDir, "audio").apply { mkdirs() }
            val chunkDir = File(filesDir, "chunks").apply { mkdirs() }
            val master = File(audioDir, "$id.m4a")
            encoder = AacM4aEncoder(master)
            acc = ChunkAccumulator(chunkDir, id)
            chunkQueue = Channel(Channel.UNLIMITED)
            chunkJob = scope.launch {
                val q = chunkQueue ?: return@launch
                for (c in q) repo.insertChunk(meetingId, c.index, c.startMs, c.endMs, c.file)
            }

            val notification = notification(title)
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(2201, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(2201, notification)
            }

            wake = (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TANU::Recording")
                .apply {
                    setReferenceCounted(false)
                    acquire()
                }

            r.startRecording()
            require(r.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Microphone did not start recording" }
            running = true
            isRecording = true

            thread = Thread {
                val buf = ByteArray(8192)
                while (running) {
                    val n = runCatching { r.read(buf, 0, buf.size) }.getOrElse { break }
                    if (n > 0 && !paused) {
                        encoder?.write(buf, n)
                        acc?.add(buf, n)?.forEach { c -> chunkQueue?.trySend(c) }
                    } else if (n < 0 && n != AudioRecord.ERROR_INVALID_OPERATION) {
                        Thread.yield()
                    }
                }
            }.apply {
                name = "TANU-AudioCapture"
                start()
            }
        } catch (e: Exception) {
            cleanupCaptureResources(deleteMaster = true)
            activeMeetingId = null
            isRecording = false
            isPaused = false
            startedAt = 0L
            pausedAt = 0L
            totalPausedMs = 0L
            runBlocking { repo.markFailed(id, e.message ?: "Microphone could not start") }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopCapture() {
        if (!running) {
            stopSelf()
            return
        }
        val stoppedAt = System.currentTimeMillis()
        if (paused && pausedAt > 0L) {
            totalPausedMs += stoppedAt - pausedAt
            pausedAt = 0L
        }
        paused = false
        isPaused = false
        running = false

        // Stop first so a blocking AudioRecord.read() is released, then join the capture thread.
        runCatching { record?.stop() }
        runCatching { thread?.join(2500) }
        thread = null
        runCatching { record?.release() }
        record = null

        acc?.flush()?.let { c -> chunkQueue?.trySend(c) }
        chunkQueue?.close()
        runBlocking { chunkJob?.join() }
        chunkJob = null
        chunkQueue = null

        val master = File(filesDir, "audio/$meetingId.m4a")
        runCatching { encoder?.close() }
        encoder = null
        acc = null
        releaseEffectsAndWakeLock()

        val duration = (stoppedAt - started - totalPausedMs).coerceAtLeast(0L)
        runBlocking { repo.markStopped(meetingId, master.absolutePath, duration) }

        isRecording = false
        activeMeetingId = null
        startedAt = 0L
        pausedAt = 0L
        totalPausedMs = 0L
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanupCaptureResources(deleteMaster: Boolean) {
        running = false
        runCatching { record?.stop() }
        runCatching { thread?.join(1000) }
        thread = null
        runCatching { record?.release() }
        record = null
        chunkQueue?.close()
        chunkQueue = null
        chunkJob?.cancel()
        chunkJob = null
        runCatching { encoder?.close() }
        encoder = null
        acc = null
        releaseEffectsAndWakeLock()
        if (deleteMaster && meetingId.isNotBlank()) runCatching { File(filesDir, "audio/$meetingId.m4a").delete() }
    }

    private fun releaseEffectsAndWakeLock() {
        runCatching { ns?.release() }
        runCatching { aec?.release() }
        runCatching { agc?.release() }
        ns = null
        aec = null
        agc = null
        wake?.let { if (it.isHeld) runCatching { it.release() } }
        wake = null
    }

    override fun onDestroy() {
        if (running) stopCapture() else cleanupCaptureResources(deleteMaster = false)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val c = NotificationChannel("tanu_recording", "Meeting recording", NotificationManager.IMPORTANCE_LOW)
            c.setSound(null, null)
            getSystemService(NotificationManager::class.java).createNotificationChannel(c)
        }
    }

    private fun notification(t: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, "tanu_recording")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("TANU is recording")
            .setContentText(t)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }
}
