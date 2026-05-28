package com.kgconsol.presentation.reports

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgconsol.data.local.dao.BatchDao
import com.kgconsol.data.local.dao.BoxDao
import com.kgconsol.data.local.dao.OrderDao
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

    private fun buildReportText(batch: Batch, reports: List<BoxReport>): String {
        val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        val sb = StringBuilder()
        sb.appendLine("═══════════════════════════════")
        sb.appendLine("  BATCH REPORT: ${batch.displayName}")
        sb.appendLine("  Generated: ${fmt.format(Date())}")
        sb.appendLine("═══════════════════════════════")
        sb.appendLine("Total boxes : ${reports.size}")
        sb.appendLine("Total orders: ${reports.sumOf { it.orders.size }}")
        sb.appendLine()

        reports.forEachIndexed { i, report ->
            sb.appendLine("───────────────────────────────")
            sb.appendLine("Box ${i + 1}: ${report.box.displayId}  (${if (report.box.isCompleted) "✓ Completed" else "In progress"})")
            sb.appendLine("Orders: ${report.orders.size}")
            report.orders.forEach { order ->
                sb.appendLine("  • ${order.orderNumber}")
            }
        }
        sb.appendLine("═══════════════════════════════")
        return sb.toString()
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    batchId: Long,
    onBack: () -> Unit,
    vm: ReportsViewModel = hiltViewModel()
) {
    val state by vm.ui.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(batchId) { vm.load(batchId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.batch?.let { "Report: ${it.displayName}" } ?: "Report",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    // Copy to clipboard
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText("KG Report", state.reportText)
                        )
                    }) { Icon(Icons.Default.ContentCopy, "Copy") }

                    // Share
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, state.reportText)
                            putExtra(Intent.EXTRA_SUBJECT, "KG Report ${state.batch?.displayName}")
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Report"))
                    }) { Icon(Icons.Default.Share, "Share") }
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
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Summary card
                state.batch?.let { batch ->
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(batch.displayName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                                Text("${state.boxReports.size} boxes • ${state.boxReports.sumOf { it.orders.size }} orders total")
                            }
                        }
                    }
                }

                // Per-box cards
                items(state.boxReports) { report ->
                    BoxReportCard(report)
                }
            }
        }
    }
}

@Composable
private fun BoxReportCard(report: BoxReport) {
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (report.box.isCompleted)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        if (report.box.isCompleted) "✓" else "…",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(report.box.displayId, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${report.orders.size} orders", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        "Expand"
                    )
                }
            }

            if (expanded && report.orders.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                report.orders.forEach { order ->
                    Text(
                        "• ${order.orderNumber}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 2.dp, start = 8.dp)
                    )
                }
            }
        }
    }
}
