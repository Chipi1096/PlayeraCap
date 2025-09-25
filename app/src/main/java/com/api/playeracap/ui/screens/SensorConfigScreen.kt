import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import android.bluetooth.BluetoothDevice
import java.text.SimpleDateFormat
import java.util.*
import com.api.playeracap.viewmodel.MainViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.api.playeracap.utils.PermissionManager
import com.api.playeracap.sensor.SensorType
import android.content.Context
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import com.api.playeracap.utils.AutoReconnectTest
import com.api.playeracap.data.UserPreferences

@Composable
fun SensorConfigScreen(
    viewModel: MainViewModel,
    navController: NavController
) {
    // Obtener el valor del intervalo actual
    val currentInterval = viewModel.simulatedInterval.collectAsState().value
    
    // Inicializar los estados usando el valor obtenido
    var selectedInterval by remember { mutableStateOf(currentInterval) }
    var tempInterval by remember { mutableStateOf(currentInterval) }
    var isScanning by remember { mutableStateOf(false) }
    val foundDevices by viewModel.foundDevices.collectAsStateWithLifecycle(initialValue = emptyList())
    var showPermissionDialog by remember { mutableStateOf(false) }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            isScanning = true
            viewModel.startBluetoothScan()
        } else {
            showPermissionDialog = true
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Text(
                text = "Configuración del Sensor",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        item {
            // Selector de tipo de sensor
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Tipo de Sensor",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        RadioButton(
                            selected = viewModel.sensorType.collectAsState().value == SensorType.SIMULATED,
                            onClick = { viewModel.setSensorType(SensorType.SIMULATED) }
                        )
                        Text("Simulado")
                        RadioButton(
                            selected = viewModel.sensorType.collectAsState().value == SensorType.BLUETOOTH,
                            onClick = { viewModel.setSensorType(SensorType.BLUETOOTH) }
                        )
                        Text("Bluetooth")
                    }
                }
            }
        }

        item {
            // Configuración del sensor simulado
            AnimatedVisibility(
                visible = viewModel.sensorType.collectAsState().value == SensorType.SIMULATED
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Configuración Sensor Simulado",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Intervalo de lectura (ms): ${tempInterval}")
                        Slider(
                            value = tempInterval.toFloat(),
                            onValueChange = { 
                                tempInterval = it.toLong()
                            },
                            valueRange = 3000f..10000f,
                            steps = 7,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Intervalo: ${tempInterval/1000} segundos",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { 
                                selectedInterval = tempInterval
                                viewModel.setSimulatedInterval(tempInterval)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Aplicar intervalo")
                        }
                        
                        Text(
                            text = "Intervalo actual configurado: ${selectedInterval} ms",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }

        item {
            // Configuración del sensor Bluetooth
            AnimatedVisibility(
                visible = viewModel.sensorType.collectAsState().value == SensorType.BLUETOOTH
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Configuración Bluetooth",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Información del dispositivo conectado
                        val deviceInfo by viewModel.deviceInfo.collectAsState()
                        val isConnected by viewModel.isConnected.collectAsState()
                        
                        if (isConnected) {
                            deviceInfo?.let { info ->
                                Divider(modifier = Modifier.padding(vertical = 8.dp))
                                
                                Text(
                                    text = "Dispositivo Conectado:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bluetooth,
                                        contentDescription = "Bluetooth",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = info.name,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "MAC: ${info.address}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = when {
                                                    info.rssi > -60 -> "Excelente"
                                                    info.rssi > -70 -> "Buena"
                                                    info.rssi > -80 -> "Regular"
                                                    info.rssi > -90 -> "Débil"
                                                    else -> "Muy débil"
                                                },
                                                color = when {
                                                    info.rssi > -60 -> Color.Green
                                                    info.rssi > -70 -> Color(0xFF8BC34A)
                                                    info.rssi > -80 -> Color(0xFFFFC107)
                                                    else -> Color(0xFFFF5722)
                                                },
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "${info.rssi} dBm",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            } ?: run {
                                Divider(modifier = Modifier.padding(vertical = 8.dp))
                                
                                Text(
                                    text = "Dispositivo Conectado:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bluetooth,
                                        contentDescription = "Bluetooth",
                                        tint = Color.Green
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Dispositivo Bluetooth conectado",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Green
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "No hay dispositivo conectado",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Button(
                            onClick = { 
                                if (!viewModel.hasBluetoothPermissions()) {
                                    permissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.BLUETOOTH_SCAN,
                                            Manifest.permission.BLUETOOTH_CONNECT,
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                } else {
                                    isScanning = !isScanning
                                    if (isScanning) {
                                        viewModel.startBluetoothScan()
                                    } else {
                                        viewModel.stopBluetoothScan()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isScanning) "Detener búsqueda" else "Buscar dispositivos")
                        }

                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(24.dp)
                                    .padding(4.dp)
                            )
                        }

                        // Lista de dispositivos encontrados
                        foundDevices.forEach { device ->
                            DeviceItem(
                                device = device,
                                onClick = {
                                    viewModel.connectBluetoothDevice(device.address)
                                    isScanning = false
                                }
                            )
                        }
                    }
                }
            }
        }

        item {
            // Configuración de reconexión automática
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Reconexión Automática",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Información del último dispositivo conectado
                    val lastDeviceAddress = remember { mutableStateOf<String?>(null) }
                    val lastDeviceName = remember { mutableStateOf<String?>(null) }
                    val lastConnectionTime = remember { mutableStateOf<Long>(0L) }
                    
                    val context = LocalContext.current
                    val scope = rememberCoroutineScope()
                    LaunchedEffect(Unit) {
                        val userPreferences = UserPreferences(context)
                        lastDeviceAddress.value = userPreferences.getLastConnectedDeviceAddress()
                        lastDeviceName.value = userPreferences.getLastConnectedDeviceName()
                        lastConnectionTime.value = userPreferences.getLastConnectionTime()
                    }
                    
                    lastDeviceAddress.value?.let { address ->
                        lastDeviceName.value?.let { name ->
                            Text(
                                text = "Último dispositivo: $name",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "MAC: $address",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (lastConnectionTime.value > 0) {
                                Text(
                                    text = "Conectado: ${
                                        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                                            .format(Date(lastConnectionTime.value))
                                    }",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Button(
                                onClick = { viewModel.attemptAutoReconnect() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                )
                            ) {
                                Text("Reconectar ahora")
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            OutlinedButton(
                                onClick = {
                                    val userPrefs = UserPreferences(context)
                                    userPrefs.clearLastConnectedDevice()
                                    lastDeviceAddress.value = null
                                    lastDeviceName.value = null
                                    lastConnectionTime.value = 0L
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Limpiar dispositivo guardado")
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            OutlinedButton(
                                onClick = {
                                    val autoReconnectTest = AutoReconnectTest(context)
                                    autoReconnectTest.runAllTests(scope)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                )
                            ) {
                                Text("🧪 Probar Reconexión Automática")
                            }
                        }
                    } ?: Text(
                        text = "No hay dispositivo guardado",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            // Calibración del Sensor Bluetooth
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "🎯 Calibración del Sensor Bluetooth",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Verificar si el sensor Bluetooth está activo
                    val isBluetoothActive = viewModel.sensorType.collectAsState().value == SensorType.BLUETOOTH
                    
                    if (!isBluetoothActive) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Text(
                                    text = "⚠️ Sensor Bluetooth no activo",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "Para usar la calibración, primero selecciona 'Sensor Bluetooth' en la sección de arriba.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    
                    Text(
                        text = "Ajusta el tiempo de delay para la captura de imagen. Esto te permite sincronizar mejor el momento exacto de la captura con la detección del sensor.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Estado actual de la calibración
                    var currentCalibration by remember { mutableStateOf(0L) }
                    LaunchedEffect(Unit) {
                        currentCalibration = viewModel.getBluetoothSensorCalibration()
                    }
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "Configuración Actual:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (currentCalibration == 0L) 
                                    "Sin delay (configuración por defecto)" 
                                else 
                                    "Delay: ${currentCalibration}ms",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                                         // Estado actual de la lógica (mover aquí para que esté disponible en el botón Restablecer)
                     var currentLogic by remember { mutableStateOf("bajada") }
                     LaunchedEffect(Unit) {
                         currentLogic = viewModel.getBluetoothSensorLogic()
                     }
                     
                     // Slider para ajustar el delay
                     var sliderValue by remember { mutableStateOf(currentCalibration.toFloat()) }
                     
                     Text(
                         text = "Delay de Captura: ${sliderValue.toInt()}ms",
                         style = MaterialTheme.typography.bodyMedium,
                         fontWeight = FontWeight.Bold
                     )
                     
                     Slider(
                         value = sliderValue,
                         onValueChange = { sliderValue = it },
                         valueRange = 0f..2000f, // Aumentado a 2000ms
                         steps = 199, // 10ms por paso (2000/10 = 200 pasos, steps = 199)
                         modifier = Modifier.fillMaxWidth(),
                         enabled = isBluetoothActive
                     )
                     
                     // Botones de control
                     Row(
                         modifier = Modifier.fillMaxWidth(),
                         horizontalArrangement = Arrangement.spacedBy(8.dp)
                     ) {
                         Button(
                             onClick = {
                                 val newDelay = sliderValue.toLong()
                                 viewModel.saveBluetoothSensorCalibration(newDelay)
                                 currentCalibration = newDelay
                             },
                             modifier = Modifier.weight(1f),
                             colors = ButtonDefaults.buttonColors(
                                 containerColor = MaterialTheme.colorScheme.primary
                             ),
                             enabled = isBluetoothActive
                         ) {
                             Text("Aplicar")
                         }
                         
                         OutlinedButton(
                             onClick = {
                                 viewModel.resetBluetoothSensorCalibration()
                                 viewModel.resetBluetoothSensorLogic()
                                 sliderValue = 0f
                                 currentCalibration = 0L
                                 currentLogic = "bajada"
                             },
                             modifier = Modifier.weight(1f),
                             colors = ButtonDefaults.outlinedButtonColors(
                                 containerColor = MaterialTheme.colorScheme.errorContainer
                             ),
                             enabled = isBluetoothActive
                         ) {
                             Text("Restablecer")
                         }
                     }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Configuración de lógica de pulso
                    Text(
                        text = "Lógica de Pulso",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "Lógica Actual:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (currentLogic == "subida") 
                                    "Pulso de Subida (cuando el sensor se activa)" 
                                else 
                                    "Pulso de Bajada (cuando el sensor se desactiva)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Botón para invertir la lógica
                    Button(
                        onClick = {
                            val newLogic = if (currentLogic == "bajada") "subida" else "bajada"
                            viewModel.saveBluetoothSensorLogic(newLogic)
                            currentLogic = newLogic
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        ),
                        enabled = isBluetoothActive
                    ) {
                        Text(
                            text = if (currentLogic == "bajada") 
                                "Cambiar a Pulso de Subida" 
                            else 
                                "Cambiar a Pulso de Bajada"
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Información adicional
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "💡 Consejos de Calibración:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "• 0ms: Captura inmediata (configuración actual)",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "• 100-300ms: Para objetos que se mueven rápido",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "• 500-800ms: Para objetos que se mueven lento",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "• 1000-2000ms: Para objetos muy lentos o ajustes finos",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "• Si la captura es muy temprana: Aumenta el delay",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "• Si la captura es muy tardía: Reduce el delay",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "• Lógica de pulso: Ajusta cuándo se activa la captura",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        item {
            // Estado actual del sensor
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Estado del Sensor",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(
                                color = if (viewModel.sensorDetected.collectAsState().value)
                                    Color.Green else Color.Red,
                                shape = CircleShape
                            )
                    )
                    
                    Text(
                        text = if (viewModel.sensorDetected.collectAsState().value)
                            "Objeto detectado" else "No hay objeto detectado"
                    )
                    
                    Text(
                        text = "Última actualización: ${
                            SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                .format(viewModel.lastUpdateTime.collectAsState().value)
                        }"
                    )
                    
                    Text(
                        text = "Estado: ${
                            when {
                                !viewModel.sensorActive.collectAsState().value -> "Inactivo"
                                viewModel.isConnected.collectAsState().value -> "Conectado"
                                else -> "Desconectado"
                            }
                        }"
                    )
                }
            }
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Permisos necesarios") },
            text = { Text("Se requieren permisos de Bluetooth para usar esta función.") },
            confirmButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("Aceptar")
                }
            }
        )
    }
}

@Composable
private fun DeviceItem(
    device: SafeBluetoothDevice,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Bluetooth,
                contentDescription = null,
                modifier = Modifier.padding(end = 12.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

data class SafeBluetoothDevice(
    val device: BluetoothDevice,
    val context: Context
) {
    val name: String
        get() = try {
            if (PermissionManager.hasBluetoothPermissions(context)) {
                device.name ?: "Dispositivo desconocido"
            } else {
                "Dispositivo desconocido"
            }
        } catch (e: SecurityException) {
            "Dispositivo desconocido"
        }

    val address: String
        get() = try {
            if (PermissionManager.hasBluetoothPermissions(context)) {
                device.address
            } else {
                "Dirección no disponible"
            }
        } catch (e: SecurityException) {
            "Dirección no disponible"
        }
} 