package com.tanu.personal.receiver

import android.content.*
import androidx.work.*
import com.tanu.personal.db.TanuDao
import com.tanu.personal.worker.SingleActionReminderWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class ActionReminderReceiver:BroadcastReceiver(){
    @Inject lateinit var dao:TanuDao
    override fun onReceive(context:Context,intent:Intent){
        val pending=goAsync()
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch{
            try{
                val id=intent.getStringExtra("actionId").orEmpty()
                when(intent.action){
                    DONE->if(id.isNotBlank())dao.setActionStatus(id,"done")
                    SNOOZE->{
                        val req=OneTimeWorkRequestBuilder<SingleActionReminderWorker>()
                            .setInitialDelay(1,TimeUnit.HOURS)
                            .setInputData(workDataOf("actionId" to id,"meetingId" to intent.getStringExtra("meetingId").orEmpty(),"title" to intent.getStringExtra("title").orEmpty(),"owner" to intent.getStringExtra("owner").orEmpty(),"due" to intent.getStringExtra("due").orEmpty()))
                            .build()
                        WorkManager.getInstance(context).enqueueUniqueWork("tanu-snooze-$id",ExistingWorkPolicy.REPLACE,req)
                    }
                }
            } finally { pending.finish() }
        }
    }
    companion object{const val DONE="com.tanu.personal.ACTION_DONE";const val SNOOZE="com.tanu.personal.ACTION_SNOOZE"}
}
