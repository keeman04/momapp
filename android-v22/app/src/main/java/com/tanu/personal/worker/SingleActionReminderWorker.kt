package com.tanu.personal.worker

import android.app.*
import android.content.*
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tanu.personal.MainActivity
import com.tanu.personal.receiver.ActionReminderReceiver
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SingleActionReminderWorker @AssistedInject constructor(
    @Assisted appContext:Context,
    @Assisted params:WorkerParameters
):CoroutineWorker(appContext,params){
    override suspend fun doWork():Result{
        val actionId=inputData.getString("actionId")?:return Result.success()
        val meetingId=inputData.getString("meetingId").orEmpty()
        val title=inputData.getString("title").orEmpty()
        val owner=inputData.getString("owner").orEmpty()
        val due=inputData.getString("due").orEmpty()
        if(Build.VERSION.SDK_INT>=26)(applicationContext.getSystemService(NotificationManager::class.java)).createNotificationChannel(NotificationChannel(ActionReminderWorker.CHANNEL,"Action reminders",NotificationManager.IMPORTANCE_DEFAULT))
        val open=PendingIntent.getActivity(applicationContext,actionId.hashCode(),Intent(applicationContext,MainActivity::class.java).putExtra("open_meeting_id",meetingId),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val done=PendingIntent.getBroadcast(applicationContext,actionId.hashCode()+100,Intent(applicationContext,ActionReminderReceiver::class.java).setAction(ActionReminderReceiver.DONE).putExtra("actionId",actionId),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n=NotificationCompat.Builder(applicationContext,ActionReminderWorker.CHANNEL).setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle("TANU Snoozed Reminder").setContentText(title).setStyle(NotificationCompat.BigTextStyle().bigText("$title\n$owner${if(due.isBlank())"" else " · Due $due"}")).setContentIntent(open).setAutoCancel(true).addAction(0,"Done",done).build()
        (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(actionId.hashCode(),n)
        return Result.success()
    }
}
