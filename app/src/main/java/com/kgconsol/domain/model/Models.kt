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

    /** Generate a random four-digit ID that is unique within the batch. */
    fun nextAutoNumber(existingNumbers: Set<String>): String {
        repeat(100) {
            val candidate = kotlin.random.Random.nextInt(1000, 10_000).toString()
            if (candidate !in existingNumbers) return candidate
        }

        return (1000..9999)
            .firstOrNull { it.toString() !in existingNumbers }
            ?.toString()
            ?: error("No free four-digit box numbers")
    }

    /** Validate manual entry: exactly 4 digits */
    fun isValidManual(input: String): Boolean =
        input.length == 4 && input.all { it.isDigit() }
}

// ─────────────────────────────────────────────────────────────────────────────
// Order number validation
// ─────────────────────────────────────────────────────────────────────────────
object OrderValidator {
    private val REGEX = Regex("""^\d{2}-\d{4}-\d{4}(?:/\d+)?$""")

    fun isValid(input: String): Boolean = REGEX.matches(input.trim())

    fun normalize(input: String): String = input.trim()
}
