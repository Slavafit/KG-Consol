package com.kgconsol.presentation.scan

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.kgconsol.domain.model.OrderValidator
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


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

    // --- Permission Handling ---
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        hasCameraPermission = isGranted
    }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) launcher.launch(Manifest.permission.CAMERA)
    }
    // ---------------------------

    LaunchedEffect(boxId) { vm.init(boxId) }

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
            Box(
                Modifier.fillMaxWidth().weight(1f).background(Color.Black)
            ) {
                if (hasCameraPermission) {
                    CameraPreview(
                        onCameraReady = { cam -> camera = cam },
                        onTextRecognized = vm::onTextScanned,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Camera permission is required", color = Color.White)
                    }
                }

                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(width = 280.dp, height = 80.dp)
                        .border(2.dp, Color(0xFF4CAF50), RoundedCornerShape(8.dp))
                )

                state.lastAdded?.let { added ->
                    Surface(
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
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

            if (state.isManualMode) {
                Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = state.manualInput,
                            onValueChange = vm::setManualInput,
                            label = { Text("Order (01-2345-6789)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            isError = state.manualInput.isNotEmpty() && !OrderValidator.isValid(state.manualInput)
                        )
                        Button(
                            onClick = vm::submitManual,
                            enabled = OrderValidator.isValid(state.manualInput)
                        ) { Text("Add") }
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

@OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraPreview(
    onCameraReady: (Camera) -> Unit,
    onTextRecognized: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val executor: ExecutorService = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            recognizer.close()
            executor.shutdown()
        }
    }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE }
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
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                        recognizer.process(image)
                            .addOnSuccessListener { visionText ->
                                if (visionText.text.isNotBlank()) onTextRecognized(visionText.text)
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
