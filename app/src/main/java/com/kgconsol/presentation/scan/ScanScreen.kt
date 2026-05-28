package com.kgconsol.presentation.scan

import android.content.Context
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.kgconsol.data.repository.KGRepository
import com.kgconsol.data.repository.RepoResult
import com.kgconsol.domain.model.OrderValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

data class ScanUiState(
    val boxId: Long = 0L,
    val orders: List<String> = emptyList(),
    val lastAdded: String? = null,
    val error: String? = null,
    val manualInput: String = "",
    val isManualMode: Boolean = false
)

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val repo: KGRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(ScanUiState())
    val ui: StateFlow<ScanUiState> = _ui.asStateFlow()

    // Tracks recently scanned to avoid double-scan within 2s
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

    /** Called from ML Kit on every recognized text block */
    fun onTextScanned(rawText: String) {
        val now = System.currentTimeMillis()
        // Extract order number pattern from raw OCR text
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
                is RepoResult.Success -> _ui.update { it.copy(lastAdded = orderNumber, error = null, manualInput = "") }
                is RepoResult.Error -> _ui.update { it.copy(error = result.message) }
            }
        }
    }

    fun setManualInput(v: String) = _ui.update { it.copy(manualInput = v) }
    fun toggleManualMode() = _ui.update { it.copy(isManualMode = !it.isManualMode) }
    fun submitManual() { addOrder(_ui.value.manualInput) }
    fun clearError() = _ui.update { it.copy(error = null) }
    fun clearLastAdded() = _ui.update { it.copy(lastAdded = null) }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    boxId: Long,
    onBack: () -> Unit,
    vm: ScanViewModel = hiltViewModel()
) {
    val state by vm.ui.collectAsState()
    val context = LocalContext.current
    var torchEnabled by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    LaunchedEffect(boxId) { vm.init(boxId) }

    // Auto-clear "last added" after 1.5s
    LaunchedEffect(state.lastAdded) {
        if (state.lastAdded != null) {
            kotlinx.coroutines.delay(1500)
            vm.clearLastAdded()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Order", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    // Torch toggle
                    IconButton(onClick = {
                        torchEnabled = !torchEnabled
                        camera?.cameraControl?.enableTorch(torchEnabled)
                    }) {
                        Icon(
                            if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Torch",
                            tint = if (torchEnabled) Color.Yellow else LocalContentColor.current
                        )
                    }
                    // Manual mode toggle
                    IconButton(onClick = vm::toggleManualMode) {
                        Icon(
                            if (state.isManualMode) Icons.Default.CameraAlt else Icons.Default.Keyboard,
                            contentDescription = "Switch mode"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // ── Camera Preview ──────────────────────────────────────────────
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black)
            ) {
                CameraPreview(
                    onCameraReady = { cam -> camera = cam },
                    onTextRecognized = vm::onTextScanned,
                    modifier = Modifier.fillMaxSize()
                )

                // Scanning frame overlay
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(width = 280.dp, height = 80.dp)
                        .border(2.dp, Color(0xFF4CAF50), RoundedCornerShape(8.dp))
                )

                // "Last added" toast
                state.lastAdded?.let { added ->
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp),
                        color = Color(0xFF4CAF50),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(added, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Text(
                    "Point camera at barcode  •  ${state.orders.size} scanned",
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .background(Color(0x88000000), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // ── Manual Input Panel ──────────────────────────────────────────
            if (state.isManualMode) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = state.manualInput,
                            onValueChange = vm::setManualInput,
                            label = { Text("Order (01-2345-6789)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            isError = state.manualInput.isNotEmpty() &&
                                    !OrderValidator.isValid(state.manualInput)
                        )
                        Button(
                            onClick = vm::submitManual,
                            enabled = OrderValidator.isValid(state.manualInput)
                        ) {
                            Text("Add")
                        }
                    }
                }
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
}

// ─── CameraX + ML Kit composable ─────────────────────────────────────────────

@Composable
private fun CameraPreview(
    onCameraReady: (Camera) -> Unit,
    onTextRecognized: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val recognizer = remember {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    val executor: ExecutorService = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            recognizer.close()
            executor.shutdown()
        }
    }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        },
        modifier = modifier,
        update = { previewView ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                val cameraProvider = future.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(executor) { imageProxy ->
                    @androidx.camera.core.ExperimentalGetImage
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val image = InputImage.fromMediaImage(
                            mediaImage, imageProxy.imageInfo.rotationDegrees
                        )
                        recognizer.process(image)
                            .addOnSuccessListener { visionText ->
                                if (visionText.text.isNotBlank()) {
                                    onTextRecognized(visionText.text)
                                }
                            }
                            .addOnCompleteListener { imageProxy.close() }
                    } else {
                        imageProxy.close()
                    }
                }

                try {
                    cameraProvider.unbindAll()
                    val cam = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                    onCameraReady(cam)
                } catch (e: Exception) {
                    Log.e("CameraPreview", "Binding failed", e)
                }
            }, ContextCompat.getMainExecutor(context))
        }
    )
}
