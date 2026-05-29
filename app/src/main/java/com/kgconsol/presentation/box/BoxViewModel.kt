package com.kgconsol.presentation.box

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgconsol.data.preferences.AppPreferences
import com.kgconsol.data.repository.KGRepository
import com.kgconsol.data.repository.RepoResult
import com.kgconsol.domain.model.Batch
import com.kgconsol.domain.model.Box
import com.kgconsol.domain.model.Order
import com.kgconsol.util.PrintResult
import com.kgconsol.util.ZebraPrinter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BoxUiState(
    val batch: Batch? = null,
    val box: Box? = null,
    val orders: List<Order> = emptyList(),
    val isPrinting: Boolean = false,
    val showSuccess: Boolean = false,
    val error: String? = null,
    val navigateToBox: Pair<Long, Long>? = null   // batchId, newBoxId
)

@HiltViewModel
class BoxViewModel @Inject constructor(
    private val repo: KGRepository,
    private val printer: ZebraPrinter,
    private val prefs: AppPreferences
) : ViewModel() {

    private val _ui = MutableStateFlow(BoxUiState())
    val ui: StateFlow<BoxUiState> = _ui.asStateFlow()

    fun load(batchId: Long, boxId: Long) {
        viewModelScope.launch {
            val batch = repo.getBatch(batchId)
            _ui.update { it.copy(batch = batch) }
        }
        viewModelScope.launch {
            repo.getOrdersForBox(boxId).collect { orders ->
                val box = repo.getBox(boxId)
                _ui.update { it.copy(box = box, orders = orders) }
            }
        }
    }

    fun completeBox() {
        val state = _ui.value
        val box = state.box ?: return
        val batch = state.batch ?: return

        viewModelScope.launch {
            _ui.update { it.copy(isPrinting = true, error = null) }

            // 1. Mark box completed
            repo.completeBox(box.id)

            // 2. Print 2 labels
            val settings = prefs.settings.first()
            val result = printer.printBoxLabel(
                ip = settings.printerIp,
                port = settings.printerPort,
                batchName = batch.displayName,
                boxDisplay = box.displayId,
                orderCount = state.orders.size,
                copies = 2
            )

            when (result) {
                is PrintResult.Success -> {
                    // 3. Show success animation
                    _ui.update { it.copy(isPrinting = false, showSuccess = true) }
                    delay(1500)

                    // 4. Open next box automatically
                    val nextNumber = repo.suggestNextBoxNumber(batch.id)
                    val boxResult = repo.createBox(batch.id, nextNumber, isAuto = true)
                    if (boxResult is RepoResult.Success) {
                        _ui.update { it.copy(showSuccess = false, navigateToBox = batch.id to boxResult.data) }
                    } else {
                        _ui.update { it.copy(showSuccess = false) }
                    }
                }
                is PrintResult.Error -> {
                    _ui.update { it.copy(isPrinting = false, error = result.message) }
                }
            }
        }
    }

    fun deleteOrder(orderId: Long) {
        viewModelScope.launch { repo.deleteOrder(orderId) }
    }

    fun navigationHandled() = _ui.update { it.copy(navigateToBox = null) }
    fun clearError() = _ui.update { it.copy(error = null) }
}