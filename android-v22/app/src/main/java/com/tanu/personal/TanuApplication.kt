package com.tanu.personal

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.tanu.personal.worker.BackgroundScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TanuApplication:Application(),Configuration.Provider{
    @Inject lateinit var workerFactory:HiltWorkerFactory
    override val workManagerConfiguration:Configuration get()=Configuration.Builder().setWorkerFactory(workerFactory).build()
    override fun onCreate(){super.onCreate();BackgroundScheduler.ensure(this)}
}
