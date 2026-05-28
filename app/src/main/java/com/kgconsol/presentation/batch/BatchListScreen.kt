package com.kgconsol.presentation.batch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgconsol.data.repository.KGRepository
import com.kgconsol.data.repository.RepoResult
import com.kgconsol.domain.model.Batch
import com.kgconsol.domain.model.BoxNumberHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

data class BatchListUiState(
    val batches: List<Batch> = emptyList(),
    val suggestedNumber: Int = 1,
    val isLoading: Boolean = false,
    val error: String? = null,
    val navigateToBox: Pair<Long, Long>? = null   // batchId to boxId
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

            // 1. Create batch
            val batchResult = repo.createBatch(batchNumber)
            if (batchResult is RepoResult.Error) {
                _ui.update { it.copy(isLoading = false, error = batchResult.message) }
                return@launch
            }
            val batchId = (batchResult as RepoResult.Success).data

            // 2. Create first box
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
            // Resume open box, or create new one
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

// ─── Screen ───────────────────────────────────────────────────────────────────

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kgconsol.R
import com.kgconsol.domain.model.Batch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchListScreen(
    onOpenBox: (Long, Long) -> Unit,
    onSettings: () -> Unit,
    vm: BatchListViewModel = hiltViewModel()
) {
    val state by vm.ui.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.navigateToBox) {
        state.navigateToBox?.let { (batchId, boxId) ->
            onOpenBox(batchId, boxId)
            vm.navigationHandled()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("KG Consol", fontWeight = FontWeight.Bold, fontSize = 22.sp) },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New Batch") },
                containerColor = MaterialTheme.colorScheme.primary
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (state.batches.isEmpty()) {
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Inventory2,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No batches yet",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text("Tap + to create your first batch")
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.batches, key = { it.id }) { batch ->
                        BatchCard(batch = batch, onClick = { vm.openExistingBatch(batch.id) })
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }

            if (state.isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }

    state.error?.let { err ->
        AlertDialog(
            onDismissRequest = vm::clearError,
            title = { Text("Error") },
            text = { Text(err) },
            confirmButton = { TextButton(onClick = vm::clearError) { Text("OK") } }
        )
    }

    if (showCreateDialog) {
        CreateBatchDialog(
            suggestedNumber = state.suggestedNumber,
            onDismiss = { showCreateDialog = false },
            onCreate = { number, autoBox, manualBox ->
                showCreateDialog = false
                vm.createBatchAndBox(number, autoBox, manualBox)
            }
        )
    }
}

@Composable
private fun BatchCard(batch: Batch, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        batch.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(batch.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(batch.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateBatchDialog(
    suggestedNumber: Int,
    onDismiss: () -> Unit,
    onCreate: (Int, Boolean, String) -> Unit
) {
    var numberText by remember { mutableStateOf(suggestedNumber.toString()) }
    var autoBox by remember { mutableStateOf(true) }
    var manualBoxNumber by remember { mutableStateOf("") }
    val number = numberText.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Batch", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = numberText,
                    onValueChange = { if (it.all(Char::isDigit)) numberText = it },
                    label = { Text("Batch number") },
                    prefix = { Text("KG") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Box type", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = autoBox, onClick = { autoBox = true })
                    Text("Auto (11111, 22222…)", Modifier.clickable { autoBox = true })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = !autoBox, onClick = { autoBox = false })
                    Text("Manual (5 digits)", Modifier.clickable { autoBox = false })
                }
                if (!autoBox) {
                    OutlinedTextField(
                        value = manualBoxNumber,
                        onValueChange = { if (it.length <= 5 && it.all(Char::isDigit)) manualBoxNumber = it },
                        label = { Text("Box number (5 digits)") },
                        singleLine = true,
                        isError = manualBoxNumber.isNotEmpty() && manualBoxNumber.length != 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { number?.let { onCreate(it, autoBox, manualBoxNumber) } },
                enabled = number != null && (autoBox || manualBoxNumber.length == 5)
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
