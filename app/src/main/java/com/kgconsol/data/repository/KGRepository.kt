package com.kgconsol.data.repository

import com.kgconsol.data.local.dao.BatchDao
import com.kgconsol.data.local.dao.BoxDao
import com.kgconsol.data.local.dao.OrderDao
import com.kgconsol.data.local.entity.BatchEntity
import com.kgconsol.data.local.entity.BoxEntity
import com.kgconsol.data.local.entity.OrderEntity
import com.kgconsol.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

sealed class RepoResult<out T> {
    data class Success<T>(val data: T) : RepoResult<T>()
    data class Error(val message: String) : RepoResult<Nothing>()
}

@Singleton
class KGRepository @Inject constructor(
    private val batchDao: BatchDao,
    private val boxDao: BoxDao,
    private val orderDao: OrderDao
) {

    // ── Batches ──────────────────────────────────────────────────────────────

    fun getAllBatches(): Flow<List<Batch>> = batchDao.getAllBatches().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun suggestNextBatchNumber(): Int {
        val max = batchDao.getMaxBatchNumber() ?: 0
        return max + 1
    }

    suspend fun createBatch(number: Int): RepoResult<Long> {
        return try {
            val id = batchDao.insertBatch(BatchEntity(number = number))
            RepoResult.Success(id)
        } catch (e: Exception) {
            RepoResult.Error("Batch KG$number already exists or invalid")
        }
    }

    suspend fun getBatch(id: Long): Batch? = batchDao.getBatchById(id)?.toDomain()

    // ── Boxes ─────────────────────────────────────────────────────────────────

    fun getBoxesForBatch(batchId: Long): Flow<List<Box>> =
        boxDao.getBoxesForBatch(batchId).map { list ->
            list.map { it.toDomain() }
        }

    /** Suggest next auto-number for a batch */
    suspend fun suggestNextBoxNumber(batchId: Long): String {
        val last = boxDao.getLastAutoBox(batchId)
        return BoxNumberHelper.nextAutoNumber(last?.boxNumber)
    }

    suspend fun createBox(
        batchId: Long,
        boxNumber: String,
        isAuto: Boolean
    ): RepoResult<Long> {
        if (!isAuto && !BoxNumberHelper.isValidManual(boxNumber)) {
            return RepoResult.Error("Box number must be exactly 5 digits")
        }
        return try {
            val id = boxDao.insertBox(
                BoxEntity(batchId = batchId, boxNumber = boxNumber, isAutoNumber = isAuto)
            )
            RepoResult.Success(id)
        } catch (e: Exception) {
            RepoResult.Error("Failed to create box: ${e.message}")
        }
    }

    suspend fun getBox(id: Long): Box? = boxDao.getBoxById(id)?.toDomain()

    suspend fun getOpenBox(batchId: Long): Box? = boxDao.getOpenBox(batchId)?.toDomain()

    suspend fun completeBox(boxId: Long): RepoResult<Unit> {
        val box = boxDao.getBoxById(boxId)
            ?: return RepoResult.Error("Box not found")
        boxDao.updateBox(box.copy(isCompleted = true))
        return RepoResult.Success(Unit)
    }

    // ── Orders ────────────────────────────────────────────────────────────────

    fun getOrdersForBox(boxId: Long): Flow<List<Order>> =
        orderDao.getOrdersForBox(boxId).map { list -> list.map { it.toDomain() } }

    fun getOrdersForBatch(batchId: Long): Flow<List<Order>> =
        orderDao.getOrdersForBatch(batchId).map { list -> list.map { it.toDomain() } }

    suspend fun addOrder(boxId: Long, orderNumber: String): RepoResult<Long> {
        val normalized = OrderValidator.normalize(orderNumber)

        if (!OrderValidator.isValid(normalized)) {
            return RepoResult.Error("Invalid order format. Expected: 01-2345-6789")
        }
        if (orderDao.orderExistsInBox(boxId, normalized)) {
            return RepoResult.Error("Order $normalized already added to this box")
        }
        return try {
            val id = orderDao.insertOrder(
                OrderEntity(boxId = boxId, orderNumber = normalized)
            )
            RepoResult.Success(id)
        } catch (e: Exception) {
            RepoResult.Error("Failed to add order: ${e.message}")
        }
    }

    suspend fun deleteOrder(orderId: Long) {
        orderDao.deleteOrderById(orderId)
    }

    suspend fun getOrderCountForBox(boxId: Long): Int =
        orderDao.getOrderCountForBox(boxId)

    // ── Mappers ───────────────────────────────────────────────────────────────

    private fun BatchEntity.toDomain() = Batch(
        id = id, number = number, createdAt = createdAt
    )

    private fun BoxEntity.toDomain() = Box(
        id = id, batchId = batchId, boxNumber = boxNumber,
        isAutoNumber = isAutoNumber, isCompleted = isCompleted, createdAt = createdAt
    )

    private fun OrderEntity.toDomain() = Order(
        id = id, boxId = boxId, orderNumber = orderNumber, scannedAt = scannedAt
    )
}
