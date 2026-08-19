package com.tanu.personal.worker

import android.app.*
import android.content.*
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tanu.personal.MainActivity
import com.tanu.personal.db.TanuDao
import com.tanu.personal.receiver.ActionReminderReceiver
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ActionReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dao: TanuDao
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        createChannel()
        dao.openActionsList().take(6).forEachIndexed { index, action ->
            notifyAction(action.id, action.meetingId, action.title, action.owner, action.dueDate, 4100 + index)
        }
        return Result.success()
    }

    private fun notifyAction(actionId:String, meetingId:String, title:String, owner:String, due:String, notificationId:Int) {
        val openIntent = Intent(applicationContext, MainActivity::class.java).putExtra("open_meeting_id", meetingId)
        val open = PendingIntent.getActivity(applicationContext, notificationId, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val done = PendingIntent.getBroadcast(applicationContext, notificationId + 100,
            Intent(applicationContext, ActionReminderReceiver::class.java).setAction(ActionReminderReceiver.DONE).putExtra("actionId", actionId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val snooze = PendingIntent.getBroadcast(applicationContext, notificationId + 200,
            Intent(applicationContext, ActionReminderReceiver::class.java).setAction(ActionReminderReceiver.SNOOZE).putExtra("actionId", actionId).putExtra("meetingId", meetingId).putExtra("title", title).putExtra("owner", owner).putExtra("due", due),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val detail = buildString { append(owner); if (due.isNotBlank()) append(" · Due ").append(due) }
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("TANU Reminder")
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$title\n$detail"))
            .setContentIntent(open).setAutoCancel(true)
            .addAction(0, "Done", done).addAction(0, "Snooze", snooze)
            .build()
        (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(actionId.hashCode(), n)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val m = applicationContext.getSystemService(NotificationManager::class.java)
            m.createNotificationChannel(NotificationChannel(CHANNEL, "Action reminders", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    companion object { const val CHANNEL = "tanu_actions" }
}
