package com.kgconsol.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// ─────────────────────────────────────────────────────────────────────────────
// Batch  →  "KG" + number  (e.g. KG123)
// ─────────────────────────────────────────────────────────────────────────────
@Entity(tableName = "batches")
data class BatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: Int,              // numeric part: 123 for KG123
    val createdAt: Long = System.currentTimeMillis()
) {
    val displayName: String get() = "KG$number"
}

// ─────────────────────────────────────────────────────────────────────────────
// Box  →  5-digit code (11111 / 22222 … for auto, any 5 digits for manual)
// ─────────────────────────────────────────────────────────────────────────────
@Entity(
    tableName = "boxes",
    foreignKeys = [
        ForeignKey(
            entity = BatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["batchId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("batchId")]
)
data class BoxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val batchId: Long,
    val boxNumber: String,        // e.g. "11111", "22222", "12345"
    val isAutoNumber: Boolean,    // true = repeating digits sequence
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    /** Always displayed as "ID XXXXX" */
    val displayId: String get() = "ID $boxNumber"
}

// ─────────────────────────────────────────────────────────────────────────────
// Order  →  format 01-2345-6789, unique per box
// ─────────────────────────────────────────────────────────────────────────────
@Entity(
    tableName = "orders",
    foreignKeys = [
        ForeignKey(
            entity = BoxEntity::class,
            parentColumns = ["id"],
            childColumns = ["boxId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("boxId"),
        Index(value = ["boxId", "orderNumber"], unique = true)  // no duplicates per box
    ]
)
data class OrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val boxId: Long,
    val orderNumber: String,      // "01-2345-6789"
    val scannedAt: Long = System.currentTimeMillis()
)
