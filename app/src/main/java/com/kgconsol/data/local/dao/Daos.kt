package com.kgconsol.data.local.dao

import androidx.room.*
import com.kgconsol.data.local.entity.BatchEntity
import com.kgconsol.data.local.entity.BoxEntity
import com.kgconsol.data.local.entity.OrderEntity
import kotlinx.coroutines.flow.Flow

// ─────────────────────────────────────────────────────────────────────────────
// BatchDao
// ─────────────────────────────────────────────────────────────────────────────
@Dao
interface BatchDao {

    @Query("SELECT * FROM batches ORDER BY number DESC")
    fun getAllBatches(): Flow<List<BatchEntity>>

    @Query("SELECT * FROM batches WHERE id = :id")
    suspend fun getBatchById(id: Long): BatchEntity?

    @Query("SELECT MAX(number) FROM batches")
    suspend fun getMaxBatchNumber(): Int?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBatch(batch: BatchEntity): Long

    @Delete
    suspend fun deleteBatch(batch: BatchEntity)

    @Query("SELECT COUNT(*) FROM batches")
    suspend fun getBatchCount(): Int
}

// ─────────────────────────────────────────────────────────────────────────────
// BoxDao
// ─────────────────────────────────────────────────────────────────────────────
@Dao
interface BoxDao {

    @Query("SELECT * FROM boxes WHERE batchId = :batchId ORDER BY createdAt ASC")
    fun getBoxesForBatch(batchId: Long): Flow<List<BoxEntity>>

    @Query("SELECT * FROM boxes WHERE id = :id")
    suspend fun getBoxById(id: Long): BoxEntity?

    /** Latest box in a batch — used to suggest next auto-number */
    @Query("""
        SELECT * FROM boxes 
        WHERE batchId = :batchId AND isAutoNumber = 1 
        ORDER BY createdAt DESC LIMIT 1
    """)
    suspend fun getLastAutoBox(batchId: Long): BoxEntity?

    /** Last open (non-completed) box for a batch */
    @Query("""
        SELECT * FROM boxes 
        WHERE batchId = :batchId AND isCompleted = 0 
        ORDER BY createdAt DESC LIMIT 1
    """)
    suspend fun getOpenBox(batchId: Long): BoxEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBox(box: BoxEntity): Long

    @Update
    suspend fun updateBox(box: BoxEntity)

    @Delete
    suspend fun deleteBox(box: BoxEntity)

    @Query("SELECT COUNT(*) FROM boxes WHERE batchId = :batchId")
    suspend fun getBoxCountForBatch(batchId: Long): Int
}

// ─────────────────────────────────────────────────────────────────────────────
// OrderDao
// ─────────────────────────────────────────────────────────────────────────────
@Dao
interface OrderDao {

    @Query("SELECT * FROM orders WHERE boxId = :boxId ORDER BY scannedAt ASC")
    fun getOrdersForBox(boxId: Long): Flow<List<OrderEntity>>

    @Query("SELECT COUNT(*) FROM orders WHERE boxId = :boxId")
    suspend fun getOrderCountForBox(boxId: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM orders WHERE boxId = :boxId AND orderNumber = :orderNumber)")
    suspend fun orderExistsInBox(boxId: Long, orderNumber: String): Boolean

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOrder(order: OrderEntity): Long

    @Delete
    suspend fun deleteOrder(order: OrderEntity)

    @Query("DELETE FROM orders WHERE id = :id")
    suspend fun deleteOrderById(id: Long)

    /** All orders grouped by box, for batch report */
    @Query("""
        SELECT o.* FROM orders o
        INNER JOIN boxes b ON o.boxId = b.id
        WHERE b.batchId = :batchId
        ORDER BY b.createdAt ASC, o.scannedAt ASC
    """)
    fun getOrdersForBatch(batchId: Long): Flow<List<OrderEntity>>

    @Query("SELECT COUNT(*) FROM orders WHERE boxId IN (SELECT id FROM boxes WHERE batchId = :batchId)")
    suspend fun getTotalOrdersForBatch(batchId: Long): Int
}
