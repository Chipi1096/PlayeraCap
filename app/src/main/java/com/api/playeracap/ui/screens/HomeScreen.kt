package com.api.playeracap.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.api.playeracap.data.ProductionInfo
import com.api.playeracap.data.ServerType
import com.api.playeracap.data.NASConfig
import com.api.playeracap.viewmodel.MainViewModel
import com.api.playeracap.data.B2Bucket
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: MainViewModel) {
    // Datos de producción
    var modelo by remember { mutableStateOf(viewModel.productionInfo.value.modelo) }
    var ordenProducto by remember { mutableStateOf(viewModel.productionInfo.value.ordenProducto) }
    var nombreCliente by remember { mutableStateOf(viewModel.productionInfo.value.nombreCliente) }
    var colorTela by remember { mutableStateOf(viewModel.productionInfo.value.colorTela) }
    var encargadoMaquina by remember { mutableStateOf(viewModel.productionInfo.value.encargadoMaquina) }
    
    // Estado de la interfaz
    var isEditing by remember { mutableStateOf(false) }
    var showConfirmation by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    
    // Datos de la lista de buckets
    val buckets by viewModel.buckets.collectAsState()
    val currentBucket by viewModel.productionInfo.collectAsState()
    var showAddBucketDialog by remember { mutableStateOf(false) }
    var showEditBucketDialog by remember { mutableStateOf(false) }
    var bucketToEdit by remember { mutableStateOf<B2Bucket?>(null) }
    
    // Configuración del servidor
    var serverType by remember { mutableStateOf(currentBucket.serverType) }
    var nasConfig by remember { mutableStateOf(currentBucket.nasConfig) }
    var showNASConfigDialog by remember { mutableStateOf(false) }
    var isTestingConnection by remember { mutableStateOf(false) }
    
    // Actualizar valores cuando cambie la información de producción
    LaunchedEffect(currentBucket) {
        serverType = currentBucket.serverType
        nasConfig = currentBucket.nasConfig
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        // Sección 1: Información de Producción
        Text(
            text = "Información de Producción",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            textAlign = TextAlign.Center
        )

        // Botones de acción para información de producción
        if (!isEditing) {
            Button(
                onClick = { isEditing = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text("Modificar Datos de Producción")
            }
        } else {
            Button(
                onClick = {
                    if (modelo.isNotBlank() && ordenProducto.isNotBlank() && 
                        nombreCliente.isNotBlank() && colorTela.isNotBlank() &&
                        encargadoMaquina.isNotBlank()
                    ) {
                        viewModel.updateProductionInfo(
                            ProductionInfo(
                                modelo = modelo,
                                ordenProducto = ordenProducto,
                                nombreCliente = nombreCliente,
                                colorTela = colorTela,
                                encargadoMaquina = encargadoMaquina,
                                bucketId = currentBucket.bucketId,
                                bucketName = currentBucket.bucketName,
                                applicationKeyId = currentBucket.applicationKeyId,
                                applicationKey = currentBucket.applicationKey,
                                selectedBucketId = currentBucket.selectedBucketId,
                                serverType = serverType,
                                nasConfig = nasConfig
                            )
                        )
                        showConfirmation = true
                        isEditing = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Guardar Cambios")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = modelo,
            onValueChange = { if (isEditing) modelo = it },
            label = { Text("Modelo") },
            modifier = Modifier.fillMaxWidth(),
            enabled = isEditing
        )
        
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = ordenProducto,
            onValueChange = { if (isEditing) ordenProducto = it },
            label = { Text("Orden de Producto") },
            modifier = Modifier.fillMaxWidth(),
            enabled = isEditing
        )
        
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = nombreCliente,
            onValueChange = { if (isEditing) nombreCliente = it },
            label = { Text("Nombre del Cliente") },
            modifier = Modifier.fillMaxWidth(),
            enabled = isEditing
        )
        
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = colorTela,
            onValueChange = { if (isEditing) colorTela = it },
            label = { Text("Color de Tela") },
            modifier = Modifier.fillMaxWidth(),
            enabled = isEditing
        )
        
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = encargadoMaquina,
            onValueChange = { if (isEditing) encargadoMaquina = it },
            label = { Text("Encargado de Máquina") },
            modifier = Modifier.fillMaxWidth(),
            enabled = isEditing
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        // Sección 2: Configuración del Bucket
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Buckets Disponibles",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Botón para añadir nuevo bucket
            IconButton(onClick = { showAddBucketDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Añadir bucket",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        // Lista de buckets
        buckets.forEach { bucket ->
            val isSelected = bucket.id == currentBucket.selectedBucketId
            val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = borderColor,
                        shape = MaterialTheme.shapes.medium
                    )
                    .clickable {
                        // Seleccionar este bucket
                        viewModel.updateSelectedBucket(bucket.id)
                    },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = bucket.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "ID: ${bucket.id}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    
                    Row {
                        // Icono de seleccionado
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Seleccionado",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        
                        // No mostrar botones de edición/eliminación para el predeterminado
                        if (!bucket.isDefault) {
                            // Botón de editar
                            IconButton(onClick = { 
                                bucketToEdit = bucket
                                showEditBucketDialog = true
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Editar",
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            
                            // Botón de eliminar
                            IconButton(onClick = { 
                                viewModel.deleteBucket(bucket.id)
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Eliminar",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        // Sección 3: Configuración del Servidor
        Text(
            text = "Configuración del Servidor",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            textAlign = TextAlign.Center
        )
        
        // Selector de tipo de servidor
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
                    text = "Tipo de Servidor:",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Opción Black Baze
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { 
                                serverType = ServerType.BLACK_BAZE
                                viewModel.updateServerType(ServerType.BLACK_BAZE)
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (serverType == ServerType.BLACK_BAZE) 
                                MaterialTheme.colorScheme.primaryContainer 
                            else 
                                MaterialTheme.colorScheme.surface
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (serverType == ServerType.BLACK_BAZE) 2.dp else 1.dp,
                            color = if (serverType == ServerType.BLACK_BAZE)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "🌐 Black Baze",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (serverType == ServerType.BLACK_BAZE) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = "Servidor en la nube",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    
                    // Opción Synology NAS
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { 
                                serverType = ServerType.SYNOLOGY_NAS
                                viewModel.updateServerType(ServerType.SYNOLOGY_NAS)
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (serverType == ServerType.SYNOLOGY_NAS) 
                                MaterialTheme.colorScheme.primaryContainer 
                            else 
                                MaterialTheme.colorScheme.surface
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (serverType == ServerType.SYNOLOGY_NAS) 2.dp else 1.dp,
                            color = if (serverType == ServerType.SYNOLOGY_NAS)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "🏠 Synology NAS",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (serverType == ServerType.SYNOLOGY_NAS) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = "Servidor local",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                
                // Configuración específica para NAS
                if (serverType == ServerType.SYNOLOGY_NAS) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Configuración NAS",
                            style = MaterialTheme.typography.titleMedium
                        )
                        
                        Button(
                            onClick = { showNASConfigDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Text("Configurar")
                        }
                    }
                    
                    // Mostrar estado de la configuración NAS
                    if (nasConfig.isEnabled) {
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
                                    text = "✅ NAS Configurado",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                                                 Text(
                                     text = "Servidor: ${nasConfig.serverAddress}:${nasConfig.port}",
                                     style = MaterialTheme.typography.bodySmall
                                 )
                                 Text(
                                     text = "Protocolo: ${if (nasConfig.useSFTP) "SFTP" else "FTP"}",
                                     style = MaterialTheme.typography.bodySmall
                                 )
                                 Text(
                                     text = "QuickConnect: ${nasConfig.quickConnect}",
                                     style = MaterialTheme.typography.bodySmall
                                 )
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                     Button(
                                         onClick = { showNASConfigDialog = true },
                                         colors = ButtonDefaults.buttonColors(
                                             containerColor = MaterialTheme.colorScheme.secondary
                                         )
                                     ) {
                                         Text("Editar")
                                     }
                                 }
                            }
                        }
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = "⚠️ NAS no configurado",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        if (showConfirmation) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    text = "Información guardada correctamente",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        
        // Espacio adicional al final para asegurar que todo sea visible con scrolling
        Spacer(modifier = Modifier.height(50.dp))
    }
    
    // Diálogos para añadir/editar buckets
    if (showAddBucketDialog) {
        BucketDialog(
            bucket = null,
            onDismiss = { showAddBucketDialog = false },
            onSave = { newBucket ->
                viewModel.saveBucket(newBucket)
                viewModel.updateSelectedBucket(newBucket.id)
                showAddBucketDialog = false
            }
        )
    }
    
    if (showEditBucketDialog && bucketToEdit != null) {
        BucketDialog(
            bucket = bucketToEdit,
            onDismiss = { 
                showEditBucketDialog = false
                bucketToEdit = null
            },
            onSave = { updatedBucket ->
                viewModel.saveBucket(updatedBucket)
                showEditBucketDialog = false
                bucketToEdit = null
            }
        )
    }
    
    // Diálogo de configuración NAS
    if (showNASConfigDialog) {
        NASConfigDialog(
            nasConfig = nasConfig,
            onDismiss = { showNASConfigDialog = false },
            onSave = { newConfig ->
                nasConfig = newConfig
                viewModel.updateNASConfig(newConfig)
                showNASConfigDialog = false
            },
            viewModel = viewModel
        )
    }
}

@Composable
fun BucketDialog(
    bucket: B2Bucket?,
    onDismiss: () -> Unit,
    onSave: (B2Bucket) -> Unit
) {
    val isNew = bucket == null
    val title = if (isNew) "Añadir Nuevo Bucket" else "Editar Bucket"
    
    var name by remember { mutableStateOf(bucket?.name ?: "") }
    var id by remember { mutableStateOf(bucket?.id ?: "") }
    var keyId by remember { mutableStateOf(bucket?.applicationKeyId ?: "") }
    var key by remember { mutableStateOf(bucket?.applicationKey ?: "") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del Bucket") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = id,
                    onValueChange = { id = it },
                    label = { Text("ID del Bucket") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = keyId,
                    onValueChange = { keyId = it },
                    label = { Text("Application Key ID") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Application Key") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && id.isNotBlank() && 
                        keyId.isNotBlank() && key.isNotBlank()) {
                        val newBucket = B2Bucket(
                            id = id,
                            name = name,
                            applicationKeyId = keyId,
                            applicationKey = key,
                            isDefault = false
                        )
                        onSave(newBucket)
                    }
                }
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NASConfigDialog(
    nasConfig: NASConfig,
    onDismiss: () -> Unit,
    onSave: (NASConfig) -> Unit,
    viewModel: MainViewModel
) {
    var serverAddress by remember { mutableStateOf(nasConfig.serverAddress) }
    var port by remember { mutableStateOf(if (nasConfig.port == 0) "22" else nasConfig.port.toString()) }
    var username by remember { mutableStateOf(if (nasConfig.username.isBlank()) "EstampadosSC" else nasConfig.username) }
    var password by remember { mutableStateOf(if (nasConfig.password.isBlank()) "NAS_Txt!2025" else nasConfig.password) }
    var quickConnect by remember { mutableStateOf(if (nasConfig.quickConnect.isBlank()) "EstampadosSC" else nasConfig.quickConnect) }
    var baseFolder by remember { mutableStateOf(nasConfig.baseFolder) }
    var useSFTP by remember { mutableStateOf(nasConfig.useSFTP) }
    var isEnabled by remember { mutableStateOf(nasConfig.isEnabled) }

    // Estados para probar conexión
    var isTestingConnection by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()
    val dialogScrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configuración del NAS Synology") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(dialogScrollState)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Habilitar NAS")
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { isEnabled = it }
                    )
                }
                if (isEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = serverAddress,
                        onValueChange = { serverAddress = it },
                        label = { Text("Dirección del Servidor") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("192.168.100.78") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text("Puerto") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("21 para FTP, 22 para SFTP") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Usuario") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Contraseña") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = quickConnect,
                        onValueChange = { quickConnect = it },
                        label = { Text("QuickConnect ID") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("EstampadosSC") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = baseFolder,
                        onValueChange = { baseFolder = it },
                        label = { Text("Carpeta base (línea)") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("PlayeraCap/linea_1") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Selector desplegable de línea
                    val lineOptions = remember { (1..10).map { it } }
                    var expanded by remember { mutableStateOf(false) }
                    // Deducir línea actual desde baseFolder
                    var selectedLine by remember(baseFolder) {
                        mutableStateOf(baseFolder.substringAfter("/linea_", "1").toIntOrNull()?.coerceIn(1,10) ?: 1)
                    }
                    Text(
                        text = "Línea",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        TextField(
                            readOnly = true,
                            value = "linea_${selectedLine}",
                            onValueChange = {},
                            label = { Text("Seleccionar línea") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            lineOptions.forEach { i ->
                                DropdownMenuItem(
                                    text = { Text("linea_$i") },
                                    onClick = {
                                        selectedLine = i
                                        val prefix = baseFolder.substringBefore("/linea_", "PlayeraCap")
                                        baseFolder = "$prefix/linea_${i}"
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { useSFTP = false },
                            colors = CardDefaults.cardColors(
                                containerColor = if (!useSFTP) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                width = if (!useSFTP) 2.dp else 1.dp,
                                color = if (!useSFTP) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(vertical = 12.dp, horizontal = 12.dp)
                                    .fillMaxWidth()
                                    .heightIn(min = 72.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "FTP",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (!useSFTP) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Puerto 21",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { useSFTP = true },
                            colors = CardDefaults.cardColors(
                                containerColor = if (useSFTP) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                width = if (useSFTP) 2.dp else 1.dp,
                                color = if (useSFTP) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(vertical = 12.dp, horizontal = 12.dp)
                                    .fillMaxWidth()
                                    .heightIn(min = 72.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "SFTP",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (useSFTP) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Puerto 22",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isEnabled) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = {
                                isTestingConnection = true
                                testResult = null
                                scope.launch {
                                    val ok = viewModel.testNASConnectionWithDiscovery(
                                        serverAddress,
                                        port.toIntOrNull() ?: (if (useSFTP) 22 else 21),
                                        username,
                                        password,
                                        useSFTP
                                    )
                                    testResult = ok
                                    isTestingConnection = false
                                }
                            },
                            enabled = !isTestingConnection && serverAddress.isNotBlank() && port.isNotBlank() && username.isNotBlank() && password.isNotBlank()
                        ) {
                            if (isTestingConnection) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Probar conexión")
                            }
                        }
                        Button(
                            onClick = {
                                if (isEnabled && serverAddress.isNotBlank() && 
                                    port.isNotBlank() && username.isNotBlank() && password.isNotBlank()) {
                                    val newConfig = NASConfig(
                                        serverAddress = serverAddress,
                                        port = port.toIntOrNull() ?: (if (useSFTP) 22 else 21),
                                        username = username,
                                        password = password,
                                        quickConnect = quickConnect,
                                        baseFolder = baseFolder,
                                        useSFTP = useSFTP,
                                        isEnabled = isEnabled
                                    )
                                    onSave(newConfig)
                                }
                            },
                            enabled = !isTestingConnection && (serverAddress.isNotBlank() && port.isNotBlank() && username.isNotBlank() && password.isNotBlank())
                        ) {
                            Text("Guardar")
                        }
                    }
                    if (testResult != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (testResult == true) "¡Conexión exitosa!" else "No se pudo conectar. Verifica los datos.",
                            color = if (testResult == true) Color(0xFF388E3C) else Color(0xFFD32F2F),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                Button(onClick = onDismiss) { Text("Cerrar") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
} 