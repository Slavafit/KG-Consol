package com.kgconsol.presentation.scan

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgconsol.data.repository.KGRepository
import com.kgconsol.data.repository.RepoResult
import com.kgconsol.data.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScanUiState(
    val boxId: Long = 0L,
    val orders: List<String> = emptyList(),
    val lastAdded: String? = null,
    val error: String? = null,
    val manualInput: String = "",       // Основные 10 цифр
    val manualFraction: String = "",    // Цифры после дроби (могут содержать /)
    val isManualMode: Boolean = false,
    val navigateBack: Boolean = false,
    val triggerVibration: Boolean = false,
    val triggerBeep: Boolean = false,    // Флаг для звукового сигнала
    val barcodeScanningEnabled: Boolean = true,
    val recognitionProgress: Float = 0f // Прогресс подтверждения (0.0 до 1.0)
)

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val repo: KGRepository,
    prefs: AppPreferences
) : ViewModel() {

    private val _ui = MutableStateFlow(ScanUiState())
    val ui: StateFlow<ScanUiState> = _ui.asStateFlow()

    private var lastScanned = ""
    private var lastScannedTime = 0L
    private var lastUnsupportedBarcode = ""
    private var lastUnsupportedBarcodeTime = 0L
    private val DEBOUNCE_MS = 2500L // Пауза между одинаковыми сканами

    // Механизм стабильного распознавания (3 подтверждения)
    private var currentCandidate = ""
    private var recognitionCount = 0
    private var lastCandidateTime = 0L
    private val REQUIRED_CONFIRMATIONS = 3
    private val STABILITY_TIMEOUT_MS = 500L // Сброс прогресса, если номер пропал на 0.5 сек

    init {
        viewModelScope.launch {
            prefs.settings.collect { settings ->
                _ui.update { it.copy(barcodeScanningEnabled = settings.barcodeScanningEnabled) }
            }
        }
    }

    fun init(boxId: Long) {
        _ui.update { it.copy(boxId = boxId) }
        viewModelScope.launch {
            repo.getOrdersForBox(boxId).collect { orders ->
                _ui.update { it.copy(orders = orders.map { o -> o.orderNumber }) }
            }
        }
    }

    fun onBarcodeScanned(rawValue: String) {
        val barcodeValue = rawValue
        Log.d("KGBarcode", "Barcode order: raw=$rawValue, stored=$barcodeValue")
        if (barcodeValue.isEmpty()) {
            val now = System.currentTimeMillis()
            if (rawValue != lastUnsupportedBarcode || now - lastUnsupportedBarcodeTime >= DEBOUNCE_MS) {
                lastUnsupportedBarcode = rawValue
                lastUnsupportedBarcodeTime = now
                _ui.update {
                    it.copy(error = "Штрихкод распознан, но номер заказа в нём не найден. Значение: $rawValue")
                }
            }
            return
        }

        val now = System.currentTimeMillis()
        if (barcodeValue == lastScanned && now - lastScannedTime < DEBOUNCE_MS) return

        lastScanned = barcodeValue
        lastScannedTime = now
        resetRecognition()
        addOrder(barcodeValue, validateFormat = false)
    }

    fun onTextScanned(rawText: String) {
        val now = System.currentTimeMillis()
        val matches = findOrderNumbers(rawText)

        if (matches.isEmpty()) {
            if (now - lastCandidateTime > STABILITY_TIMEOUT_MS && recognitionCount > 0) {
                resetRecognition()
            }
            return
        }

        // 2. Выбираем САМОЕ ДЛИННОЕ совпадение в текущем кадре.
        // Это предотвращает добавление "23-4568-5647" если камера видит "23-4568-5647/1".
        val match = matches.maxByOrNull { it.length } ?: return

        // 3. Проверка на дубликат (чтобы не сканировать одно и то же сразу)
        if (match == lastScanned && now - lastScannedTime < DEBOUNCE_MS) {
            _ui.update { it.copy(recognitionProgress = 0f) }
            return
        }

        // 4. Проверка стабильности (таймаут между кадрами)
        if (now - lastCandidateTime > STABILITY_TIMEOUT_MS) {
            recognitionCount = 0
            currentCandidate = ""
        }

        if (match == currentCandidate) {
            recognitionCount++
        } else {
            currentCandidate = match
            recognitionCount = 1
        }
        lastCandidateTime = now

        // Обновляем прогресс для визуальной полоски
        val progress = recognitionCount.toFloat() / REQUIRED_CONFIRMATIONS
        _ui.update { it.copy(recognitionProgress = progress) }

        if (recognitionCount >= REQUIRED_CONFIRMATIONS) {
            lastScanned = match
            lastScannedTime = now
            
            resetRecognition()
            addOrder(match)
        }
    }

    private fun findOrderNumbers(rawValue: String): List<String> {
        // Some scanners return spaces or non-breaking spaces around separators.
        val normalizedRaw = rawValue.trim().replace('\u00A0', ' ')
        val formattedRegex = Regex("""\d{2}\s*-\s*\d{4}\s*-\s*\d{4}(?:\s*/\s*\d+)*""")
        val compactRegex = Regex("""(?<!\d)\d{10}(?:\s*/\s*\d+)*(?!\d)""")
        return buildList {
            formattedRegex.findAll(normalizedRaw).forEach { result ->
                add(result.value.replace(" ", ""))
            }
            compactRegex.findAll(normalizedRaw).forEach { result ->
                val value = result.value.replace(" ", "")
                val digits = value.substringBefore('/')
                val fraction = value.removePrefix(digits)
                add("${digits.take(2)}-${digits.substring(2, 6)}-${digits.takeLast(4)}$fraction")
            }
        }.distinct()
    }

    private fun resetRecognition() {
        currentCandidate = ""
        recognitionCount = 0
        _ui.update { it.copy(recognitionProgress = 0f) }
    }

    fun addOrder(orderNumber: String, validateFormat: Boolean = true) {
        val boxId = _ui.value.boxId
        viewModelScope.launch {
            val result = repo.addOrder(boxId, orderNumber, validateFormat)
            when (result) {
                is RepoResult.Success -> {
                    _ui.update {
                        it.copy(
                            lastAdded = orderNumber,
                            error = null, 
                            manualInput = "",
                            manualFraction = "",
                            triggerVibration = true,
                            triggerBeep = true
                        )
                    }
                }
                is RepoResult.Error -> {
                    _ui.update { it.copy(error = result.message) }
                    resetRecognition()
                }
            }
        }
    }

    fun setManualInput(raw: String) {
        val digits = raw.filter { it.isDigit() }.take(10)
        _ui.update { it.copy(manualInput = digits) }
    }

    fun setManualFraction(raw: String) {
        // Разрешаем вводить цифры и слеши для сложных дробей
        val allowed = raw.filter { it.isDigit() || it == '/' }.take(10)
        _ui.update { it.copy(manualFraction = allowed) }
    }

    fun toggleManualMode() = _ui.update { it.copy(isManualMode = !it.isManualMode) }

    fun submitManual() {
        val digits = _ui.value.manualInput
        if (digits.length != 10) return
        val base = "${digits.take(2)}-${digits.substring(2,6)}-${digits.substring(6,10)}"
        
        var fraction = _ui.value.manualFraction
        // Если введена дробь без начального слеша, добавляем его
        if (fraction.isNotEmpty() && !fraction.startsWith("/")) {
            fraction = "/$fraction"
        }
        
        val full = base + fraction
        addOrder(full)
    }
    
    fun clearError() = _ui.update { it.copy(error = null) }
    fun clearLastAdded() = _ui.update { it.copy(lastAdded = null) }
    fun navigateBack() = _ui.update { it.copy(navigateBack = false) }
    fun effectsHandled() = _ui.update { it.copy(triggerVibration = false, triggerBeep = false) }
    fun vibrationHandled() = _ui.update { it.copy(triggerVibration = false) }
}
