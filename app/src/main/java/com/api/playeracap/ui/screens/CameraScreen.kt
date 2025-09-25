package com.api.playeracap.ui.screens

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.MediaStore.Files.getContentUri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.api.playeracap.data.ProductionInfo
import com.api.playeracap.sensor.SensorType
import com.api.playeracap.viewmodel.MainViewModel
import com.api.playeracap.viewmodel.UploadStatus
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import android.hardware.camera2.CaptureRequest
import com.api.playeracap.data.UserPreferences
import kotlinx.coroutines.launch

// Función para extraer el número de línea del baseFolder
private fun extractLineNumber(baseFolder: String): Int {
    return baseFolder.substringAfter("/linea_", "1").toIntOrNull()?.coerceIn(1, 10) ?: 1
}

private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    productionInfo: ProductionInfo,
    viewModel: MainViewModel
) {
    try {
        Log.d("CameraScreen", "📸 Iniciando captura de foto")
        
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "IMG_${productionInfo.modelo}_${productionInfo.ordenProducto}_$timestamp.jpg"
            .replace(" ", "_")

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PlayeraCap")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    try {
                        outputFileResults.savedUri?.let { uri ->
                            // Crear metadatos después de confirmar que la imagen se guardó
                            val metadataContent = """
                                Fecha y Hora: $timestamp
                                Modelo: ${productionInfo.modelo}
                                Orden de Producto: ${productionInfo.ordenProducto}
                                Cliente: ${productionInfo.nombreCliente}
                                Color de Tela: ${productionInfo.colorTela}
                                Encargado de Máquina: ${productionInfo.encargadoMaquina}
                            """.trimIndent()

                            viewModel.addCapturedImage(uri, metadataContent)
                            Log.d("CameraScreen", "✅ Imagen guardada exitosamente: $uri")
                            
                            // Mostrar mensaje de éxito
                            Toast.makeText(
                                context,
                                "Imagen capturada correctamente",
                                Toast.LENGTH_SHORT
                            ).show()
                        } ?: run {
                            Log.e("CameraScreen", "❌ URI de imagen nulo")
                            Toast.makeText(
                                context,
                                "Error: No se pudo guardar la imagen",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } catch (e: Exception) {
                        Log.e("CameraScreen", "❌ Error al procesar imagen guardada: ${e.message}", e)
                        Toast.makeText(
                            context,
                            "Error al procesar la imagen: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraScreen", "❌ Error al guardar la imagen: ${exception.message}", exception)
                    Toast.makeText(
                        context,
                        "Error al capturar la imagen: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    } catch (e: Exception) {
        Log.e("CameraScreen", "❌ Error general en takePhoto: ${e.message}", e)
        Toast.makeText(
            context,
            "Error al iniciar la captura: ${e.message}",
            Toast.LENGTH_SHORT
        ).show()
    }
}

@Composable
fun CameraScreen(
    viewModel: MainViewModel,
    navController: NavController
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val userPreferences = remember { UserPreferences(context) }
    val sensorActive by viewModel.sensorActive.collectAsState()
    val productionInfo by viewModel.productionInfo.collectAsState()
    val isProductionInfoSet by viewModel.isProductionInfoSet.collectAsState()
    
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var lastCaptureTime by remember { mutableStateOf(0L) }
    var cameraInitialized by remember { mutableStateOf(false) }
    
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var isCaptureActive by remember { mutableStateOf(false) }
    var captureMode by remember { mutableStateOf("Automático") }
    var shutterSpeed by remember { mutableStateOf("1/125") }
    var cameraZoomLevel by remember { mutableStateOf(1f) }
    var cameraInstance by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    
    // Estados para el menú de configuración
    var showCameraConfigMenu by remember { mutableStateOf(false) }
    var showFullImagePreview by remember { mutableStateOf(false) }
    var showFullscreenCameraPreview by remember { mutableStateOf(false) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    // Recuperar la velocidad de obturación guardada
    LaunchedEffect(Unit) {
        userPreferences.shutterSpeed.collect { savedSpeed ->
            shutterSpeed = savedSpeed
        }
    }

    val shutterSpeeds = listOf("1/30", "1/60", "1/125", "1/250", "1/500", "1/1000")

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )

    LaunchedEffect(Unit) {
        launcher.launch(Manifest.permission.CAMERA)
    }
    
    // Función para actualizar zoom dinámicamente
    fun updateCameraZoom(newZoom: Float) {
        cameraZoomLevel = newZoom
        cameraInstance?.cameraControl?.setZoomRatio(newZoom)
        Log.d("CameraScreen", "🔄 Zoom actualizado dinámicamente: ${newZoom}x")
    }
    
    // Efecto para aplicar zoom dinámicamente sin reinicializar la cámara
    LaunchedEffect(cameraZoomLevel) {
        if (cameraInitialized && isCaptureActive && cameraInstance != null) {
            cameraInstance?.cameraControl?.setZoomRatio(cameraZoomLevel)
            Log.d("CameraScreen", "🔄 Zoom aplicado dinámicamente a vista previa: ${cameraZoomLevel}x")
        }
    }

    val sensorDetected by viewModel.sensorDetected.collectAsState()
    val sensorType by viewModel.sensorType.collectAsState()
    val serverConnectionStatus by viewModel.serverConnectionStatus.collectAsState()
    
    val capturedImagesCount by viewModel.capturedImages.collectAsState()

    // Función local para manejar la captura automática basada en estados
    fun handleAutoCapture(imageCapture: ImageCapture?, currentState: com.api.playeracap.viewmodel.MainViewModel.AutoState) {
        if (!isCaptureActive || captureMode != "Automático") {
            Log.d("CameraScreen", "⚠️ No se puede procesar: activo=$isCaptureActive, modo=$captureMode")
            return
        }
        
        // Ejecutar acción basada en el estado actual
        when (currentState) {
            com.api.playeracap.viewmodel.MainViewModel.AutoState.CAPTURA -> {
                // Estado 2 - CAPTURA
                if (imageCapture == null) {
                    Log.d("CameraScreen", "⚠️ No se puede capturar: cámara no disponible")
                    return
                }
                
                Log.d("CameraScreen", "📸 Iniciando captura automática en estado CAPTURA")
                try {
                    // Capturar la foto directamente
                    takePhoto(
                        context,
                        imageCapture,
                        productionInfo,
                        viewModel
                    )
                    Log.d("CameraScreen", "✅ Foto capturada con éxito en modo automático")
                } catch (e: Exception) {
                    Log.e("CameraScreen", "❌ Error en captura automática: ${e.message}", e)
                }
            }
            com.api.playeracap.viewmodel.MainViewModel.AutoState.SUBIDA -> {
                // Estado 3 - SUBIDA
                val imagesToUpload = viewModel.capturedImages.value.size
                
                if (imagesToUpload > 0) {
                    Log.d("CameraScreen", "📤 Iniciando subida automática en estado SUBIDA")
                    
                    try {
                        viewModel.uploadAndClearImages()
                        
                        // Mostrar mensaje de subida
                        Toast.makeText(
                            context,
                            "Subiendo $imagesToUpload imágenes...",
                            Toast.LENGTH_SHORT
                        ).show()
                        
                        Log.d("CameraScreen", "📤 Subida automática iniciada con éxito")
                    } catch (e: Exception) {
                        Log.e("CameraScreen", "❌ Error al iniciar subida automática: ${e.message}", e)
                    }
                } else {
                    Log.d("CameraScreen", "⏭️ No hay imágenes para subir en estado SUBIDA")
                }
            }
            com.api.playeracap.viewmodel.MainViewModel.AutoState.ESPERA -> {
                // Estado 1 - ESPERA
                Log.d("CameraScreen", "⏭️ Estado ESPERA - esperando próximo pulso")
            }
        }
    }

    if (!isProductionInfoSet) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Información de Producción Requerida",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Por favor, ingrese la información de producción en la pestaña de inicio antes de usar la cámara.",
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCameraConfigMenu = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Configuración de Cámara"
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
        // Contenido en un ScrollView vertical
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Indicador del tipo de sensor activo y línea de producción
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Primera fila: Sensor y Línea
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Sensor Activo
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Sensor:",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(
                                        color = if (sensorDetected) Color.Green else Color.Red,
                                        shape = CircleShape
                                    )
                            )
                            Text(
                                text = when (sensorType) {
                                    SensorType.SIMULATED -> "Simulado"
                                    SensorType.BLUETOOTH -> "Real"
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        
                        // Línea de Producción
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Línea:",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Línea ${extractLineNumber(productionInfo.nasConfig.baseFolder)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                    
                    // Segunda fila: Estado del sensor (opcional, para más detalle)
                    if (sensorType == SensorType.BLUETOOTH) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Estado:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (sensorDetected) "Detectando" else "Esperando",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (sensorDetected) Color.Green else Color.Gray
                            )
                        }
                    }
                }
            }

            // 2. Contador de imágenes con información de estado
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (capturedImagesCount.isNotEmpty()) 
                        MaterialTheme.colorScheme.tertiaryContainer 
                    else 
                        MaterialTheme.colorScheme.secondaryContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Imágenes capturadas: ${capturedImagesCount.size}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    
                    // Mostrar información adicional si hay imágenes pendientes
                    if (capturedImagesCount.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Pendientes de subir",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            
                            // Botón pequeño para limpiar imágenes pendientes
                            TextButton(
                                onClick = { 
                                    viewModel.clearPendingImages()
                                    Toast.makeText(
                                        context,
                                        "Imágenes pendientes eliminadas",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Limpiar",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            // 3. Vista previa de la cámara
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .padding(bottom = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Log.d("CameraScreen", "Camera UI - hasCameraPermission: $hasCameraPermission, isCaptureActive: $isCaptureActive")
                    
                    // Indicador de zoom en la parte superior
                    if (hasCameraPermission && isCaptureActive && cameraZoomLevel > 1f) {
                        Card(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                            )
                        ) {
                            Text(
                                text = "Zoom: ${String.format("%.1f", cameraZoomLevel)}x",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                    
                    if (hasCameraPermission && isCaptureActive) {
                        AndroidView(
                            factory = { ctx ->
                                PreviewView(ctx).apply {
                                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        ) { previewView ->
                            if (!cameraInitialized) {
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                                cameraProviderFuture.addListener({
                                    val cameraProvider = cameraProviderFuture.get()
                                
                                val imageCaptureBuilder = ImageCapture.Builder()
                                    .setTargetRotation(previewView.display.rotation)
                                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                    .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                                
                                try {
                                    imageCapture = imageCaptureBuilder.build()
                                    Log.d("CameraScreen", "ImageCapture creado exitosamente")
                                    
                                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                                    val preview = Preview.Builder()
                                        .build()

                                    try {
                                        cameraProvider.unbindAll()
                                        
                                        // Configurar Camera2Interop y obtener referencia de la cámara
                                        val cameraInstanceLocal = cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            cameraSelector,
                                            preview,
                                            imageCapture
                                        )
                                        // Guardar referencia de la cámara para cambios dinámicos
                                        cameraInstance = cameraInstanceLocal
                                        val camera2Control = Camera2CameraControl.from(cameraInstanceLocal.cameraControl)
                                        
                                        // Aplicar la velocidad de obturación seleccionada
                                        val exposureTime = when (shutterSpeed) {
                                            "1/30" -> 33333333L
                                            "1/60" -> 16666667L
                                            "1/125" -> 8000000L
                                            "1/250" -> 4000000L
                                            "1/500" -> 2000000L
                                            "1/1000" -> 1000000L
                                            else -> 8000000L
                                        }
                                        
                                        camera2Control.captureRequestOptions = CaptureRequestOptions.Builder()
                                            .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureTime)
                                            .build()
                                        
                                        // Aplicar el zoom configurado (afecta tanto vista previa como captura)
                                        if (cameraZoomLevel > 1f) {
                                            cameraInstanceLocal.cameraControl.setZoomRatio(cameraZoomLevel)
                                            Log.d("CameraScreen", "✅ Zoom aplicado a vista previa y captura: ${cameraZoomLevel}x")
                                        }
                                        
                                        preview.setSurfaceProvider(previewView.surfaceProvider)
                                        cameraInitialized = true
                                        Log.d("CameraScreen", "✅ Cámara iniciada correctamente con velocidad de obturación: $shutterSpeed y zoom: ${cameraZoomLevel}x")
                                    } catch (e: Exception) {
                                        Log.e("CameraScreen", "❌ Error al iniciar la cámara: ${e.message}", e)
                                        Toast.makeText(
                                            context,
                                            "Error al iniciar la cámara: ${e.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                } catch (e: Exception) {
                                    Log.e("CameraScreen", "❌ Error al construir ImageCapture: ${e.message}", e)
                                    Toast.makeText(
                                        context,
                                        "Error al configurar la cámara: ${e.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }, ContextCompat.getMainExecutor(context))
                            }
                        }
                    } else {
                        Text(
                            text = if (!hasCameraPermission) 
                                "Se requiere permiso de cámara"
                            else if (!isCaptureActive)
                                "Presione 'INICIAR' para comenzar"
                            else
                                "",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            // 4. Botones de control
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Fila de botones principales (movida arriba)
                    // En modo Manual: dos botones (Iniciar/Detener y Subir)
                    // En modo Automático: un solo botón (Iniciar/Detener proceso completo)
                    if (captureMode == "Manual") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Botón de iniciar/detener captura
                            Button(
                                onClick = {
                                    Log.d("CameraScreen", "Botón Iniciar/Detener presionado - isCaptureActive era: $isCaptureActive")
                                    isCaptureActive = !isCaptureActive
                                    if (!isCaptureActive) {
                                        cameraInitialized = false // Resetear para permitir reinicialización
                                    }
                                    Log.d("CameraScreen", "Botón Iniciar/Detener presionado - isCaptureActive ahora es: $isCaptureActive")
                                    viewModel.updateSensorStatus(isCaptureActive)
                                    
                                    // Mostrar mensaje al iniciar o detener
                                    Toast.makeText(
                                        context,
                                        if (isCaptureActive) 
                                            "Proceso de captura manual iniciado"
                                        else 
                                            "Proceso detenido",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isCaptureActive)
                                        MaterialTheme.colorScheme.error
                                    else
                                        MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(60.dp) // Altura fija para el botón
                            ) {
                                Text(
                                    text = if (isCaptureActive) "Detener" else "Iniciar",
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                            
                            // Botón para subir imágenes manualmente - SIEMPRE visible en modo Manual
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                            ) {
                                Button(
                                    onClick = {
                                        Log.d("CameraScreen", "Iniciando subida manual de imágenes")
                                        // Verificar el estado de subida a través del estado observable
                                        val uploadStatus = viewModel.uploadStatus.value
                                        if (uploadStatus is UploadStatus.Uploading) {
                                            Toast.makeText(
                                                context,
                                                "Ya hay una subida en progreso",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else if (capturedImagesCount.isEmpty()) {
                                            Toast.makeText(
                                                context,
                                                "No hay imágenes para subir",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            viewModel.uploadAndClearImages()
                                            Toast.makeText(
                                                context,
                                                "Subiendo ${capturedImagesCount.size} imágenes...",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.tertiary
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(60.dp)
                                ) {
                                    Text(
                                        text = if (capturedImagesCount.isNotEmpty()) 
                                            "Subir (${capturedImagesCount.size})" 
                                        else 
                                            "Subir",
                                        style = MaterialTheme.typography.titleLarge,
                                        maxLines = 1
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                val dest = if (productionInfo.serverType == com.api.playeracap.data.ServerType.SYNOLOGY_NAS) "NAS" else "Backblaze"
                                Text(
                                    text = "Destino: $dest",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    } else {
                        // Modo Automático: Un solo botón para iniciar/detener todo el proceso
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Indicador de estado cíclico (visible solo cuando está activo)
                            val currentState by viewModel.currentAutoState.collectAsState()
                            val pulseIgnored by viewModel.pulseIgnored.collectAsState()
                            
                            if (isCaptureActive) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 4.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Indicador visual para pulsos ignorados
                                    if (pulseIgnored) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(
                                                    color = Color.Red,
                                                    shape = CircleShape
                                                )
                                        )
                                        Text(
                                            text = " ¡Muy rápido! ",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Red
                                        )
                                    } else {
                                        // Estado actual del sistema cíclico
                                        val stateText = when (currentState) {
                                            com.api.playeracap.viewmodel.MainViewModel.AutoState.ESPERA -> "1 - ESPERA"
                                            com.api.playeracap.viewmodel.MainViewModel.AutoState.CAPTURA -> "2 - CAPTURA"
                                            com.api.playeracap.viewmodel.MainViewModel.AutoState.SUBIDA -> "3 - SUBIDA"
                                        }
                                        
                                        val stateColor = when (currentState) {
                                            com.api.playeracap.viewmodel.MainViewModel.AutoState.ESPERA -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) // Espera
                                            com.api.playeracap.viewmodel.MainViewModel.AutoState.CAPTURA -> MaterialTheme.colorScheme.primary // Captura
                                            com.api.playeracap.viewmodel.MainViewModel.AutoState.SUBIDA -> MaterialTheme.colorScheme.tertiary // Subida
                                        }
                                        
                                        Text(
                                            text = "Estado: $stateText",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = stateColor,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                    }
                                    
                                    // Añadir indicador de tiempo mínimo entre pulsos
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(
                                                color = if (sensorDetected) 
                                                            Color.Green.copy(alpha = 0.7f) 
                                                        else 
                                                            Color.Red.copy(alpha = 0.7f),
                                                shape = CircleShape
                                            )
                                    )
                                    
                                    Spacer(modifier = Modifier.width(8.dp))
                                    
                                    // Botón pequeño para probar el sistema de captura
                                    TextButton(
                                        onClick = { 
                                            // Probar el estado actual del sistema
                                            val currentState = viewModel.currentAutoState.value
                                            handleAutoCapture(imageCapture, currentState)
                                        },
                                        modifier = Modifier
                                            .height(24.dp)
                                            .padding(horizontal = 2.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp)
                                    ) {
                                        Text(
                                            text = "Probar",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.width(4.dp))
                                    
                                    // Botón para limpiar recursos cuando hay problemas
                                    TextButton(
                                        onClick = { 
                                            viewModel.cleanupAndReset()
                                            Toast.makeText(
                                                context,
                                                "Recursos limpiados y estados reiniciados",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        modifier = Modifier
                                            .height(24.dp)
                                            .padding(horizontal = 2.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp)
                                    ) {
                                        Text(
                                            text = "Limpiar",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                            
                            // Botón simple para iniciar/detener el proceso automático
                            Button(
                                onClick = {
                                    isCaptureActive = !isCaptureActive
                                    if (!isCaptureActive) {
                                        cameraInitialized = false // Resetear para permitir reinicialización
                                    }
                                    
                                    // Si estamos activando
                                    if (isCaptureActive) {
                                        // Iniciar el sensor y el modo automático
                                        viewModel.updateSensorStatus(true)
                                        viewModel.setAutoModeActive(true)
                                        
                                        val pendingCount = capturedImagesCount.size
                                        val message = if (pendingCount > 0) {
                                            "Proceso iniciado. Subiendo $pendingCount imágenes pendientes..."
                                        } else {
                                            "Proceso automático iniciado: ESPERA → CAPTURA → SUBIDA → ESPERA..."
                                        }
                                        
                                        Toast.makeText(
                                            context,
                                            message,
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        // Detener el sensor y el modo automático
                                        viewModel.updateSensorStatus(false)
                                        viewModel.setAutoModeActive(false)
                                        
                                        Toast.makeText(
                                            context,
                                            "Proceso detenido",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isCaptureActive)
                                        MaterialTheme.colorScheme.error
                                    else
                                        MaterialTheme.colorScheme.tertiary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp)
                            ) {
                                Text(
                                    text = if (isCaptureActive) "Detener Proceso" else "Iniciar Captura y Subida Automática",
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Selector de modo de captura (ahora después de los botones)
                    Text(
                        text = "Modo de captura:",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Tarjeta para modo Automático (ahora primero)
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { captureMode = "Automático" },
                            colors = CardDefaults.cardColors(
                                containerColor = if (captureMode == "Automático") 
                                    MaterialTheme.colorScheme.primaryContainer 
                                else 
                                    MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (captureMode == "Automático")
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.outline
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start
                            ) {
                                RadioButton(
                                    selected = captureMode == "Automático",
                                    onClick = { captureMode = "Automático" },
                                    modifier = Modifier.size(32.dp),
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Text(
                                    text = "Automático",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(start = 4.dp),
                                    maxLines = 1
                                )
                            }
                        }
                        
                        // Tarjeta para modo Manual (ahora segundo)
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { captureMode = "Manual" },
                            colors = CardDefaults.cardColors(
                                containerColor = if (captureMode == "Manual") 
                                    MaterialTheme.colorScheme.primaryContainer 
                                else 
                                    MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (captureMode == "Manual")
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.outline
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start
                            ) {
                                RadioButton(
                                    selected = captureMode == "Manual",
                                    onClick = { captureMode = "Manual" },
                                    modifier = Modifier.size(32.dp),
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Text(
                                    text = "Manual",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Controles de zoom
                    if (hasCameraPermission && isCaptureActive) {
                        Text(
                            text = "Control de Zoom:",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        )
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Botón zoom out
                            IconButton(
                                onClick = {
                                    val newZoom = (cameraZoomLevel - 0.5f).coerceAtLeast(1f)
                                    updateCameraZoom(newZoom)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .background(
                                        MaterialTheme.colorScheme.secondaryContainer,
                                        RoundedCornerShape(8.dp)
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ZoomOut,
                                    contentDescription = "Alejar zoom",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            
                            // Indicador de zoom
                            Card(
                                modifier = Modifier
                                    .weight(2f)
                                    .height(48.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${String.format("%.1f", cameraZoomLevel)}x",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            
                            // Botón zoom in
                            IconButton(
                                onClick = {
                                    val newZoom = (cameraZoomLevel + 0.5f).coerceAtMost(10f)
                                    updateCameraZoom(newZoom)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .background(
                                        MaterialTheme.colorScheme.secondaryContainer,
                                        RoundedCornerShape(8.dp)
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ZoomIn,
                                    contentDescription = "Acercar zoom",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        
                        // Botón para resetear zoom
                        TextButton(
                            onClick = { updateCameraZoom(1f) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Resetear zoom a 1x",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }


                }
            }

        }
    }

    // 5. Diálogo de estado de subida
    val uploadStatus by viewModel.uploadStatus.collectAsState()
    when (val status = uploadStatus) {
            is UploadStatus.Uploading -> {
                Dialog(onDismissRequest = { }) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Subiendo imagen...")
                        }
                    }
                }
            }
            is UploadStatus.Error -> {
                LaunchedEffect(status) {
                    Toast.makeText(
                        context,
                        "Error al subir: ${status.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    viewModel.resetUploadStatus()
                }
            }
            else -> Unit
        }
    }

    // Efecto para manejar la captura automática
    LaunchedEffect(sensorDetected) {
        Log.d("CameraScreen", "LaunchedEffect triggered - isCaptureActive: $isCaptureActive, imageCapture: ${imageCapture != null}, sensorDetected: $sensorDetected")
        if (isCaptureActive && imageCapture != null) {
            if (captureMode == "Manual") {
                // Modo Manual: Capturar según la lógica configurada
                try {
                    // Obtener configuración del sensor Bluetooth
                    val calibrationDelay = if (sensorType == SensorType.BLUETOOTH) {
                        viewModel.getBluetoothSensorCalibration()
                    } else {
                        100L // Delay por defecto para sensor simulado
                    }
                    
                    val sensorLogic = if (sensorType == SensorType.BLUETOOTH) {
                        viewModel.getBluetoothSensorLogic()
                    } else {
                        "bajada" // Lógica por defecto para sensor simulado
                    }
                    
                    // Determinar cuándo capturar según la lógica configurada
                    val shouldCaptureNow = when (sensorLogic) {
                        "subida" -> sensorDetected // Capturar cuando el sensor se activa
                        "bajada" -> !sensorDetected // Capturar cuando el sensor se desactiva (comportamiento original)
                        else -> !sensorDetected // Por defecto, comportamiento original
                    }
                    
                    if (shouldCaptureNow) {
                        Log.d("CameraScreen", "🎯 Aplicando delay de calibración: ${calibrationDelay}ms (lógica: $sensorLogic)")
                        delay(calibrationDelay) // Delay configurable para calibración
                        takePhoto(
                            context,
                            imageCapture!!,
                            productionInfo,
                            viewModel
                        )
                    }
                } catch (e: Exception) {
                    Log.e("CameraScreen", "❌ Error en captura manual", e)
                }
            } else if (captureMode == "Automático") {
                // Modo Automático: Procesar pulso usando el nuevo sistema
                // En cada cambio de sensorDetected, intentamos procesar el pulso
                // La función processAutoModePulse decide si es un flanco descendente válido
                Log.d("CameraScreen", "Detectado cambio en sensor: $sensorDetected (enviando a processAutoModePulse)")
                viewModel.processAutoModePulse(System.currentTimeMillis())
            }
        }
    }
    
    // Efecto para detectar cuándo se debe ejecutar una acción en modo automático mediante el estado actual
    val currentState by viewModel.currentAutoState.collectAsState()
    LaunchedEffect(currentState) {
        if (captureMode == "Automático" && isCaptureActive && imageCapture != null) {
            handleAutoCapture(imageCapture, currentState)
        }
    }
    
    // Ya no usamos shouldCapture para iniciar capturas, pero mantenemos este efecto solo para resetear el flag si es necesario
    val shouldCapture by viewModel.shouldCapture.collectAsState()
    LaunchedEffect(shouldCapture) {
        if (shouldCapture) {
            Log.d("CameraScreen", "🔄 Reiniciando flag shouldCapture que estaba en true")
            viewModel.resetCaptureFlag()
        }
    }
    
    // Menú de configuración de cámara
    if (showCameraConfigMenu) {
        CameraConfigDialog(
            shutterSpeed = shutterSpeed,
            shutterSpeeds = shutterSpeeds,
            capturedImages = capturedImagesCount,
            onShutterSpeedChange = { speed: String ->
                shutterSpeed = speed
                scope.launch {
                    userPreferences.saveShutterSpeed(speed)
                }
            },
            onImagePreview = { uri: Uri ->
                selectedImageUri = uri
                showFullImagePreview = true
            },
            onFullscreenPreview = {
                showFullscreenCameraPreview = true
                showCameraConfigMenu = false
            },
            onDismiss = { showCameraConfigMenu = false }
        )
    }
    
    // Vista previa de cámara en pantalla completa
    if (showFullscreenCameraPreview) {
        FullscreenCameraPreview(
            initialZoom = cameraZoomLevel,
            onZoomChanged = { newZoom -> cameraZoomLevel = newZoom },
            onDismiss = { showFullscreenCameraPreview = false }
        )
    }
    
    // Vista previa de imagen completa
    if (showFullImagePreview && selectedImageUri != null) {
        FullImagePreviewDialog(
            imageUri = selectedImageUri!!,
            onDismiss = { 
                showFullImagePreview = false
                selectedImageUri = null
            }
        )
    }
}

@Composable
fun CameraConfigDialog(
    shutterSpeed: String,
    shutterSpeeds: List<String>,
    capturedImages: List<com.api.playeracap.viewmodel.CapturedImage>,
    onShutterSpeedChange: (String) -> Unit,
    onImagePreview: (Uri) -> Unit,
    onFullscreenPreview: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Encabezado
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Configuración de Cámara",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar"
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Sección de vista previa completa
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Vista Previa Completa",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Text(
                            text = "Ver la cámara en pantalla completa para posicionamiento del dispositivo",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        Button(
                            onClick = onFullscreenPreview,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Vista completa",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Abrir Vista Completa",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
                
                // Sección de velocidad de obturación
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Velocidad de Obturación",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        Text(
                            text = "Actual: $shutterSpeed",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        // Selector de velocidad de obturación
                        var expandedShutter by remember { mutableStateOf(false) }
                        
                        Box(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = { expandedShutter = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = shutterSpeed,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Icon(
                                        imageVector = if (expandedShutter) 
                                            Icons.Default.ExpandLess 
                                        else 
                                            Icons.Default.ExpandMore,
                                        contentDescription = "Expandir selector"
                                    )
                                }
                            }
                            
                            DropdownMenu(
                                expanded = expandedShutter,
                                onDismissRequest = { expandedShutter = false },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                shutterSpeeds.forEach { speed ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = speed,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = if (speed == shutterSpeed) 
                                                    MaterialTheme.colorScheme.primary 
                                                else 
                                                    MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        onClick = {
                                            onShutterSpeedChange(speed)
                                            expandedShutter = false
                                        },
                                        leadingIcon = if (speed == shutterSpeed) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Seleccionado",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        } else null
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Sección de imágenes capturadas
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Imágenes Capturadas (${capturedImages.size})",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        if (capturedImages.isEmpty()) {
                            Text(
                                text = "No hay imágenes capturadas",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                                textAlign = TextAlign.Center
                            )
                        } else {
                            // Grid de miniaturas de imágenes
                            val chunkedImages = capturedImages.chunked(2)
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                chunkedImages.forEach { rowImages ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        rowImages.forEach { image ->
                                            Card(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .aspectRatio(1f)
                                                    .clickable { onImagePreview(image.uri) },
                                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    // Aquí podrías cargar la imagen real usando Coil
                                                    Icon(
                                                        imageVector = Icons.Default.CameraAlt,
                                                        contentDescription = "Imagen capturada",
                                                        modifier = Modifier.size(48.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                        // Rellenar espacio si la fila no está completa
                                        repeat(2 - rowImages.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FullImagePreviewDialog(
    imageUri: Uri,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // Aquí deberías usar una librería como Coil para cargar la imagen
                // Por ahora, mostramos un placeholder
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Vista previa de imagen",
                        modifier = Modifier.size(120.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Vista previa de imagen completa",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "URI: ${imageUri.lastPathSegment}",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Botón cerrar en la esquina superior derecha
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar vista previa"
                    )
                }
            }
        }
    }
}

@Composable
fun FullscreenCameraPreview(
    initialZoom: Float = 1f,
    onZoomChanged: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    // Estados para el zoom
    var zoomLevel by remember { mutableStateOf(initialZoom) }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    val minZoom = 1f
    val maxZoom = remember { mutableStateOf(10f) }
    
    // Efecto para sincronizar el zoom inicial cuando cambie
    LaunchedEffect(initialZoom) {
        if (camera != null && initialZoom != zoomLevel) {
            zoomLevel = initialZoom
            camera?.cameraControl?.setZoomRatio(initialZoom)
            Log.d("FullscreenCamera", "🔄 Zoom sincronizado desde vista principal: ${initialZoom}x")
        }
    }
    
    // Función para actualizar zoom y notificar al padre
    fun updateZoom(newZoom: Float) {
        zoomLevel = newZoom
        onZoomChanged(newZoom)
        camera?.cameraControl?.setZoomRatio(newZoom)
    }

    // Dialog de pantalla completa
    Dialog(
        onDismissRequest = onDismiss
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (hasCameraPermission) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, _, zoom, _ ->
                                val newZoom = (zoomLevel * zoom).coerceIn(minZoom, maxZoom.value)
                                if (newZoom != zoomLevel) {
                                    updateZoom(newZoom)
                                }
                            }
                        }
                ) { previewView ->
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        
                        val preview = Preview.Builder()
                            .build()

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        try {
                            cameraProvider.unbindAll()
                            val cameraInstance = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview
                            )
                            
                            // Obtener referencia de la cámara y configurar zoom máximo
                            camera = cameraInstance
                            val cameraInfo = cameraInstance.cameraInfo
                            maxZoom.value = cameraInfo.zoomState.value?.maxZoomRatio ?: 10f
                            
                            // Aplicar el zoom inicial si es mayor a 1x
                            if (zoomLevel > 1f) {
                                cameraInstance.cameraControl.setZoomRatio(zoomLevel)
                                Log.d("FullscreenCamera", "✅ Zoom inicial aplicado: ${zoomLevel}x")
                            }
                            
                            preview.setSurfaceProvider(previewView.surfaceProvider)
                            
                            Log.d("FullscreenCamera", "Cámara iniciada - Zoom máximo: ${maxZoom.value}")
                            
                        } catch (e: Exception) {
                            Log.e("FullscreenCamera", "Error al iniciar vista previa: ${e.message}", e)
                        }
                    }, ContextCompat.getMainExecutor(context))
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Se requiere permiso de cámara",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
            
            // Controles de zoom en el lado izquierdo
            if (hasCameraPermission && camera != null) {
                Card(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Black.copy(alpha = 0.7f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Botón zoom in
                        IconButton(
                            onClick = {
                                val newZoom = (zoomLevel + 0.5f).coerceAtMost(maxZoom.value)
                                if (newZoom != zoomLevel) {
                                    updateZoom(newZoom)
                                }
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    Color.White.copy(alpha = 0.2f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.ZoomIn,
                                contentDescription = "Acercar zoom",
                                tint = Color.White
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Slider de zoom vertical
                        Slider(
                            value = zoomLevel,
                            onValueChange = { newZoom ->
                                updateZoom(newZoom)
                            },
                            valueRange = minZoom..maxZoom.value,
                            modifier = Modifier
                                .height(120.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Botón zoom out
                        IconButton(
                            onClick = {
                                val newZoom = (zoomLevel - 0.5f).coerceAtLeast(minZoom)
                                if (newZoom != zoomLevel) {
                                    updateZoom(newZoom)
                                }
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    Color.White.copy(alpha = 0.2f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.ZoomOut,
                                contentDescription = "Alejar zoom",
                                tint = Color.White
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Indicador de nivel de zoom (clickeable para reset)
                        Text(
                            text = "${String.format("%.1f", zoomLevel)}x",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .clickable {
                                    updateZoom(1f)
                                }
                                .background(
                                    Color.White.copy(alpha = 0.2f),
                                    CircleShape
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            
            // Botón de cerrar en la esquina superior derecha
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(
                        Color.Black.copy(alpha = 0.5f),
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cerrar vista previa",
                    tint = Color.White
                )
            }
            
            // Texto informativo en la parte inferior
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.7f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Vista previa para posicionamiento del dispositivo",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    if (hasCameraPermission && camera != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Pellizca para hacer zoom • Toca ${String.format("%.1f", zoomLevel)}x para resetear",
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
} 