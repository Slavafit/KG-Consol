package com.kgconsol.presentation.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgconsol.data.repository.KGRepository
import com.kgconsol.data.repository.RepoResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScanUiState(
    val boxId: Long = 0L,
    val orders: List<String> = emptyList(),
    val lastAdded: String? = null,
    val error: String? = null,
    val manualInput: String = "",
    val isManualMode: Boolean = false,
    val navigateBack: Boolean = false,
    val triggerVibration: Boolean = false
)

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val repo: KGRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(ScanUiState())
    val ui: StateFlow<ScanUiState> = _ui.asStateFlow()

    private var lastScanned = ""
    private var lastScannedTime = 0L
    private val DEBOUNCE_MS = 2000L

    fun init(boxId: Long) {
        _ui.update { it.copy(boxId = boxId) }
        viewModelScope.launch {
            repo.getOrdersForBox(boxId).collect { orders ->
                _ui.update { it.copy(orders = orders.map { o -> o.orderNumber }) }
            }
        }
    }

    fun onTextScanned(rawText: String) {
        val now = System.currentTimeMillis()
        val regex = Regex("""\d{2}-\d{4}-\d{4}""")
        val match = regex.find(rawText)?.value ?: return

        if (match == lastScanned && now - lastScannedTime < DEBOUNCE_MS) return
        lastScanned = match
        lastScannedTime = now

        addOrder(match)
    }

    fun addOrder(orderNumber: String) {
        val boxId = _ui.value.boxId
        viewModelScope.launch {
            val result = repo.addOrder(boxId, orderNumber)
            when (result) {
                is RepoResult.Success -> _ui.update {
                    //it.copy(lastAdded = orderNumber, error = null, manualInput = "")
                    it.copy(
                        lastAdded = orderNumber,
                        error = null, manualInput = "",
                        navigateBack = true,
                        triggerVibration = true
                    )
                }
                is RepoResult.Error -> _ui.update { it.copy(error = result.message) }
            }
        }
    }

    //fun setManualInput(v: String) = _ui.update { it.copy(manualInput = v) }
    fun setManualInput(raw: String) {
        val digits = raw.filter { it.isDigit() }.take(10)
        _ui.update { it.copy(manualInput = digits) }
    }
    fun toggleManualMode() = _ui.update { it.copy(isManualMode = !it.isManualMode) }
    fun submitManual() {
        val digits = _ui.value.manualInput
        val formatted = "${digits.take(2)}-${digits.substring(2, 6)}-${digits.substring(6, 10)}"
        addOrder(formatted)
    }
    fun clearError() = _ui.update { it.copy(error = null) }
    fun clearLastAdded() = _ui.update { it.copy(lastAdded = null) }
    fun navigateBack() = _ui.update { it.copy(navigateBack = false) }
    fun vibrationHandled() = _ui.update { it.copy(triggerVibration = false) }
}