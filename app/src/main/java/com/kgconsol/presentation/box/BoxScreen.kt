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

// ─── Screen ───────────────────────────────────────────────────────────────────

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScreen(
    batchId: Long,
    boxId: Long,
    onScan: () -> Unit,
    onBoxCompleted: (Long) -> Unit,
    onReports: () -> Unit,
    onBack: () -> Unit,
    vm: BoxViewModel = hiltViewModel()
) {
    val state by vm.ui.collectAsState()

    LaunchedEffect(batchId, boxId) { vm.load(batchId, boxId) }

    LaunchedEffect(state.navigateToBox) {
        state.navigateToBox?.let { (bid, newBoxId) ->
            onBoxCompleted(newBoxId)
            vm.navigationHandled()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                state.batch?.displayName ?: "...",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${state.orders.size} orders",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = onReports) {
                            Icon(Icons.Default.Assessment, "Reports")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── Big Box ID Display ──────────────────────────────────────
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 8.dp
                ) {
                    Column(
                        Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "ACTIVE BOX",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            letterSpacing = 3.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = state.box?.displayId ?: "ID -----",
                            fontSize = 52.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Center,
                            letterSpacing = 4.sp
                        )
                        if (state.box?.isAutoNumber == false) {
                            Text(
                                "Manual",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }

                // ── Scan Button ─────────────────────────────────────────────
                Button(
                    onClick = onScan,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(64.dp),
                    enabled = state.box?.isCompleted == false
                ) {
                    Icon(Icons.Default.QrCodeScanner, null, Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("SCAN ORDER", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(12.dp))

                // ── Orders List ─────────────────────────────────────────────
                Text(
                    "Orders in this box",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp).align(Alignment.Start)
                )

                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (state.orders.isEmpty()) {
                        item {
                            Text(
                                "No orders scanned yet",
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    } else {
                        items(state.orders, key = { it.id }) { order ->
                            OrderRow(order = order, onDelete = { vm.deleteOrder(order.id) })
                        }
                    }
                }

                // ── Complete Box Button ─────────────────────────────────────
                Button(
                    onClick = vm::completeBox,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    ),
                    enabled = state.orders.isNotEmpty() && !state.isPrinting && state.box?.isCompleted == false
                ) {
                    if (state.isPrinting) {
                        CircularProgressIndicator(
                            Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onTertiary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Print, null, Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("COMPLETE BOX & PRINT", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ── Success Overlay ──────────────────────────────────────────────────
        AnimatedVisibility(
            visible = state.showSuccess,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color(0xCC000000),
                    modifier = Modifier.fillMaxSize()
                ) {}
                val scale by animateFloatAsState(
                    targetValue = if (state.showSuccess) 1f else 0.5f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "success_scale"
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.scale(scale)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        null,
                        Modifier.size(120.dp),
                        tint = Color(0xFF4CAF50)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Box Completed!",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "Printing labels...",
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }

    state.error?.let { err ->
        AlertDialog(
            onDismissRequest = vm::clearError,
            icon = { Icon(Icons.Default.PrintDisabled, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Print Error") },
            text = { Text(err) },
            confirmButton = { Button(onClick = vm::clearError) { Text("OK") } }
        )
    }
}

@Composable
private fun OrderRow(order: Order, onDelete: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.QrCode,
                null,
                Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Text(
                order.orderNumber,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete Order?") },
            text = { Text("Remove ${order.orderNumber} from this box?") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}
