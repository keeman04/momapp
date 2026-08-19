package com.tanu.personal.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tanu.personal.MainActivity
import com.tanu.personal.db.TanuDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@HiltWorker
class ActionReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dao: TanuDao
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        createChannel()
        if (!canNotify()) return Result.success()
        val today = LocalDate.now()
        dao.openActionsList().filter { action ->
            parseDate(action.dueDate)?.let { !it.isAfter(today.plusDays(1)) } ?: false
        }.take(5).forEach { action ->
            val open = PendingIntent.getActivity(
                applicationContext,
                action.id.hashCode(),
                Intent(applicationContext, MainActivity::class.java)
                    .putExtra("open_meeting_id", action.meetingId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val notification = NotificationCompat.Builder(applicationContext, "tanu_actions")
                .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
                .setContentTitle("TANU action due")
                .setContentText(action.title.take(100))
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
            (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(action.id.hashCode(), notification)
        }
        return Result.success()
    }

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun parseDate(raw: String): LocalDate? {
        if (raw.isBlank()) return null
        val normalized = raw.trim().lowercase()
        if (normalized == "today") return LocalDate.now()
        if (normalized == "tomorrow") return LocalDate.now().plusDays(1)
        return listOf("yyyy-MM-dd", "dd/MM/yyyy", "dd-MM-yyyy", "d MMM yyyy", "d MMMM yyyy")
            .firstNotNullOfOrNull { pattern ->
                runCatching {
                    LocalDate.parse(raw.trim(), DateTimeFormatter.ofPattern(pattern, java.util.Locale.ENGLISH))
                }.getOrNull()
            }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel("tanu_actions", "TANU action reminders", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }
}
