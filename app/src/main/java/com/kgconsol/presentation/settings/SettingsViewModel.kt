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