package com.api.playeracap.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.api.playeracap.viewmodel.MainViewModel
import com.api.playeracap.viewmodel.StorageStatus
import android.os.StatFs
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.ui.graphics.Color
import com.api.playeracap.viewmodel.DeleteImagesResult
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import com.api.playeracap.viewmodel.MainViewModel.DeletePhysicalFilesResult

@Composable
fun DeviceStatusScreen(viewModel: MainViewModel) {
    val storageStatus by viewModel.storageWarning.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var successMessage by remember { mutableStateOf("") }
    var deleteErrorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Título
        Text(
            text = "Estado del Dispositivo",
            style = MaterialTheme.typography.headlineMedium
        )

        // Información de almacenamiento
        StorageInfoCard()

        // Acciones
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Acciones",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Button(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(
                        Icons.Default.Storage,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Liberar espacio")
                }
            }
        }
    }

    // Diálogo de confirmación de borrado exitoso
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
            title = { Text("Borrado exitoso") },
            text = { Text(successMessage) },
            confirmButton = {
                TextButton(onClick = { showSuccessDialog = false }) {
                    Text("Aceptar")
                }
            }
        )
    }

    // Diálogo de confirmación de borrado
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { 
                if (storageStatus !is StorageStatus.Critical) {
                    showDeleteDialog = false 
                    deleteErrorMessage = null
                }
            },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text("Liberar espacio del dispositivo") },
            text = { 
                Column {
                    Text("Esta acción eliminará permanentemente las imágenes almacenadas en el dispositivo.")
                    deleteErrorMessage?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            when (val result = viewModel.deletePhysicalFiles()) {
                                is DeletePhysicalFilesResult.Success -> {
                                    showDeleteDialog = false
                                    successMessage = "${result.count} archivos han sido borrados permanentemente"
                                    showSuccessDialog = true
                                }
                                is DeletePhysicalFilesResult.Error -> {
                                    deleteErrorMessage = result.message
                                }
                            }
                        }
                    }
                ) {
                    Text("Borrar")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showDeleteDialog = false
                    deleteErrorMessage = null
                }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun StorageInfoCard() {
    val stat = StatFs(Environment.getExternalStorageDirectory().path)
    val available = stat.availableBytes.toFloat()
    val total = stat.totalBytes.toFloat()
    val used = total - available
    val usedPercentage = (used * 100 / total).roundToInt()
    
    // Definir colores para la barra de progreso
    val progressColor = when {
        usedPercentage >= 95 -> MaterialTheme.colorScheme.error
        usedPercentage >= 80 -> Color(0xFFFFA000) // Color ámbar para advertencia
        else -> MaterialTheme.colorScheme.primary
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Almacenamiento",
                style = MaterialTheme.typography.titleMedium
            )
            
            // Barra de progreso
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(
                        MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(12.dp)
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(usedPercentage / 100f)
                        .fillMaxHeight()
                        .background(
                            progressColor,
                            RoundedCornerShape(12.dp)
                        )
                )
                Text(
                    text = "$usedPercentage%",
                    modifier = Modifier
                        .align(Alignment.Center),
                    color = if (usedPercentage >= 50) Color.White else Color.Black
                )
            }

            // Detalles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StorageDetail("Usado", used)
                StorageDetail("Disponible", available)
                StorageDetail("Total", total)
            }
        }
    }
}

@Composable
private fun StorageDetail(label: String, bytes: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = formatBytes(bytes),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun formatBytes(bytes: Float): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes
    var unitIndex = 0
    while (value > 1024 && unitIndex < units.size - 1) {
        value /= 1024
        unitIndex++
    }
    return "%.1f %s".format(value, units[unitIndex])
} 