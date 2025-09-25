package com.api.playeracap.ui.screens

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.api.playeracap.viewmodel.CapturedImage
import com.api.playeracap.viewmodel.MainViewModel
import com.api.playeracap.viewmodel.ClearImagesResult
import com.api.playeracap.viewmodel.StorageStatus
import com.api.playeracap.viewmodel.DeleteImagesResult
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun GalleryScreen(viewModel: MainViewModel, navController: NavController) {
    val capturedImages by viewModel.capturedImages.collectAsState()
    val context = LocalContext.current
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    var clearErrorMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteLocalDialog by remember { mutableStateOf(false) }
    var deleteErrorMessage by remember { mutableStateOf<String?>(null) }
    val storageStatus by viewModel.storageWarning.collectAsState()
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        Log.d("GalleryScreen", "Iniciando GalleryScreen")
        capturedImages.forEach { image ->
            try {
                context.contentResolver.openInputStream(image.uri)?.use {
                    Log.d("GalleryScreen", "URI válida: ${image.uri}")
                }
            } catch (e: Exception) {
                Log.e("GalleryScreen", "Error accediendo a URI: ${image.uri}", e)
            }
        }
        // Verificar almacenamiento al iniciar
        when (viewModel.checkStorageStatus()) {
            is StorageStatus.Critical -> showDeleteLocalDialog = true
            else -> {}
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Encabezado más compacto
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Información de estado
            Text(
                text = "Imágenes: ${capturedImages.size}",
                style = MaterialTheme.typography.titleSmall
            )
            
            // Botones en fila
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Botón de estado del dispositivo
                IconButton(
                    onClick = { navController.navigate("device_status") }
                ) {
                    Icon(
                        Icons.Default.Storage,
                        contentDescription = "Estado del dispositivo",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Botón único de liberar espacio
                IconButton(
                    onClick = { showDeleteLocalDialog = true }
                ) {
                    Icon(
                        Icons.Default.DeleteForever,
                        contentDescription = "Limpiar todas las imágenes",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Grid de imágenes
        if (capturedImages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No hay imágenes capturadas")
                    Text(
                        text = "Toma algunas fotos primero",
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(8.dp),
                modifier = Modifier.fillMaxSize()  // Asegurar que ocupe todo el espacio restante
            ) {
                items(capturedImages) { image ->
                    Column(
                        modifier = Modifier.padding(4.dp)
                    ) {
                        // Box con la imagen
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clickable { selectedImageUri = image.uri }
                        ) {
                            var isLoading by remember { mutableStateOf(true) }
                            var hasError by remember { mutableStateOf(false) }
                            
                            if (!hasError) {
                                Image(
                                    painter = rememberAsyncImagePainter(
                                        model = image.uri,
                                        onLoading = { 
                                            isLoading = true
                                            Log.d("GalleryScreen", "Cargando imagen local: ${image.uri}")
                                        },
                                        onSuccess = { 
                                            isLoading = false
                                            Log.d("GalleryScreen", "Imagen local cargada exitosamente: ${image.uri}")
                                        },
                                        onError = { 
                                            isLoading = false
                                            hasError = true
                                            Log.e("GalleryScreen", "Error cargando imagen local: ${image.uri}")
                                        }
                                    ),
                                    contentDescription = "Imagen capturada",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            
                            if (hasError) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Error al cargar imagen")
                                }
                            }
                            
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        }
                        
                        // Información de la imagen
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            // Datos de producción
                            image.productionInfo?.let { info ->
                                Text(
                                    text = "Modelo: ${info.modelo}",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Orden: ${info.ordenProducto}",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Cliente: ${info.nombreCliente}",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Color: ${info.colorTela}",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Encargado: ${info.encargadoMaquina}",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            
                            // Metadatos y estado de subida
                            Text(
                                text = image.metadata,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            Text(
                                text = if (image.b2Url != null) "Subida ✓" else "No subida",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (image.b2Url != null) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }

    selectedImageUri?.let { uri ->
        FullScreenImageDialog(
            imageUri = uri,
            onDismiss = { selectedImageUri = null }
        )
    }

    // Diálogo de confirmación
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { 
                showClearDialog = false 
                clearErrorMessage = null
            },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text("Confirmar borrado") },
            text = { 
                Column {
                    Text("¿Estás seguro de que quieres borrar todas las imágenes en caché?")
                    
                    // Mostrar advertencia si hay imágenes sin subir
                    val unuploadedCount = capturedImages.count { it.b2Url == null }
                    if (unuploadedCount > 0) {
                        Text(
                            text = "¡Advertencia! Hay $unuploadedCount ${if (unuploadedCount == 1) "imagen" else "imágenes"} sin subir que se perderán permanentemente.",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    
                    clearErrorMessage?.let {
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
                        when (val result = viewModel.clearCapturedImages()) {
                            is ClearImagesResult.Success -> {
                                showClearDialog = false
                                clearErrorMessage = null
                            }
                            is ClearImagesResult.Error -> {
                                clearErrorMessage = result.message
                            }
                        }
                    }
                ) {
                    Text("Borrar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showClearDialog = false
                        clearErrorMessage = null
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Diálogo para borrar archivos locales
    if (showDeleteLocalDialog) {
        AlertDialog(
            onDismissRequest = { 
                if (storageStatus !is StorageStatus.Critical) {
                    showDeleteLocalDialog = false 
                    deleteErrorMessage = null
                }
            },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text("Liberar espacio del dispositivo") },
            text = { 
                Column {
                    if (storageStatus is StorageStatus.Critical) {
                        Text(
                            "¡Almacenamiento crítico! Es necesario liberar espacio.",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    Text(
                        "Esta acción eliminará todos los archivos de imágenes almacenados en el dispositivo para liberar espacio."
                    )
                    Text(
                        "Las imágenes ya subidas seguirán disponibles para ver en la galería.",
                        modifier = Modifier.padding(top = 4.dp)
                    )
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
                        // Limpiar TODOS los archivos locales
                        when (val result = viewModel.deleteLocalImages()) {
                            is DeleteImagesResult.Success -> {
                                // También ejecutar una limpieza física completa
                                scope.launch {
                                    viewModel.deletePhysicalFiles()
                                    showDeleteLocalDialog = false
                                    deleteErrorMessage = null
                                }
                            }
                            is DeleteImagesResult.Error -> {
                                deleteErrorMessage = result.message
                            }
                        }
                    }
                ) {
                    Text("Liberar espacio")
                }
            },
            dismissButton = {
                if (storageStatus !is StorageStatus.Critical) {
                    TextButton(
                        onClick = { 
                            showDeleteLocalDialog = false
                            deleteErrorMessage = null
                        }
                    ) {
                        Text("Cancelar")
                    }
                }
            }
        )
    }
} 