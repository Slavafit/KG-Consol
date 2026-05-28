package com.kgconsol.domain.model

// ─────────────────────────────────────────────────────────────────────────────
// Domain models — decoupled from Room entities
// ─────────────────────────────────────────────────────────────────────────────

data class Batch(
    val id: Long,
    val number: Int,
    val createdAt: Long,
    val boxCount: Int = 0,
    val totalOrders: Int = 0
) {
    val displayName: String get() = "KG$number"
}

data class Box(
    val id: Long,
    val batchId: Long,
    val boxNumber: String,
    val isAutoNumber: Boolean,
    val isCompleted: Boolean,
    val createdAt: Long,
    val orderCount: Int = 0
) {
    val displayId: String get() = "ID $boxNumber"
}

data class Order(
    val id: Long,
    val boxId: Long,
    val orderNumber: String,
    val scannedAt: Long
)

// ─────────────────────────────────────────────────────────────────────────────
// Box number logic
// ─────────────────────────────────────────────────────────────────────────────
object BoxNumberHelper {

    /** Auto sequence: 11111, 22222, 33333 … 99999 */
    private val AUTO_SEQUENCE = (1..9).map { "$it$it$it$it$it" }

    fun nextAutoNumber(current: String?): String {
        if (current == null) return AUTO_SEQUENCE.first()
        val idx = AUTO_SEQUENCE.indexOf(current)
        return if (idx == -1 || idx == AUTO_SEQUENCE.lastIndex) AUTO_SEQUENCE.first()
        else AUTO_SEQUENCE[idx + 1]
    }

    /** Validate manual entry: exactly 5 digits */
    fun isValidManual(input: String): Boolean =
        input.length == 5 && input.all { it.isDigit() }
}

// ─────────────────────────────────────────────────────────────────────────────
// Order number validation
// ─────────────────────────────────────────────────────────────────────────────
object OrderValidator {
    // Format: 01-2345-6789  (2 digits - 4 digits - 4 digits)
    private val REGEX = Regex("""^\d{2}-\d{4}-\d{4}$""")

    fun isValid(input: String): Boolean = REGEX.matches(input.trim())

    fun normalize(input: String): String = input.trim()
}
