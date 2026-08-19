package com.tanu.personal.worker

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object BackgroundScheduler {
    fun ensure(context: Context) {
        val work = WorkManager.getInstance(context)
        val retention = PeriodicWorkRequestBuilder<AudioCleanupWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        work.enqueueUniquePeriodicWork("tanu-audio-retention", ExistingPeriodicWorkPolicy.UPDATE, retention)

        val reminders = PeriodicWorkRequestBuilder<ActionReminderWorker>(12, TimeUnit.HOURS)
            .build()
        work.enqueueUniquePeriodicWork("tanu-action-reminders", ExistingPeriodicWorkPolicy.UPDATE, reminders)
    }
}
