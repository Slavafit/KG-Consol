package com.kgconsol.presentation.batch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgconsol.data.repository.KGRepository
import com.kgconsol.data.repository.RepoResult
import com.kgconsol.domain.model.Batch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BatchListUiState(
    val batches: List<Batch> = emptyList(),
    val suggestedNumber: Int = 1,
    val isLoading: Boolean = false,
    val error: String? = null,
    val navigateToBox: Pair<Long, Long>? = null
)

@HiltViewModel
class BatchListViewModel @Inject constructor(
    private val repo: KGRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(BatchListUiState())
    val ui: StateFlow<BatchListUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            repo.getAllBatches().collect { batches ->
                val suggested = repo.suggestNextBatchNumber()
                _ui.update { it.copy(batches = batches, suggestedNumber = suggested) }
            }
        }
    }

    fun createBatchAndBox(batchNumber: Int, autoBox: Boolean, manualBoxNumber: String = "") {
        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true, error = null) }

            val batchResult = repo.createBatch(batchNumber)
            if (batchResult is RepoResult.Error) {
                _ui.update { it.copy(isLoading = false, error = batchResult.message) }
                return@launch
            }
            val batchId = (batchResult as RepoResult.Success).data

            val boxNumber: String
            val isAuto: Boolean
            if (autoBox) {
                boxNumber = repo.suggestNextBoxNumber(batchId)
                isAuto = true
            } else {
                boxNumber = manualBoxNumber
                isAuto = false
            }

            val boxResult = repo.createBox(batchId, boxNumber, isAuto)
            if (boxResult is RepoResult.Error) {
                _ui.update { it.copy(isLoading = false, error = boxResult.message) }
                return@launch
            }
            val boxId = (boxResult as RepoResult.Success).data
            _ui.update { it.copy(isLoading = false, navigateToBox = batchId to boxId) }
        }
    }

    fun openExistingBatch(batchId: Long) {
        viewModelScope.launch {
            val openBox = repo.getOpenBox(batchId)
            if (openBox != null) {
                _ui.update { it.copy(navigateToBox = batchId to openBox.id) }
            } else {
                val boxNumber = repo.suggestNextBoxNumber(batchId)
                val result = repo.createBox(batchId, boxNumber, isAuto = true)
                if (result is RepoResult.Success) {
                    _ui.update { it.copy(navigateToBox = batchId to result.data) }
                }
            }
        }
    }

    fun navigationHandled() = _ui.update { it.copy(navigateToBox = null) }
    fun clearError() = _ui.update { it.copy(error = null) }
}