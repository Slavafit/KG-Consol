package com.kgconsol.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kgconsol.data.preferences.AppLanguage


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel()
) {
    val state by vm.ui.collectAsState()
    var ipField by remember(state.settings.printerIp) { mutableStateOf(state.settings.printerIp) }
    var portField by remember(state.settings.printerPort) { mutableStateOf(state.settings.printerPort.toString()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Printer Section ─────────────────────────────────────────────
            SettingsSection(title = "Zebra Printer", icon = Icons.Default.Print) {
                OutlinedTextField(
                    value = ipField,
                    onValueChange = { ipField = it },
                    label = { Text("Printer IP Address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    trailingIcon = {
                        if (ipField != state.settings.printerIp) {
                            IconButton(onClick = { vm.setIp(ipField) }) {
                                Icon(Icons.Default.Save, "Save")
                            }
                        }
                    }
                )

                OutlinedTextField(
                    value = portField,
                    onValueChange = { portField = it },
                    label = { Text("Port (default: 9100)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    trailingIcon = {
                        val port = portField.toIntOrNull()
                        if (port != null && port != state.settings.printerPort) {
                            IconButton(onClick = { vm.setPort(port) }) {
                                Icon(Icons.Default.Save, "Save")
                            }
                        }
                    }
                )

                Button(
                    onClick = vm::testPrint,
                    enabled = !state.isTesting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isTesting) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Default.Print, null)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Test Print")
                }

                state.testResult?.let { result ->
                    val isSuccess = result.startsWith("✓")
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (isSuccess)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            result,
                            modifier = Modifier.padding(12.dp),
                            color = if (isSuccess)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // ── Display Section ─────────────────────────────────────────────
            SettingsSection(title = "Display", icon = Icons.Default.Brightness6) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Keep Screen On", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Prevent screen from sleeping",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Switch(
                        checked = state.settings.keepScreenOn,
                        onCheckedChange = vm::setKeepScreenOn
                    )
                }
            }

            // ── Language Section ────────────────────────────────────────────
            SettingsSection(title = "Language", icon = Icons.Default.Language) {
                val languages = listOf(
                    AppLanguage.RUSSIAN to "🇷🇺 Русский",
                    AppLanguage.ENGLISH to "🇬🇧 English",
                    AppLanguage.SPANISH to "🇪🇸 Español"
                )
                languages.forEach { (lang, label) ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = state.settings.language == lang,
                            onClick = { vm.setLanguage(lang) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Text(
                    "Restart the app to apply language changes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // ── App Info ────────────────────────────────────────────────────
            SettingsSection(title = "About", icon = Icons.Default.Info) {
                Text("KG Consol v1.0.0", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Barcode scanning and label printing for warehouse operations.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            HorizontalDivider()
            content()
        }
    }
}
