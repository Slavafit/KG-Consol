package com.kgconsol.presentation.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgconsol.data.repository.KGRepository
import com.kgconsol.domain.model.Batch
import com.kgconsol.domain.model.Box
import com.kgconsol.domain.model.Order
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class BoxReport(val box: Box, val orders: List<Order>)

data class ReportsUiState(
    val batch: Batch? = null,
    val boxReports: List<BoxReport> = emptyList(),
    val isLoading: Boolean = true,
    val reportText: String = ""
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val repo: KGRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(ReportsUiState())
    val ui: StateFlow<ReportsUiState> = _ui.asStateFlow()

    fun load(batchId: Long) {
        viewModelScope.launch {
            val batch = repo.getBatch(batchId) ?: return@launch
            _ui.update { it.copy(batch = batch) }

            repo.getBoxesForBatch(batchId).collect { boxes ->
                val boxReports = boxes.map { box ->
                    val orders = repo.getOrdersForBox(box.id).first()
                    BoxReport(box, orders)
                }
                val text = buildReportText(batch, boxReports)
                _ui.update { it.copy(boxReports = boxReports, reportText = text, isLoading = false) }
            }
        }
    }
    fun deleteOrder(orderId: Long) {
        viewModelScope.launch {
            repo.deleteOrder(orderId)
            load(_ui.value.batch?.id ?: return@launch)
        }
    }

    fun deleteBox(boxId: Long) {
        viewModelScope.launch {
            // удаляем коробку — Room каскадно удалит все заказы
            val box = repo.getBox(boxId) ?: return@launch
            // добавьте в KGRepository:
            // suspend fun deleteBox(boxId: Long)
            repo.deleteBoxById(boxId)
            load(_ui.value.batch?.id ?: return@launch)
        }
    }

    private fun buildReportText(batch: Batch, reports: List<BoxReport>): String {
        val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        val sb = StringBuilder()
        sb.appendLine("══════════════════")
        sb.appendLine("  BATCH REPORT: ${batch.displayName}")
        sb.appendLine("  Generated: ${fmt.format(Date())}")
        sb.appendLine("══════════════════")
        sb.appendLine("Total boxes : ${reports.size}")
        sb.appendLine("Total orders: ${reports.sumOf { it.orders.size }}")
        sb.appendLine()

        reports.forEachIndexed { i, report ->
            sb.appendLine("──────────────────")
            //sb.appendLine("Box ${i + 1}: ${report.box.displayId}  (${if (report.box.isCompleted) "✓ Completed" else "In progress"})")
            sb.appendLine("Box ${i + 1}: ${report.box.displayId} ")
                        sb.appendLine("Orders: ${report.orders.size}")
            report.orders.forEach { order ->
                sb.appendLine("  • ${order.orderNumber}")
            }
        }
        sb.appendLine("══════════════════")
        return sb.toString()
    }
}