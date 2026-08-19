package com.tanu.personal.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.tanu.personal.db.TanuDao
import com.tanu.personal.db.TanuDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun db(@ApplicationContext context: Context): TanuDatabase =
        Room.databaseBuilder(context, TanuDatabase::class.java, "tanu-personal.db").build()

    @Provides
    fun dao(db: TanuDatabase): TanuDao = db.dao()

    @Provides
    @Singleton
    fun work(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)
}
