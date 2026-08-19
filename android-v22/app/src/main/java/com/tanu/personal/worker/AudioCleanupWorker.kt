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
        val age = when (settings.retention) {
            "1d" -> 24L * 60 * 60 * 1000
            "7d" -> 7L * 24 * 60 * 60 * 1000
            "30d" -> 30L * 24 * 60 * 60 * 1000
            else -> return Result.success()
        }
        val cutoff = System.currentTimeMillis() - age
        listOf("audio", "imports").forEach { name ->
            val files = File(applicationContext.filesDir, name).listFiles() ?: emptyArray()
            files.filter { it.isFile && it.lastModified() < cutoff }.forEach { runCatching { it.delete() } }
        }
        return Result.success()
    }
}
