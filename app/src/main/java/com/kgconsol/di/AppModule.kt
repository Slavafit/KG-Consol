package com.kgconsol.di

import android.content.Context
import androidx.room.Room
import com.kgconsol.data.local.AppDatabase
import com.kgconsol.data.local.dao.BatchDao
import com.kgconsol.data.local.dao.BoxDao
import com.kgconsol.data.local.dao.OrderDao
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
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideBatchDao(db: AppDatabase): BatchDao = db.batchDao()
    @Provides fun provideBoxDao(db: AppDatabase): BoxDao = db.boxDao()
    @Provides fun provideOrderDao(db: AppDatabase): OrderDao = db.orderDao()
}
