package com.kgconsol.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kgconsol.data.local.dao.BatchDao
import com.kgconsol.data.local.dao.BoxDao
import com.kgconsol.data.local.dao.OrderDao
import com.kgconsol.data.local.entity.BatchEntity
import com.kgconsol.data.local.entity.BoxEntity
import com.kgconsol.data.local.entity.OrderEntity

@Database(
    entities = [BatchEntity::class, BoxEntity::class, OrderEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun batchDao(): BatchDao
    abstract fun boxDao(): BoxDao
    abstract fun orderDao(): OrderDao

    companion object {
        const val DATABASE_NAME = "kgconsol.db"
    }
}
