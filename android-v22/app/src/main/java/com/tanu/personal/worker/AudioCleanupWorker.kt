package com.tanu.personal.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tanu.personal.data.SettingsStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

@HiltWorker
class AudioCleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val settings: SettingsStore
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()

        // PCM chunks are temporary work files, never user-retained audio. Anything this old
        // is orphaned by a crash/kill because normal finalization removes chunks within minutes.
        deleteOlderThan(File(applicationContext.filesDir, "chunks"), now - DAY_MS)
        File(applicationContext.filesDir, "models").listFiles()
            ?.filter { it.isFile && it.name.endsWith(".part") && it.lastModified() < now - DAY_MS }
            ?.forEach { runCatching { it.delete() } }

        val age = when (settings.retention) {
            "1d" -> DAY_MS
            "7d" -> 7L * DAY_MS
            "30d" -> 30L * DAY_MS
            else -> return Result.success()
        }
        val cutoff = now - age
        deleteOlderThan(File(applicationContext.filesDir, "audio"), cutoff)
        deleteOlderThan(File(applicationContext.filesDir, "imports"), cutoff)
        return Result.success()
    }

    private fun deleteOlderThan(dir: File, cutoff: Long) {
        val files = dir.listFiles() ?: return
        files.filter { it.isFile && it.lastModified() < cutoff }
            .forEach { runCatching { it.delete() } }
    }

    companion object {
        private const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
