package com.api.playeracap.viewmodel

import SafeBluetoothDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.api.playeracap.data.ProductionInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlinx.coroutines.delay
import android.net.Uri
import com.api.playeracap.data.B2Storage
import com.api.playeracap.data.SynologyStorage
import com.api.playeracap.data.NASConfig
import com.api.playeracap.data.ServerType
import android.util.Log
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.api.playeracap.data.B2File
import com.api.playeracap.data.ProductionInfoCache
import kotlinx.coroutines.Job
import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import com.api.playeracap.sensor.SimulatedSensor
import com.api.playeracap.sensor.BluetoothSensor
import java.util.*
import com.api.playeracap.utils.PermissionManager
import com.api.playeracap.sensor.SensorType
import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.isActive
import com.api.playeracap.bluetooth.BluetoothManager
import com.api.playeracap.data.B2Bucket
import com.api.playeracap.data.NASDiscovery
import com.api.playeracap.data.NASDevice
import com.api.playeracap.data.UserPreferences
import com.google.gson.Gson

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "MainViewModel" // Constante TAG para los logs

    private val _productionInfo = MutableStateFlow(ProductionInfo())
    val productionInfo: StateFlow<ProductionInfo> = _productionInfo.asStateFlow()

    private val _sensorActive = MutableStateFlow(false)
    val sensorActive: StateFlow<Boolean> = _sensorActive.asStateFlow()

    private val _isProductionInfoSet = MutableStateFlow(false)
    val isProductionInfoSet: StateFlow<Boolean> = _isProductionInfoSet.asStateFlow()

    private val _uploadStatus = MutableStateFlow<UploadStatus>(UploadStatus.Idle)
    val uploadStatus: StateFlow<UploadStatus> = _uploadStatus.asStateFlow()

    private val _capturedImages = MutableStateFlow<List<CapturedImage>>(emptyList())
    val capturedImages: StateFlow<List<CapturedImage>> = _capturedImages.asStateFlow()

    // Valores por defecto para B2Storage
    private val defaultApplicationKeyId = "0058e6fe4cdb74d0000000001"
    private val defaultApplicationKey = "K005iX9QuaAwbNoIN+XR+fOQULaMAco"
    private val defaultBucketId = "f8dec60ffe442c0d9b57041d"
    private val defaultBucketName = "Bucket Predeterminado"

    // Lista de buckets disponibles
    private val _buckets = MutableStateFlow<List<B2Bucket>>(emptyList())
    val buckets: StateFlow<List<B2Bucket>> = _buckets.asStateFlow()

    // B2Storage inicialmente con valores por defecto
    private var b2Storage = createB2Storage()
    
    // SynologyStorage para NAS
    private var synologyStorage: SynologyStorage? = null

    private val productionInfoCache = ProductionInfoCache(application)
    private val userPreferences = UserPreferences(application)

    private var isSimulationRunning = false
    private var sensorJob: Job? = null

    private val sharedPreferences = application.getSharedPreferences(
        "captured_images",
        Context.MODE_PRIVATE
    )

    private val _storageWarning = MutableStateFlow<StorageStatus>(StorageStatus.OK)
    val storageWarning: StateFlow<StorageStatus> = _storageWarning.asStateFlow()

    private val _simulatedInterval = MutableStateFlow(3000L)
    val simulatedInterval = _simulatedInterval.asStateFlow()

    private val simulatedSensor = SimulatedSensor().apply {
        setInterval(_simulatedInterval.value)
    }

    private val bluetoothSensor = BluetoothSensor(application)
    
    // Detección automática de NAS
    private val nasDiscovery = NASDiscovery()
    private val _discoveredNASDevices = MutableStateFlow<List<NASDevice>>(emptyList())
    val discoveredNASDevices: StateFlow<List<NASDevice>> = _discoveredNASDevices.asStateFlow()
    
    private val _isDiscoveringNAS = MutableStateFlow(false)
    val isDiscoveringNAS: StateFlow<Boolean> = _isDiscoveringNAS.asStateFlow()
    
    private val _sensorType = MutableStateFlow(SensorType.SIMULATED)
    val sensorType = _sensorType.asStateFlow()
    
    private val _currentDistance = MutableStateFlow<Float>(0f)
    val currentDistance = _currentDistance.asStateFlow()

    private val _sensorDetected = MutableStateFlow(false)
    val sensorDetected: StateFlow<Boolean> = _sensorDetected.asStateFlow()

    private val _lastUpdateTime = MutableStateFlow(Date())
    val lastUpdateTime = _lastUpdateTime.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    private val _foundDevices = MutableStateFlow<List<SafeBluetoothDevice>>(emptyList())
    val foundDevices = _foundDevices.asStateFlow()

    private var lastCaptureTime = 0L
    private val minimumCaptureInterval = 500L // Tiempo mínimo entre capturas

    private val minimumPulseInterval = 1000L // Tiempo mínimo entre pulsos en milisegundos
    private var pulseDetected = false

    private var lastPulseEndTime = 0L
    private var shouldTakePhoto = false

    // Nuevo estado para indicar cuándo tomar la foto
    private val _shouldCapture = MutableStateFlow(false)
    val shouldCapture: StateFlow<Boolean> = _shouldCapture.asStateFlow()

    private val _deviceInfo = MutableStateFlow<DeviceInfo?>(null)
    val deviceInfo = _deviceInfo.asStateFlow()

    private val bluetoothManager = BluetoothManager(application)
    
    // Estado de conexión al servidor
    private val _serverConnectionStatus = MutableStateFlow<ServerConnectionStatus>(ServerConnectionStatus.UNKNOWN)
    val serverConnectionStatus = _serverConnectionStatus.asStateFlow()

    // Variables para auto-reconexión
    private var autoReconnectJob: Job? = null
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 3
    private val reconnectDelay = 2000L // 2 segundos

    // Estados cíclicos del sistema automático
    enum class AutoState {
        ESPERA,    // Estado 1 - Esperando
        CAPTURA,   // Estado 2 - Capturando foto
        SUBIDA     // Estado 3 - Subiendo fotos
    }
    
    private val _currentAutoState = MutableStateFlow(AutoState.ESPERA)
    val currentAutoState: StateFlow<AutoState> = _currentAutoState.asStateFlow()

    private val _pulseIgnored = MutableStateFlow(false)
    val pulseIgnored: StateFlow<Boolean> = _pulseIgnored.asStateFlow()

    // Variable para evitar múltiples procesamiento de pulsos simultáneos
    private val _pulseJobRunning = MutableStateFlow(false)
    val pulseJobRunning: StateFlow<Boolean> = _pulseJobRunning.asStateFlow()

    // SharedPreferences para almacenar buckets
    private val bucketsPrefs = application.getSharedPreferences(
        "b2_buckets",
        Context.MODE_PRIVATE
    )

    private val gson = Gson()

    data class DeviceInfo(
        val name: String,
        val address: String,
        val rssi: Int
    )

    init {
        try {
            // Cargar buckets primero
            loadBuckets()
            
            // Cargar información guardada
            productionInfoCache.getProductionInfo()?.let { savedInfo ->
                _productionInfo.value = savedInfo
                _isProductionInfoSet.value = true
                
                // Asegurarse de que el bucket seleccionado existe
                val selectedBucketId = if (savedInfo.selectedBucketId.isNotBlank())
                    savedInfo.selectedBucketId else defaultBucketId
                    
                // Verificar si el bucket seleccionado existe
                val exists = _buckets.value.any { it.id == selectedBucketId }
                
                if (!exists) {
                    // Si no existe, usar el predeterminado
                    updateSelectedBucket(defaultBucketId)
                } else {
                    // Si existe, actualizar con los datos guardados
                    updateSelectedBucket(selectedBucketId)
                }
                
                // Recrear B2Storage con la configuración cargada
                b2Storage = createB2Storage()
                Log.d(TAG, "Configuración cargada desde caché. Bucket: ${savedInfo.bucketId}")
            }

            // Cargar imágenes guardadas
            loadSavedImages()

            // Cargar el tipo de sensor guardado
            loadSavedSensorType()
            
            // Iniciar reconexión automática si está habilitada
            startAutoReconnectOnAppStart()
            
            // Iniciar verificación periódica de conexión al servidor
            startPeriodicConnectionCheck()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error durante la inicialización: ${e.message}", e)
            // En caso de error, asegurar que al menos el bucket predeterminado esté disponible
            if (_buckets.value.isEmpty()) {
                val defaultBucket = B2Bucket(
                    id = defaultBucketId,
                    name = defaultBucketName,
                    applicationKeyId = defaultApplicationKeyId,
                    applicationKey = defaultApplicationKey,
                    isDefault = true
                )
                _buckets.value = listOf(defaultBucket)
            }
        }
    }

    companion object {
        private const val PULSE_CYCLE = 2  // Reducido a 2 pulsos: uno para capturar, otro para subir
    }

    // Contador para el ciclo de pulsos
    private var pulseCounter = 0
    private var shouldCaptureInThisPulse = true
    private var isUploading = false
    
    // Variables para el modo automático
    private val _isAutoModeActive = MutableStateFlow(false)
    val isAutoModeActive = _isAutoModeActive.asStateFlow()
    
    // Contador de pulsos para el modo automático
    private var lastSensorState = false
    
    // Variables para gestión de memoria y reinicio periódico
    private var lastPulseTime = 0L
    private var pulseResetJob: Job? = null
    private val PULSE_RESET_INTERVAL = 300000L // 5 minutos en milisegundos

    private fun loadSavedSensorType() {
        try {
            val savedSensorType = userPreferences.getSensorType()
            val sensorType = when (savedSensorType) {
                "BLUETOOTH" -> SensorType.BLUETOOTH
                else -> SensorType.SIMULATED
            }
            
            _sensorType.value = sensorType
            Log.d(TAG, "📱 Tipo de sensor cargado: $sensorType")
            
            // Iniciar el sensor correspondiente
            when (sensorType) {
                SensorType.SIMULATED -> startSimulatedSensor()
                SensorType.BLUETOOTH -> {
                    // Para Bluetooth, solo inicializar si hay un dispositivo guardado
                    val lastDeviceAddress = userPreferences.getLastConnectedDeviceAddress()
                    if (lastDeviceAddress != null) {
                        Log.d(TAG, "📱 Dispositivo Bluetooth guardado encontrado, iniciando sensor Bluetooth")
                        // No conectar automáticamente, solo preparar el sensor
                    } else {
                        Log.d(TAG, "📱 No hay dispositivo Bluetooth guardado, iniciando sensor simulado")
                        _sensorType.value = SensorType.SIMULATED
                        startSimulatedSensor()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al cargar tipo de sensor guardado: ${e.message}")
            // En caso de error, usar sensor simulado por defecto
            _sensorType.value = SensorType.SIMULATED
            startSimulatedSensor()
        }
    }

    private fun startSimulatedSensor() {
        Log.d("MainViewModel", "🔄 Iniciando sensor simulado")
        sensorJob?.cancel()
        sensorJob = viewModelScope.launch {
            while (isActive) {
                _sensorDetected.value = simulatedSensor.getCurrentDistance() > 0
                _lastUpdateTime.value = Date()
                delay(100) // Frecuencia de actualización del UI
            }
        }
    }

    fun uploadAndClearImages() {
        if (isUploading) {
            Log.d("MainViewModel", "⚠️ Ya hay una subida en proceso, ignorando solicitud")
            return
        }
        
        isUploading = true
        Log.d("MainViewModel", "🚀 INICIO DE SUBIDA DE IMÁGENES")
        
        viewModelScope.launch {
            try {
                val imagesToUpload = _capturedImages.value
                if (imagesToUpload.isEmpty()) {
                    Log.d("MainViewModel", "No hay imágenes para subir")
                    isUploading = false
                    return@launch
                }

                Log.d("MainViewModel", "📤 Iniciando subida de ${imagesToUpload.size} imágenes")
                _uploadStatus.value = UploadStatus.Uploading(0)
                
                var exitososCont = 0
                val serverType = _productionInfo.value.serverType
                
                Log.d("MainViewModel", "🖥️ Usando servidor: $serverType")
                
                // Verificar conexión al servidor antes de intentar subir
                val hasConnection = when (serverType) {
                    ServerType.SYNOLOGY_NAS -> {
                        Log.d("MainViewModel", "🔍 Verificando conexión al NAS...")
                        testNASConnection()
                    }
                    ServerType.BLACK_BAZE -> {
                        Log.d("MainViewModel", "🔍 Verificando conexión a Black Baze...")
                        testB2Connection()
                    }
                }
                
                if (!hasConnection) {
                    Log.w("MainViewModel", "⚠️ No hay conexión al servidor. Borrando ${imagesToUpload.size} imágenes automáticamente.")
                    // Borrar todas las imágenes automáticamente si no hay conexión
                    clearAllImagesAutomatically()
                    _uploadStatus.value = UploadStatus.Error("Sin conexión al servidor - Imágenes borradas automáticamente")
                    isUploading = false
                    return@launch
                }
                
                Log.d("MainViewModel", "✅ Conexión al servidor verificada. Procediendo con la subida...")
                
                imagesToUpload.forEachIndexed { index, image ->
                    try {
                        val progress = ((index + 1) * 100) / imagesToUpload.size
                        _uploadStatus.value = UploadStatus.Uploading(progress)
                        
                        Log.d("MainViewModel", "📷 Procesando imagen ${index + 1}/${imagesToUpload.size}")
                        getApplication<Application>().contentResolver.openInputStream(image.uri)?.use { inputStream ->
                            val tempFile = File(getApplication<Application>().cacheDir, "temp_${System.currentTimeMillis()}.jpg")
                            tempFile.outputStream().use { outputStream ->
inputStream.copyTo(outputStream)
                            }

                            val folderPath = generateFolderPath(image.productionInfo)
                            val fileName = "${System.currentTimeMillis()}.jpg"
                            val fullPath = "$folderPath/$fileName"
                            
                            when (serverType) {
                                ServerType.BLACK_BAZE -> {
                                    Log.d("MainViewModel", "⬆️ Subiendo imagen ${index + 1} a Black Baze: $fullPath")
                                    b2Storage.uploadFile(tempFile, fullPath)
                                }
                                ServerType.SYNOLOGY_NAS -> {
                                    Log.d("MainViewModel", "⬆️ Subiendo imagen ${index + 1} a Synology NAS: $fullPath")
                                    val nasStorage = getSynologyStorage()
                                    nasStorage?.uploadFile(tempFile, fullPath)
                                        ?: throw Exception("No se pudo inicializar la conexión al NAS")
                                }
                            }
                            
                            tempFile.delete()
                            deleteImage(image.uri)
                            Log.d("MainViewModel", "✅ Imagen ${index + 1}/${imagesToUpload.size} subida exitosamente")
                            exitososCont++
                        } ?: run {
                            Log.e("MainViewModel", "❌ No se pudo abrir la imagen ${index + 1}")
                        }
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "❌ Error al subir imagen ${index + 1}: ${e.message}")
                        // Continuar con las demás imágenes en lugar de fallar todo el proceso
                    }
                }
                
                // Limpiar todas las imágenes
                _capturedImages.value = emptyList()
                saveImages(emptyList())
                
                _uploadStatus.value = UploadStatus.Success
                Log.d("MainViewModel", "✅ Proceso de subida completado - $exitososCont imágenes subidas exitosamente")
                
            } catch (e: Exception) {
                Log.e("MainViewModel", "❌ Error general en el proceso de subida: ${e.message}")
                _uploadStatus.value = UploadStatus.Error(e.message ?: "Error desconocido")
            } finally {
                isUploading = false
                Log.d("MainViewModel", "🔄 Proceso de subida finalizado")
            }
        }
    }

    private fun deleteImage(uri: Uri) {
        try {
            getApplication<Application>().contentResolver.delete(uri, null, null)
            Log.d("MainViewModel", "Imagen eliminada: $uri")
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error al eliminar imagen", e)
        }
    }

    fun updateSensorStatus(active: Boolean) {
        _sensorActive.value = active
        if (active) {
            when (_sensorType.value) {
                SensorType.SIMULATED -> {
                    simulatedSensor.startSensing()
                    startSimulatedSensor()
                }
                SensorType.BLUETOOTH -> {
                    bluetoothSensor.startSensing()
                    startBluetoothSensor()
                }
            }
            
            // Depurar el estado del sensor
            debugSensorState()
        } else {
            bluetoothSensor.stopSensing()
            simulatedSensor.stopSensing()
            sensorJob?.cancel()
            sensorJob = null
            _sensorDetected.value = false
            _isConnected.value = false
            _isAutoModeActive.value = false
            _currentAutoState.value = AutoState.ESPERA
            lastSensorState = false
        }
    }

    fun updateProductionInfo(info: ProductionInfo) {
        _productionInfo.value = info
        _isProductionInfoSet.value = true
        productionInfoCache.saveProductionInfo(info)
        
        // Recrear B2Storage con la nueva configuración
        b2Storage = createB2Storage()
        
        // Recrear SynologyStorage si es necesario
        if (info.serverType == ServerType.SYNOLOGY_NAS && info.nasConfig.isEnabled) {
            synologyStorage = null
            getSynologyStorage()
        }
        
        Log.d(TAG, "Información de producción actualizada y servidores reconfigurados")
    }

    fun resetProductionInfo() {
        _productionInfo.value = ProductionInfo()
        _isProductionInfoSet.value = false
        productionInfoCache.clear()  // Limpiar caché
    }

    private fun loadSavedImages() {
        val savedImages = sharedPreferences.all.mapNotNull { (key, value) ->
            try {
                when {
                    key.startsWith("uri_") -> {
                        val index = key.removePrefix("uri_")
                        val uri = Uri.parse(value as String)
                        val metadata = sharedPreferences.getString("metadata_$index", "") ?: ""
                        val b2Url = sharedPreferences.getString("b2url_$index", null)
                        CapturedImage(
                            uri = uri, 
                            metadata = metadata, 
                            b2Url = b2Url
                        )
                    }
                    else -> null
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error cargando imagen guardada", e)
                null
            }
        }
        
        _capturedImages.value = savedImages
        Log.d("MainViewModel", "Imágenes cargadas: ${savedImages.size}")
    }

    private fun saveImages(images: List<CapturedImage>) {
        sharedPreferences.edit().apply {
            clear() // Limpiar datos anteriores
            images.forEachIndexed { index, image ->
                putString("uri_$index", image.uri.toString())
                putString("metadata_$index", image.metadata)
                image.b2Url?.let { putString("b2url_$index", it) }
            }
            apply()
        }
    }

    fun addCapturedImage(uri: Uri, metadata: String) {
        Log.d("MainViewModel", "Agregando imagen. Lista actual: ${_capturedImages.value.size}")
        val currentList = _capturedImages.value.toMutableList()
        
        // Agregar nueva imagen al principio de la lista
        currentList.add(0, CapturedImage(
            uri = uri,
            metadata = metadata,
            productionInfo = _productionInfo.value
        ))
        
        _capturedImages.value = currentList
        saveImages(currentList)
        Log.d("MainViewModel", "Imagen agregada. Nueva cantidad: ${currentList.size}")
    }

    fun getCapturedImagesCount(): Int {
        val count = _capturedImages.value.size
        Log.d("MainViewModel", "Cantidad actual de imágenes: $count")
        return count
    }

    fun uploadImages() {
        viewModelScope.launch {
            try {
                val imagesToUpload = _capturedImages.value.filter { it.b2Url == null }
                
                if (imagesToUpload.isEmpty()) {
                    _uploadStatus.value = UploadStatus.Error("No hay imágenes nuevas para subir")
                    return@launch
                }

                _uploadStatus.value = UploadStatus.Uploading(0)
                val allImages = _capturedImages.value.toMutableList()
                
                imagesToUpload.forEachIndexed { index, image ->
                    try {
                        val progress = ((index + 1) * 100) / imagesToUpload.size
                        _uploadStatus.value = UploadStatus.Uploading(progress)
                        
                        getApplication<Application>().contentResolver.openInputStream(image.uri)?.use { inputStream ->
                            try {
                                val tempFile = File(getApplication<Application>().cacheDir, "temp_${System.currentTimeMillis()}.jpg")
                                tempFile.outputStream().use { outputStream ->
                                    inputStream.copyTo(outputStream)
                                }

                                if (!tempFile.exists() || tempFile.length() == 0L) {
                                    throw Exception("El archivo temporal no se creó correctamente")
                                }

                                // Generar la ruta de la carpeta basada en la información de producción
                                val folderPath = generateFolderPath(image.productionInfo)
                                val fileName = "${System.currentTimeMillis()}_${index}.jpg"
                                val fullPath = "$folderPath/$fileName"
                                
                                Log.d("MainViewModel", "Subiendo archivo a: $fullPath")
                                val imageUrl = b2Storage.uploadFile(tempFile, fullPath)
                                
                                val imageIndex = allImages.indexOfFirst { it.uri == image.uri }
                                if (imageIndex != -1) {
                                    allImages[imageIndex] = image.copy(b2Url = imageUrl)
                                }
                                
                                tempFile.delete()
                            } catch (e: Exception) {
                                Log.e("MainViewModel", "Error procesando archivo temporal", e)
                                throw e
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "Error al subir imagen ${index + 1}", e)
                        throw e
                    }
                }
                
                _capturedImages.value = allImages
                saveImages(allImages)
                _uploadStatus.value = UploadStatus.Success
                
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error en el proceso de subida", e)
                _uploadStatus.value = UploadStatus.Error(e.message ?: "Error desconocido")
            }
        }
    }

    private fun generateFolderPath(productionInfo: ProductionInfo?): String {
        if (productionInfo == null) return "images/sin_info"
        
        // Limpiar y normalizar los valores para usar como nombres de carpeta
        val cliente = normalizeForPath(productionInfo.nombreCliente)
        val orden = normalizeForPath(productionInfo.ordenProducto)
        val modelo = normalizeForPath(productionInfo.modelo)
        
        // Crear estructura de carpetas: cliente/orden/modelo
        return "images/$cliente/$orden/$modelo"
    }

    private fun normalizeForPath(text: String): String {
        return text
            .trim()
            .lowercase()
            .replace(Regex("[^a-z0-9]"), "_") // Reemplazar caracteres no alfanuméricos con _
            .replace(Regex("_+"), "_") // Reemplazar múltiples _ consecutivos con uno solo
            .trim('_') // Eliminar _ al inicio y final
            .takeIf { it.isNotEmpty() } ?: "sin_especificar"
    }

    fun resetUploadStatus() {
        _uploadStatus.value = UploadStatus.Idle
    }

    fun clearCapturedImages(): ClearImagesResult {
        return try {
            _capturedImages.value = emptyList()
            sharedPreferences.edit().clear().apply()
            Log.d("MainViewModel", "Lista de imágenes limpiada manualmente")
            ClearImagesResult.Success
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error al limpiar imágenes", e)
            ClearImagesResult.Error("Error al borrar imágenes: ${e.message}")
        }
    }

    suspend fun listB2Files(): List<B2File> {
        return try {
            b2Storage.listFiles()
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error al listar archivos", e)
            throw e
        }
    }

    fun checkStorageStatus(): StorageStatus {
        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        val available = stat.availableBytes
        val total = stat.totalBytes
        val usedPercentage = ((total - available) * 100 / total)
        
        return if (usedPercentage >= 95) {
            StorageStatus.Critical
        } else {
            StorageStatus.OK
        }
    }

    fun deleteLocalImages(): DeleteImagesResult {
        return try {
            _capturedImages.value = emptyList()
            sharedPreferences.edit().clear().apply()
            Log.d("MainViewModel", "Lista de imágenes limpiada manualmente")
            DeleteImagesResult.Success
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error al limpiar imágenes", e)
            DeleteImagesResult.Error("Error al borrar imágenes: ${e.message}")
        }
    }

    sealed class DeletePhysicalFilesResult {
        data class Success(val count: Int) : DeletePhysicalFilesResult()
        data class Error(val message: String) : DeletePhysicalFilesResult()
    }

    suspend fun deletePhysicalFiles(): DeletePhysicalFilesResult {
        return try {
            var deletedCount = 0
            _capturedImages.value.forEach { image ->
                try {
                    getApplication<Application>().contentResolver.query(
                        image.uri,
                        arrayOf(MediaStore.Images.Media.DATA),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val filePath = cursor.getString(
                                cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                            )
                            val file = File(filePath)
                            if (file.exists()) {
                                if (file.delete()) {
                                    getApplication<Application>().contentResolver.delete(image.uri, null, null)
                                    deletedCount++
                                    Log.d("MainViewModel", "Archivo físico borrado: $filePath")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Error borrando archivo físico: ${image.uri}", e)
                }
            }

            _capturedImages.value = emptyList()
            sharedPreferences.edit().clear().apply()
            
            Log.d("MainViewModel", "Archivos físicos borrados: $deletedCount")
            DeletePhysicalFilesResult.Success(deletedCount)
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error al borrar archivos físicos", e)
            DeletePhysicalFilesResult.Error(e.message ?: "Error desconocido")
        }
    }

    fun setSensorType(type: SensorType) {
        Log.d("MainViewModel", "📱 Cambiando tipo de sensor a: $type")
        // Detener el sensor actual si está activo
        stopCurrentSensor()
        
        // Cambiar el tipo de sensor
        _sensorType.value = type
        
        // Guardar la selección del usuario
        val sensorTypeString = when (type) {
            SensorType.SIMULATED -> "SIMULATED"
            SensorType.BLUETOOTH -> "BLUETOOTH"
        }
        userPreferences.saveSensorType(sensorTypeString)
        
        // Si el sensor estaba activo, iniciar el nuevo tipo de sensor
        if (_sensorActive.value) {
            updateSensorStatus(true)
        }
        
        // Registrar el cambio de sensor
        Log.d("MainViewModel", "📱 Tipo de sensor cambiado y guardado: $type")
    }
    
    fun setSimulatedInterval(interval: Long) {
        _simulatedInterval.value = interval
        simulatedSensor.setInterval(interval)
    }
    
    fun connectBluetoothDevice(address: String) {
        viewModelScope.launch {
            try {
                Log.d("MainViewModel", "🔌 Conectando al dispositivo: $address")
                bluetoothManager.connectToDevice(address)
                
                // Guardar información del dispositivo conectado
                val deviceInfo = bluetoothManager.getConnectedDeviceInfo()
                deviceInfo?.let { info ->
                    userPreferences.saveLastConnectedDevice(info.address, info.name)
                    Log.d("MainViewModel", "💾 Dispositivo guardado para reconexión: ${info.name}")
                }
                
                // Iniciar el sensor automáticamente después de conectar
                setSensorType(SensorType.BLUETOOTH)
                updateSensorStatus(true)
                reconnectAttempts = 0 // Resetear contador de intentos
                Log.d("MainViewModel", "🔌 Dispositivo conectado y sensor iniciado")
            } catch (e: Exception) {
                Log.e("MainViewModel", "❌ Error al conectar: ${e.message}")
                updateSensorStatus(false)
                // Aquí podrías mostrar un mensaje de error al usuario
            }
        }
    }

    // Función para intentar reconexión automática
    fun attemptAutoReconnect() {
        if (autoReconnectJob?.isActive == true) {
            Log.d("MainViewModel", "⚠️ Auto-reconexión ya en progreso")
            return
        }

        val lastDeviceAddress = userPreferences.getLastConnectedDeviceAddress()
        if (lastDeviceAddress == null) {
            Log.d("MainViewModel", "ℹ️ No hay dispositivo guardado para reconexión")
            return
        }

        if (reconnectAttempts >= maxReconnectAttempts) {
            Log.d("MainViewModel", "❌ Máximo de intentos de reconexión alcanzado")
            return
        }

        autoReconnectJob = viewModelScope.launch {
            try {
                reconnectAttempts++
                Log.d("MainViewModel", "🔄 Intento de reconexión #$reconnectAttempts a $lastDeviceAddress")
                
                delay(reconnectDelay)
                
                // Intentar conectar
                bluetoothManager.connectToDevice(lastDeviceAddress!!)
                
                // Verificar si la conexión fue exitosa
                delay(1000) // Esperar a que se establezca la conexión
                
                if (bluetoothManager.isConnected.value) {
                    Log.d("MainViewModel", "✅ Reconexión exitosa")
                    setSensorType(SensorType.BLUETOOTH)
                    updateSensorStatus(true)
                    reconnectAttempts = 0 // Resetear contador
                } else {
                    Log.d("MainViewModel", "❌ Reconexión fallida, intento #$reconnectAttempts")
                    // Intentar de nuevo si no se alcanzó el máximo
                    if (reconnectAttempts < maxReconnectAttempts) {
                        attemptAutoReconnect()
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "❌ Error en reconexión: ${e.message}")
                if (reconnectAttempts < maxReconnectAttempts) {
                    attemptAutoReconnect()
                }
            }
        }
    }

    // Función para iniciar reconexión automática al iniciar la app
    fun startAutoReconnectOnAppStart() {
        if (!userPreferences.shouldAutoReconnect()) {
            Log.d("MainViewModel", "ℹ️ Auto-reconexión deshabilitada por el usuario")
            return
        }

        // Verificar el tipo de sensor guardado
        val savedSensorType = userPreferences.getSensorType()
        if (savedSensorType != "BLUETOOTH") {
            Log.d("MainViewModel", "ℹ️ Último sensor guardado fue $savedSensorType, no se ejecuta reconexión automática")
            return
        }

        val lastDeviceAddress = userPreferences.getLastConnectedDeviceAddress()
        if (lastDeviceAddress == null) {
            Log.d("MainViewModel", "ℹ️ No hay dispositivo guardado para reconexión")
            return
        }

        Log.d("MainViewModel", "🚀 Iniciando reconexión automática al arrancar la app (último sensor: $savedSensorType)")
        viewModelScope.launch {
            try {
                delay(userPreferences.getAutoReconnectDelay().toLong())
                attemptAutoReconnect()
            } catch (e: Exception) {
                Log.e("MainViewModel", "❌ Error al iniciar reconexión automática: ${e.message}")
            }
        }
    }

    // Función para cancelar reconexión automática
    fun cancelAutoReconnect() {
        autoReconnectJob?.cancel()
        reconnectAttempts = 0
        Log.d("MainViewModel", "🛑 Reconexión automática cancelada")
    }
    
    private fun startBluetoothSensor() {
        Log.d("MainViewModel", "📱 Iniciando BluetoothSensor y observadores")
        sensorJob?.cancel()
        sensorJob = viewModelScope.launch {
            // Observar el BluetoothSensor (implementación original)
            launch {
                Log.d("MainViewModel", "🔵 Iniciando observación de BluetoothSensor")
                bluetoothSensor.sensorDetected.collect { detected ->
                    _sensorDetected.value = detected
                    _lastUpdateTime.value = Date()
                    Log.d("MainViewModel", "🔵 Estado de BluetoothSensor actualizado: $detected")
                }
            }
            
            // Observar el BluetoothManager (nueva implementación)
            launch {
                Log.d("MainViewModel", "🟣 Iniciando observación de BluetoothManager")
                bluetoothManager.sensorDetected.collect { detected ->
                    _sensorDetected.value = detected
                    _lastUpdateTime.value = Date()
                    Log.d("MainViewModel", "🟣 Estado de BluetoothManager actualizado: $detected")
                }
            }
            
            // Observar el estado de conexión del BluetoothManager
            launch {
                Log.d("MainViewModel", "🟢 Iniciando observación de conexión BluetoothManager")
                bluetoothManager.isConnected.collect { connected ->
                    _isConnected.value = connected
                    Log.d("MainViewModel", "🟢 Estado de conexión BluetoothManager: $connected")
                    
                    // Si se perdió la conexión y el sensor está activo, intentar reconectar
                    if (!connected && _sensorActive.value && _sensorType.value == SensorType.BLUETOOTH) {
                        Log.d("MainViewModel", "🔌 Conexión perdida, iniciando reconexión automática")
                        attemptAutoReconnect()
                    }
                }
            }
        }
    }

    private fun stopSensor() {
        when (_sensorType.value) {
            SensorType.SIMULATED -> {
                sensorJob?.cancel()
                sensorJob = null
            }
            SensorType.BLUETOOTH -> {
                bluetoothSensor.stopSensing()
                sensorJob?.cancel()
                sensorJob = null
            }
        }
        _sensorDetected.value = false
        _sensorActive.value = false
    }

    private fun stopCurrentSensor() {
        sensorJob?.cancel()
        when (_sensorType.value) {
            SensorType.BLUETOOTH -> bluetoothSensor.stopSensing()
            SensorType.SIMULATED -> {} // No necesita limpieza adicional
        }
    }

    // Función para resetear el estado de captura después de tomar la foto
    fun resetCaptureFlag() {
        viewModelScope.launch {
            if (_shouldCapture.value) {
                Log.d(TAG, "🔄 Reiniciando flag de captura (shouldCapture: true -> false)")
                _shouldCapture.value = false
                delay(50) // Pequeño delay para asegurar que el cambio es detectado
                Log.d(TAG, "🔄 Flag de captura reiniciado: shouldCapture = ${_shouldCapture.value}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopCurrentSensor()
    }

    fun updateConnectedDevice(device: BluetoothDevice?, rssi: Int = 0) {
        try {
            if (!PermissionManager.hasBluetoothPermissions(getApplication())) {
                Log.e("MainViewModel", "Permisos Bluetooth no concedidos")
                return
            }

            _deviceInfo.value = device?.let {
                try {
                    DeviceInfo(
                        name = it.name ?: "Dispositivo Desconocido",
                        address = it.address,
                        rssi = rssi
                    )
                } catch (e: SecurityException) {
                    Log.e("MainViewModel", "Error de permisos al acceder al dispositivo", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error al actualizar dispositivo", e)
        }
    }

    fun clearDeviceInfo() {
        _deviceInfo.value = null
    }

    // Función auxiliar para verificar permisos
    private fun checkBluetoothPermissions(): Boolean {
        return PermissionManager.hasBluetoothPermissions(getApplication())
    }

    // Función para verificar permisos Bluetooth
    fun hasBluetoothPermissions(): Boolean {
        return bluetoothManager.hasPermissions()
    }

    // Función para iniciar el escaneo Bluetooth
    fun startBluetoothScan() {
        viewModelScope.launch {
            try {
                if (!hasBluetoothPermissions()) {
                    Log.e("MainViewModel", "No hay permisos Bluetooth")
                    return@launch
                }

                bluetoothManager.startScan { device ->
                    // Actualizar la lista de dispositivos encontrados
                    val currentDevices = _foundDevices.value.toMutableList()
                    if (!currentDevices.any { it.device.address == device.address }) {
                        currentDevices.add(SafeBluetoothDevice(device, getApplication()))
                        _foundDevices.value = currentDevices
                    }
                }
                Log.d("MainViewModel", "Escaneo Bluetooth iniciado")
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error al iniciar escaneo Bluetooth", e)
            }
        }
    }

    // Función para detener el escaneo Bluetooth
    fun stopBluetoothScan() {
        viewModelScope.launch {
            try {
                bluetoothManager.stopScan()
                Log.d("MainViewModel", "Escaneo Bluetooth detenido")
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error al detener escaneo Bluetooth", e)
            }
        }
    }

    fun debugSensorState() {
        Log.d("MainViewModel", "📊 Estado actual de sensores:")
        Log.d("MainViewModel", "📊 sensorActive: ${_sensorActive.value}")
        Log.d("MainViewModel", "📊 sensorType: ${_sensorType.value}")
        Log.d("MainViewModel", "📊 sensorDetected: ${_sensorDetected.value}")
        Log.d("MainViewModel", "📊 isConnected: ${_isConnected.value}")
        
        viewModelScope.launch {
            try {
                Log.d("MainViewModel", "📊 BluetoothManager sensorDetected: ${bluetoothManager.sensorDetected.value}")
                Log.d("MainViewModel", "📊 BluetoothManager isConnected: ${bluetoothManager.isConnected.value}")
                Log.d("MainViewModel", "📊 BluetoothSensor sensorDetected: ${bluetoothSensor.sensorDetected.value}")
                Log.d("MainViewModel", "📊 BluetoothSensor isConnected: ${bluetoothSensor.isConnected.value}")
            } catch (e: Exception) {
                Log.e("MainViewModel", "📊 Error al obtener estado de sensores", e)
            }
        }
    }

    // Función para activar el modo automático
    fun setAutoModeActive(active: Boolean) {
        _isAutoModeActive.value = active
        
        if (!active) {
            // Reiniciar estados si se desactiva
            _currentAutoState.value = AutoState.ESPERA
            lastSensorState = false
            lastPulseTime = 0L
            
            // Cancelar job de reinicio periódico
            pulseResetJob?.cancel()
            pulseResetJob = null
            
            Log.d("MainViewModel", "🔄 Reiniciando estados del modo automático")
        } else {
            // Inicializar al activar
            lastSensorState = _sensorDetected.value
            lastPulseTime = System.currentTimeMillis()
            
            // Manejar imágenes pendientes antes de iniciar
            handlePendingImagesOnStartup()
            
            _currentAutoState.value = AutoState.ESPERA // Siempre empezar en ESPERA
            
            // Iniciar job de reinicio periódico para evitar acumulación de memoria
            startPeriodicPulseReset()
            
            Log.d("MainViewModel", "🔄 Inicializando modo automático con estado del sensor: ${if (lastSensorState) "ALTO" else "BAJO"}")
        }
        
        Log.d("MainViewModel", "🚥 Modo automático: ${if (active) "ACTIVADO" else "DESACTIVADO"}")
    }
    
    // Función para manejar imágenes pendientes al iniciar el proceso
    private fun handlePendingImagesOnStartup() {
        viewModelScope.launch {
            try {
                val pendingImages = _capturedImages.value
                
                if (pendingImages.isNotEmpty()) {
                    Log.d("MainViewModel", "📤 Detectadas ${pendingImages.size} imágenes pendientes al iniciar proceso")
                    
                    // Subir automáticamente las imágenes pendientes
                    uploadAndClearImages()
                    
                    Log.d("MainViewModel", "✅ Imágenes pendientes subidas automáticamente al iniciar")
                } else {
                    Log.d("MainViewModel", "✅ No hay imágenes pendientes al iniciar proceso")
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "❌ Error al manejar imágenes pendientes: ${e.message}", e)
                
                // Si hay error en la subida, ofrecer opción de limpiar
                // Esto se manejará desde la UI
            }
        }
    }
    
    // Función para iniciar el reinicio periódico de estados
    private fun startPeriodicPulseReset() {
        pulseResetJob?.cancel()
        pulseResetJob = viewModelScope.launch {
            while (isActive && _isAutoModeActive.value) {
                delay(PULSE_RESET_INTERVAL)
                
                if (_isAutoModeActive.value) {
                    val currentTime = System.currentTimeMillis()
                    val timeSinceLastPulse = currentTime - lastPulseTime
                    
                    // Solo reiniciar si han pasado más de 4 minutos sin actividad
                    if (timeSinceLastPulse > 240000L) { // 4 minutos
                        _currentAutoState.value = AutoState.ESPERA
                        Log.d("MainViewModel", "🔄 Reinicio periódico de estados (inactividad: ${timeSinceLastPulse}ms)")
                    }
                }
            }
        }
    }
    
    /**
     * Procesa los pulsos en modo automático. 
     * Esta función alterna cíclicamente entre los estados: ESPERA → CAPTURA → SUBIDA → ESPERA...
     * 
     * @param timestamp El tiempo en que se recibió el pulso
     */
    fun processAutoModePulse(timestamp: Long) {
        // Obtenemos el estado actual del sensor
        val currentSensorState = _sensorDetected.value
        
        Log.d(TAG, "📊 Estado recibido: ${if (currentSensorState) "ALTO" else "BAJO"}, estado anterior: ${if (lastSensorState) "ALTO" else "BAJO"}")
        
        // Solo procesamos cuando hay un flanco descendente (de true/alto a false/bajo)
        if (lastSensorState && !currentSensorState) {
            // Detectamos un flanco descendente
            Log.d(TAG, "⬇️ FLANCO DESCENDENTE DETECTADO")
            
            // Si ya hay un job procesando un pulso, no iniciar otro
            if (_pulseJobRunning.value) {
                Log.d(TAG, "Ignorando flanco descendente: ya hay un pulso en procesamiento")
                lastSensorState = currentSensorState // Actualizamos el estado anterior
                return
            }
            
            viewModelScope.launch {
                try {
                    _pulseJobRunning.value = true
                    
                    // Comprobar si el modo automático está activado
                    if (!_isAutoModeActive.value) {
                        Log.d(TAG, "Flanco descendente ignorado: modo automático no activado")
                        return@launch
                    }
        
                    // Actualizar tiempo del último pulso recibido
                    val currentTime = System.currentTimeMillis()
                    val timeSinceLastPulse = currentTime - lastPulseTime
                    
                    // Verificar si el intervalo es válido (entre 500ms y 10000ms)
                    if (timeSinceLastPulse < 500) {
                        _pulseIgnored.value = true
                        Log.d(TAG, "Flanco descendente ignorado: muy rápido ($timeSinceLastPulse ms < 500ms)")
                        
                        // Mostrar indicador de pulso ignorado por 500ms
                        delay(500)
                        _pulseIgnored.value = false
                        
                        return@launch
                    } else if (timeSinceLastPulse > 10000) {
                        // Reiniciar estado si han pasado más de 10 segundos
                        _currentAutoState.value = AutoState.ESPERA
                        Log.d(TAG, "Estado reiniciado por inactividad ($timeSinceLastPulse ms > 10000ms)")
                    }
                    
                    // Actualizar el tiempo del último pulso válido
                    lastPulseTime = currentTime
                    _lastUpdateTime.value = Date()
                    
                    // Alternar al siguiente estado cíclicamente
                    val currentState = _currentAutoState.value
                    val nextState = when (currentState) {
                        AutoState.ESPERA -> AutoState.CAPTURA
                        AutoState.CAPTURA -> AutoState.SUBIDA
                        AutoState.SUBIDA -> AutoState.ESPERA
                    }
                    
                    _currentAutoState.value = nextState
                    
                    Log.d(TAG, "🔄 Estado cambiado: ${currentState.name} → ${nextState.name} (intervalo: $timeSinceLastPulse ms)")
                    
                } finally {
                    // Asegurar que el job se marca como terminado
                    _pulseJobRunning.value = false
                }
            }
        }
        
        // Actualizamos siempre el estado anterior al final para la próxima llamada
        lastSensorState = currentSensorState
    }

    // Función para forzar una captura (útil para pruebas o depuración)
    fun forceCapture() {
        viewModelScope.launch {
            Log.d(TAG, "⚡ Iniciando captura forzada manualmente")
            
            // Primero ponemos el flag en false para asegurar un cambio de estado
            _shouldCapture.value = false
            delay(50) // Pequeño delay para asegurar que el cambio es detectado
            
            // Ahora activamos la captura
            _shouldCapture.value = true
            Log.d(TAG, "⚡ Captura forzada: shouldCapture = ${_shouldCapture.value}")
        }
    }
    
    // Función para limpiar recursos y reiniciar estados (útil cuando hay problemas de memoria)
    fun cleanupAndReset() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "🧹 Iniciando limpieza de recursos y reinicio de estados")
                
                // Cancelar jobs activos
                pulseResetJob?.cancel()
                pulseResetJob = null
                
                // Reiniciar estados
                _currentAutoState.value = AutoState.ESPERA
                lastSensorState = false
                lastPulseTime = 0L
                
                // Limpiar flags de estado
                _pulseIgnored.value = false
                _shouldCapture.value = false
                
                // Limpiar cache de imágenes si hay muchas
                if (_capturedImages.value.size > 50) {
                    Log.d(TAG, "🧹 Limpiando cache de imágenes (${_capturedImages.value.size} imágenes)")
                    _capturedImages.value = emptyList()
                    sharedPreferences.edit().clear().apply()
                }
                
                // Reiniciar job de reinicio periódico si el modo automático está activo
                if (_isAutoModeActive.value) {
                    startPeriodicPulseReset()
                }
                
                Log.d(TAG, "✅ Limpieza de recursos completada")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error durante la limpieza de recursos: ${e.message}", e)
            }
        }
    }
    
    // Función para limpiar imágenes pendientes (útil cuando hay problemas de conectividad)
    fun clearPendingImages() {
        viewModelScope.launch {
            try {
                val pendingCount = _capturedImages.value.size
                
                if (pendingCount > 0) {
                    Log.d(TAG, "🗑️ Limpiando ${pendingCount} imágenes pendientes")
                    
                    _capturedImages.value = emptyList()
                    sharedPreferences.edit().clear().apply()
                    
                    Log.d(TAG, "✅ Imágenes pendientes eliminadas")
                } else {
                    Log.d(TAG, "ℹ️ No hay imágenes pendientes para limpiar")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error al limpiar imágenes pendientes: ${e.message}", e)
            }
        }
    }

    // Función para crear o actualizar la instancia de B2Storage
    private fun createB2Storage(): B2Storage {
        val info = _productionInfo.value
        val appKeyId = if (info.applicationKeyId.isNotBlank()) info.applicationKeyId else defaultApplicationKeyId
        val appKey = if (info.applicationKey.isNotBlank()) info.applicationKey else defaultApplicationKey
        val bucket = if (info.bucketId.isNotBlank()) info.bucketId else defaultBucketId
        
        Log.d(TAG, "Creando B2Storage con: bucketId=$bucket")
        
        return B2Storage(
            applicationKeyId = appKeyId,
            applicationKey = appKey,
            bucketId = bucket
        )
    }
    
    // Función para obtener o crear la instancia de SynologyStorage
    private fun getSynologyStorage(): SynologyStorage? {
        val nasConfig = _productionInfo.value.nasConfig
        
        if (!nasConfig.isEnabled || nasConfig.serverAddress.isBlank()) {
            Log.d(TAG, "Configuración NAS no habilitada o incompleta")
            return null
        }
        
        // Crear nueva instancia si no existe o si la configuración cambió
        if (synologyStorage == null) {
            Log.d(TAG, "Creando SynologyStorage con: ${nasConfig.serverAddress}:${nasConfig.port}")
            synologyStorage = SynologyStorage(
                serverAddress = nasConfig.serverAddress,
                port = nasConfig.port,
                username = nasConfig.username,
                password = nasConfig.password,
                useSFTP = nasConfig.useSFTP,
                baseFolder = nasConfig.baseFolder
            )
        }
        
        return synologyStorage
    }
    
    // Función para probar la conexión al NAS
    suspend fun testNASConnection(): Boolean {
        return try {
            val nasStorage = getSynologyStorage()
            nasStorage?.testConnection() ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error al probar conexión NAS", e)
            false
        }
    }
    
    // Función para probar la conexión a Black Baze
    suspend fun testB2Connection(): Boolean {
        return try {
            b2Storage.testConnection()
        } catch (e: Exception) {
            Log.e(TAG, "Error al probar conexión Black Baze", e)
            false
        }
    }
    
    // Función para borrar todas las imágenes automáticamente
    private fun clearAllImagesAutomatically() {
        try {
            val imagesToDelete = _capturedImages.value
            Log.d(TAG, "🗑️ Borrando automáticamente ${imagesToDelete.size} imágenes por falta de conexión")
            
            imagesToDelete.forEach { image ->
                deleteImage(image.uri)
            }
            
            // Limpiar la lista de imágenes
            _capturedImages.value = emptyList()
            saveImages(emptyList())
            
            Log.d(TAG, "✅ ${imagesToDelete.size} imágenes borradas automáticamente")
        } catch (e: Exception) {
            Log.e(TAG, "Error al borrar imágenes automáticamente", e)
        }
    }
    
    // Función para verificar la conexión al servidor
    fun checkServerConnection() {
        viewModelScope.launch {
            try {
                val serverType = _productionInfo.value.serverType
                val hasConnection = when (serverType) {
                    ServerType.SYNOLOGY_NAS -> testNASConnection()
                    ServerType.BLACK_BAZE -> testB2Connection()
                }
                
                _serverConnectionStatus.value = if (hasConnection) {
                    ServerConnectionStatus.CONNECTED
                } else {
                    ServerConnectionStatus.DISCONNECTED
                }
                
                Log.d(TAG, "🔍 Estado de conexión al servidor: ${_serverConnectionStatus.value}")
            } catch (e: Exception) {
                Log.e(TAG, "Error al verificar conexión al servidor", e)
                _serverConnectionStatus.value = ServerConnectionStatus.DISCONNECTED
            }
        }
    }
    
    // Función para verificar conexión periódicamente
    fun startPeriodicConnectionCheck() {
        viewModelScope.launch {
            while (isActive) {
                checkServerConnection()
                delay(30000) // Verificar cada 30 segundos
            }
        }
    }
    
    // Función para actualizar la configuración del servidor
    fun updateServerType(serverType: ServerType) {
        val currentInfo = _productionInfo.value
        _productionInfo.value = currentInfo.copy(serverType = serverType)
        productionInfoCache.saveProductionInfo(_productionInfo.value)
        Log.d(TAG, "Tipo de servidor actualizado: $serverType")
        
        // Verificar conexión al nuevo servidor
        checkServerConnection()
    }
    
    // Función para actualizar la configuración del NAS
    fun updateNASConfig(nasConfig: NASConfig) {
        val currentInfo = _productionInfo.value
        _productionInfo.value = currentInfo.copy(nasConfig = nasConfig)
        productionInfoCache.saveProductionInfo(_productionInfo.value)
        
        // Recrear SynologyStorage con la nueva configuración
        synologyStorage = null
        getSynologyStorage()
        
        Log.d(TAG, "Configuración NAS actualizada")
        
        // Verificar conexión al NAS con la nueva configuración
        checkServerConnection()
    }

    // Cargar y guardar buckets
    private fun loadBuckets() {
        Log.d(TAG, "Iniciando carga de buckets")
        
        // Crear una lista mutable para almacenar los buckets
        val bucketsList = mutableListOf<B2Bucket>()
        
        // Añadir el bucket predeterminado
        val defaultBucket = B2Bucket(
            id = defaultBucketId,
            name = defaultBucketName,
            applicationKeyId = defaultApplicationKeyId,
            applicationKey = defaultApplicationKey,
            isDefault = true
        )
        bucketsList.add(defaultBucket)
        
        // Obtener todos los buckets guardados en SharedPreferences
        val savedBucketsJson = ArrayList<String>()
        for (entry in bucketsPrefs.all) {
            if (entry.key.startsWith("bucket_") && entry.value is String) {
                savedBucketsJson.add(entry.value as String)
            }
        }
        
        // Procesar cada JSON
        for (json in savedBucketsJson) {
            try {
                val bucket = gson.fromJson(json, B2Bucket::class.java)
                if (bucket != null && bucket.id != defaultBucketId) {
                    bucketsList.add(bucket)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al deserializar bucket: $json", e)
            }
        }
        
        // Actualizar la lista de buckets
        _buckets.value = bucketsList
        Log.d(TAG, "Total de buckets cargados: ${bucketsList.size}")
    }
    
    fun saveBucket(bucket: B2Bucket) {
        try {
            // Comprobar si ya existe un bucket con el mismo ID
            val existingBuckets = _buckets.value.toMutableList()
            val existingIndex = existingBuckets.indexOfFirst { it.id == bucket.id }
            
            if (existingIndex >= 0) {
                // Actualizar el bucket existente
                existingBuckets[existingIndex] = bucket
            } else {
                // Añadir nuevo bucket
                existingBuckets.add(bucket)
            }
            
            // Actualizar la lista
            _buckets.value = existingBuckets
            
            // Guardar en SharedPreferences
            val bucketJson = gson.toJson(bucket)
            val editor = bucketsPrefs.edit()
            editor.putString("bucket_${bucket.id}", bucketJson)
            editor.apply()
            
            Log.d(TAG, "Bucket guardado: ${bucket.name} (${bucket.id})")
        } catch (e: Exception) {
            Log.e(TAG, "Error al guardar bucket", e)
        }
    }
    
    fun deleteBucket(bucketId: String) {
        try {
            // No permitir eliminar el bucket predeterminado
            if (bucketId == defaultBucketId) {
                Log.d(TAG, "No se puede eliminar el bucket predeterminado")
                return
            }
            
            // Eliminar de la lista
            val existingBuckets = _buckets.value.toMutableList()
            val removed = existingBuckets.removeIf { it.id == bucketId }
            
            if (removed) {
                // Actualizar la lista
                _buckets.value = existingBuckets
                
                // Eliminar de SharedPreferences
                val editor = bucketsPrefs.edit()
                editor.remove("bucket_$bucketId")
                editor.apply()
                
                // Si era el bucket seleccionado, cambiar al predeterminado
                if (_productionInfo.value.selectedBucketId == bucketId) {
                    updateSelectedBucket(defaultBucketId)
                }
                
                Log.d(TAG, "Bucket eliminado: $bucketId")
            } else {
                Log.d(TAG, "No se encontró el bucket con ID: $bucketId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al eliminar bucket", e)
        }
    }
    
    fun updateSelectedBucket(bucketId: String) {
        val selectedBucket = _buckets.value.find { it.id == bucketId }
        
        if (selectedBucket != null) {
            // Actualizar la información de producción
            val currentInfo = _productionInfo.value
            _productionInfo.value = currentInfo.copy(
                selectedBucketId = bucketId,
                bucketId = selectedBucket.id,
                bucketName = selectedBucket.name,
                applicationKeyId = selectedBucket.applicationKeyId,
                applicationKey = selectedBucket.applicationKey
            )
            
            // Guardar en caché
            productionInfoCache.saveProductionInfo(_productionInfo.value)
            
            // Actualizar B2Storage
            b2Storage = createB2Storage()
            
            Log.d(TAG, "Bucket seleccionado actualizado: ${selectedBucket.name}")
        } else {
            Log.e(TAG, "No se encontró el bucket con ID: $bucketId")
        }
    }
    
    // ===== FUNCIONES DE DETECCIÓN AUTOMÁTICA DE NAS =====
    
    fun startNASDiscovery() {
        if (_isDiscoveringNAS.value) {
            Log.d(TAG, "Descubrimiento de NAS ya en progreso")
            return
        }
        
        _isDiscoveringNAS.value = true
        _discoveredNASDevices.value = emptyList()
        
        viewModelScope.launch {
            try {
                Log.d(TAG, "Iniciando descubrimiento automático de NAS...")
                val discoveredDevices = nasDiscovery.discoverNASDevices()
                _discoveredNASDevices.value = discoveredDevices
                Log.d(TAG, "Descubrimiento completado. Dispositivos encontrados: ${discoveredDevices.size}")
            } catch (e: Exception) {
                Log.e(TAG, "Error durante el descubrimiento de NAS", e)
            } finally {
                _isDiscoveringNAS.value = false
            }
        }
    }
    
    fun getDeviceInfo(ipAddress: String) {
        viewModelScope.launch {
            try {
                val deviceInfo = nasDiscovery.getDeviceInfo(ipAddress)
                if (deviceInfo != null) {
                    Log.d(TAG, "Información del dispositivo $ipAddress: $deviceInfo")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error obteniendo información del dispositivo $ipAddress", e)
            }
        }
    }
    
    suspend fun testNASConnectionWithDiscovery(ipAddress: String, port: Int, username: String, password: String, useSFTP: Boolean): Boolean {
        return try {
            val result = nasDiscovery.testNASConnection(ipAddress, port, username, password, useSFTP)
            Log.d(TAG, "Prueba de conexión a $ipAddress:$port - Resultado: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error probando conexión a $ipAddress:$port", e)
            false
        }
    }
    
    fun clearDiscoveredDevices() {
        _discoveredNASDevices.value = emptyList()
    }

    // ===== FUNCIONES DE CALIBRACIÓN DEL SENSOR BLUETOOTH =====
    
    fun saveBluetoothSensorCalibration(captureDelay: Long) {
        viewModelScope.launch {
            try {
                userPreferences.saveBluetoothSensorCalibration(captureDelay)
                Log.d(TAG, "🎯 Calibración del sensor Bluetooth guardada: ${captureDelay}ms")
            } catch (e: Exception) {
                Log.e(TAG, "Error al guardar calibración del sensor Bluetooth", e)
            }
        }
    }
    
    fun getBluetoothSensorCalibration(): Long {
        return try {
            userPreferences.getBluetoothSensorCalibration()
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener calibración del sensor Bluetooth", e)
            0L
        }
    }
    
               fun resetBluetoothSensorCalibration() {
               viewModelScope.launch {
                   try {
                       userPreferences.resetBluetoothSensorCalibration()
                       Log.d(TAG, "🔄 Calibración del sensor Bluetooth restablecida")
                   } catch (e: Exception) {
                       Log.e(TAG, "Error al restablecer calibración del sensor Bluetooth", e)
                   }
               }
           }

           // ===== FUNCIONES DE LÓGICA DEL SENSOR BLUETOOTH =====
           
           fun saveBluetoothSensorLogic(logic: String) {
               viewModelScope.launch {
                   try {
                       userPreferences.saveBluetoothSensorLogic(logic)
                       Log.d(TAG, "🔄 Lógica del sensor Bluetooth guardada: $logic")
                   } catch (e: Exception) {
                       Log.e(TAG, "Error al guardar lógica del sensor Bluetooth", e)
                   }
               }
           }
           
           fun getBluetoothSensorLogic(): String {
               return try {
                   userPreferences.getBluetoothSensorLogic()
               } catch (e: Exception) {
                   Log.e(TAG, "Error al obtener lógica del sensor Bluetooth", e)
                   "bajada"
               }
           }
           
           fun resetBluetoothSensorLogic() {
               viewModelScope.launch {
                   try {
                       userPreferences.resetBluetoothSensorLogic()
                       Log.d(TAG, "🔄 Lógica del sensor Bluetooth restablecida")
                   } catch (e: Exception) {
                       Log.e(TAG, "Error al restablecer lógica del sensor Bluetooth", e)
                   }
               }
           }
}

enum class SensorType {
    SIMULATED,
    BLUETOOTH
} 