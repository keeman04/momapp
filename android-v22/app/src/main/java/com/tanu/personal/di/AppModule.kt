package com.tanu.personal.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.tanu.personal.db.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun db(@ApplicationContext c:Context)=Room.databaseBuilder(c,TanuDatabase::class.java,"tanu-personal.db").fallbackToDestructiveMigration().build()
    @Provides fun dao(db:TanuDatabase)=db.dao()
    @Provides @Singleton fun work(@ApplicationContext c:Context)=WorkManager.getInstance(c)
}
