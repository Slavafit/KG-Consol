package com.kgconsol.presentation.reports


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
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState

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
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
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
                    items(state.boxReports) { report ->
                        BoxReportCard(
                            report = report,
                            onDeleteOrder = { orderId -> vm.deleteOrder(orderId) },
                            onDeleteBox = { vm.deleteBox(report.box.id) }
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun BoxReportCard(
    report: BoxReport,
    onDeleteOrder: (Long) -> Unit,
    onDeleteBox: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var confirmDeleteBox by remember { mutableStateOf(false) }

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
                    Text(
                        report.box.displayId,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${report.orders.size} orders",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                // Кнопка удалить коробку
                IconButton(onClick = { confirmDeleteBox = true }) {
                    Icon(
                        Icons.Default.Delete,
                        "Delete box",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                // Кнопка развернуть
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        "Expand"
                    )
                }
            }

            if (expanded) {
                HorizontalDivider(
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )
                if (report.orders.isEmpty()) {
                    Text(
                        "No orders",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                } else {
                    report.orders.forEach { order ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "• ${order.orderNumber}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 8.dp, top = 2.dp, bottom = 2.dp)
                            )
                            IconButton(onClick = { onDeleteOrder(order.id) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    "Delete order",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Диалог подтверждения удаления коробки
    if (confirmDeleteBox) {
        AlertDialog(
            onDismissRequest = { confirmDeleteBox = false },
            icon = {
                Icon(
                    Icons.Default.Delete,
                    null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Delete Box?") },
            text = {
                Text("Delete ${report.box.displayId} and all ${report.orders.size} orders inside?")
            },
            confirmButton = {
                Button(
                    onClick = { confirmDeleteBox = false; onDeleteBox() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteBox = false }) { Text("Cancel") }
            }
        )
    }
}
