package com.kgconsol.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgconsol.data.preferences.AppLanguage
import com.kgconsol.data.preferences.AppPreferences
import com.kgconsol.data.preferences.AppSettings
import com.kgconsol.util.PrintResult
import com.kgconsol.util.ZebraPrinter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isTesting: Boolean = false,
    val testResult: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val printer: ZebraPrinter
) : ViewModel() {

    private val _ui = MutableStateFlow(SettingsUiState())
    val ui: StateFlow<SettingsUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.settings.collect { settings ->
                _ui.update { it.copy(settings = settings) }
            }
        }
    }

    fun setIp(ip: String) = viewModelScope.launch { prefs.setPrinterIp(ip) }
    fun setPort(port: Int) = viewModelScope.launch { prefs.setPrinterPort(port) }
    fun setKeepScreenOn(v: Boolean) = viewModelScope.launch { prefs.setKeepScreenOn(v) }
    fun setLanguage(lang: AppLanguage) = viewModelScope.launch { prefs.setLanguage(lang) }

    fun testPrint() {
        val settings = _ui.value.settings
        viewModelScope.launch {
            _ui.update { it.copy(isTesting = true, testResult = null) }
            val result = printer.printTest(settings.printerIp, settings.printerPort)
            val msg = when (result) {
                is PrintResult.Success -> "✓ Test print sent successfully!"
                is PrintResult.Error -> "✗ ${result.message}"
            }
            _ui.update { it.copy(isTesting = false, testResult = msg) }
        }
    }

    fun clearTestResult() = _ui.update { it.copy(testResult = null) }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

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
import androidx.compose.ui.unit.sp
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
