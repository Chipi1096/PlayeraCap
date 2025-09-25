package com.api.playeracap.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import android.Manifest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.api.playeracap.utils.PermissionManager
import android.util.Log
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.BluetoothGattCharacteristic
import java.util.UUID
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile

class BluetoothManager(private val context: Context) {
    companion object {
        // UUIDs para BLE
        const val SERVICE_UUID = "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
        const val CHARACTERISTIC_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26a8"
        const val TAG = "BluetoothManager"
    }

    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    private val _foundDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val foundDevices = _foundDevices.asStateFlow()

    private var bluetoothGatt: BluetoothGatt? = null
    private var connectedDevice: BluetoothDevice? = null
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    private val _sensorDetected = MutableStateFlow(false)
    val sensorDetected = _sensorDetected.asStateFlow()

    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted = _permissionGranted.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    init {
        checkPermissions()
    }

    // Función para obtener el dispositivo conectado
    fun getConnectedDevice(): BluetoothDevice? {
        return connectedDevice
    }

    // Función para obtener información del dispositivo conectado
    fun getConnectedDeviceInfo(): BluetoothDeviceInfo? {
        return connectedDevice?.let { device ->
            try {
                BluetoothDeviceInfo(
                    name = device.name ?: "Dispositivo Desconocido",
                    address = device.address,
                    rssi = 0 // RSSI no disponible para dispositivos conectados
                )
            } catch (e: SecurityException) {
                null
            }
        }
    }

    // Clase de datos para información del dispositivo
    data class BluetoothDeviceInfo(
        val name: String,
        val address: String,
        val rssi: Int
    )

    private fun checkPermissions() {
        _permissionGranted.value = PermissionManager.hasBluetoothPermissions(context)
    }

    fun connect(address: String) {
        try {
            if (!_permissionGranted.value) {
                throw SecurityException("Permisos Bluetooth no concedidos")
            }

            val device = bluetoothAdapter?.getRemoteDevice(address)
            device?.let {
                bluetoothGatt = it.connectGatt(context, false, gattCallback)
            }
        } catch (e: SecurityException) {
            // Manejar el error de permisos
            _isConnected.value = false
            throw e
        }
    }

    fun disconnect() {
        try {
            if (!_permissionGranted.value) {
                throw SecurityException("Permisos Bluetooth no concedidos")
            }
            bluetoothGatt?.disconnect()
            bluetoothGatt = null
            connectedDevice = null
        } catch (e: SecurityException) {
            // Manejar el error de permisos
            throw e
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            try {
                _isConnected.value = newState == BluetoothGatt.STATE_CONNECTED
            } catch (e: SecurityException) {
                _isConnected.value = false
            }
        }

        // Implementar otros callbacks necesarios
    }

    fun startScan(onDeviceFound: (BluetoothDevice) -> Unit) {
        if (!hasPermissions()) {
            throw SecurityException("Permisos Bluetooth no concedidos")
        }

        try {
            // Limpiar lista de dispositivos encontrados
            _foundDevices.value = emptyList()
            val currentDevices = mutableListOf<BluetoothDevice>()
            Log.d(TAG, "Iniciando escaneo Bluetooth...")
            _isScanning.value = true
            
            // Primero, agregar dispositivos ya vinculados
            try {
                if (PermissionManager.hasBluetoothPermissions(context)) {
                    bluetoothAdapter?.bondedDevices?.let { bondedDevices ->
                        Log.d(TAG, "Dispositivos vinculados encontrados: ${bondedDevices.size}")
                        bondedDevices.forEach { device ->
                            val deviceName = device.name ?: "Desconocido"
                            Log.d(TAG, "Dispositivo vinculado: $deviceName, Address: ${device.address}")
                            // Agregar todos los dispositivos vinculados, no solo los que contienen "ESP32"
                            currentDevices.add(device)
                            onDeviceFound(device)
                        }
                        // Actualizar la lista con los dispositivos vinculados
                        _foundDevices.value = currentDevices
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Error de permisos al acceder a dispositivos vinculados", e)
            }
            
            // Luego iniciar el escaneo BLE para encontrar nuevos dispositivos
            bluetoothAdapter?.bluetoothLeScanner?.startScan(object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val device = result.device
                    val deviceName = try {
                        device.name ?: "Desconocido"
                    } catch (e: SecurityException) {
                        "Desconocido"
                    }
                    val rssi = result.rssi
                    
                    Log.d(TAG, "Dispositivo encontrado: $deviceName, Address: ${device.address}, RSSI: $rssi")
                    
                    // Agregar todos los dispositivos encontrados, no solo los que contienen "ESP32"
                    val currentList = _foundDevices.value.toMutableList()
                    if (!currentList.any { it.address == device.address }) {
                        currentList.add(device)
                        _foundDevices.value = currentList
                        onDeviceFound(device)
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.e(TAG, "Error en escaneo: $errorCode")
                    _isScanning.value = false
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar escaneo", e)
            _isScanning.value = false
            throw e
        }
    }

    fun stopScan() {
        if (!hasPermissions()) {
            throw SecurityException("Permisos Bluetooth no concedidos")
        }

        if (bluetoothAdapter?.isEnabled == true) {
            try {
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(object : ScanCallback() {})
                _isScanning.value = false
                Log.d(TAG, "Deteniendo escaneo Bluetooth")
            } catch (e: Exception) {
                Log.e(TAG, "Error al detener escaneo", e)
                throw e
            }
        }
    }

    fun hasPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun connectToDevice(address: String) {
        if (!hasPermissions()) {
            throw SecurityException("Permisos Bluetooth no concedidos")
        }

        try {
            val device = bluetoothAdapter?.getRemoteDevice(address)
            device?.let {
                Log.d(TAG, "Intentando conectar a: ${it.address}")
                
                // Almacenar el dispositivo que se está conectando
                connectedDevice = it
                
                // Desconectar GATT existente si hay uno
                bluetoothGatt?.disconnect()
                bluetoothGatt?.close()
                bluetoothGatt = null
                
                bluetoothGatt = it.connectGatt(context, false, object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                        when (newState) {
                            BluetoothProfile.STATE_CONNECTED -> {
                                Log.d(TAG, "Conectado exitosamente a ${gatt?.device?.address}")
                                _isConnected.value = true
                                // Importante: descubrir servicios después de la conexión
                                gatt?.discoverServices()
                            }
                            BluetoothProfile.STATE_DISCONNECTED -> {
                                Log.d(TAG, "Desconectado de ${gatt?.device?.address}")
                                _isConnected.value = false
                                bluetoothGatt?.close()
                                bluetoothGatt = null
                                connectedDevice = null
                            }
                        }
                    }

                    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            Log.d(TAG, "Servicios descubiertos")
                            // Intentar con los UUIDs específicos
                            var service = gatt?.getService(UUID.fromString(SERVICE_UUID))
                            
                            if (service == null) {
                                Log.d(TAG, "Servicio específico no encontrado, buscando servicios disponibles...")
                                // Listar todos los servicios disponibles
                                gatt?.services?.forEach { availableService ->
                                    Log.d(TAG, "Servicio disponible: ${availableService.uuid}")
                                    availableService.characteristics.forEach { characteristic ->
                                        Log.d(TAG, "  Característica: ${characteristic.uuid}")
                                    }
                                }
                                
                                // Si hay servicios disponibles, intentar con el primero
                                if (!gatt?.services.isNullOrEmpty()) {
                                    service = gatt?.services?.firstOrNull()
                                    Log.d(TAG, "Usando primer servicio disponible: ${service?.uuid}")
                                }
                            }
                            
                            if (service == null) {
                                Log.e(TAG, "No se encontraron servicios")
                                return
                            }
                            
                            // Intentar con el UUID específico de la característica
                            var characteristic = service.getCharacteristic(UUID.fromString(CHARACTERISTIC_UUID))
                            
                            if (characteristic == null) {
                                Log.d(TAG, "Característica específica no encontrada, usando la primera disponible...")
                                // Si no se encuentra la característica específica, usar la primera disponible
                                characteristic = service.characteristics.firstOrNull()
                                Log.d(TAG, "Usando primera característica disponible: ${characteristic?.uuid}")
                            }
                            
                            if (characteristic != null) {
                                // Habilitar notificaciones
                                val success = gatt?.setCharacteristicNotification(characteristic, true)
                                Log.d(TAG, "Notificaciones habilitadas: $success")
                                
                                // Configurar descriptor
                                characteristic.getDescriptor(
                                    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                                )?.let { descriptor ->
                                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                    val writeSuccess = gatt?.writeDescriptor(descriptor)
                                    Log.d(TAG, "Descriptor escrito: $writeSuccess")
                                }
                            } else {
                                Log.e(TAG, "No se encontraron características")
                            }
                        } else {
                            Log.e(TAG, "Error al descubrir servicios: $status")
                        }
                    }

                    override fun onCharacteristicChanged(
                        gatt: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        value: ByteArray
                    ) {
                        try {
                            val data = String(value).trim()
                            Log.d(TAG, "Datos RECIBIDOS RAW: ${value.joinToString(",") { it.toString() }}")
                            Log.d(TAG, "Datos RECIBIDOS TEXTO: '$data'")
                            
                            // Manejo basado en el contenido del mensaje
                            when {
                                data.contains("PULSE_START", ignoreCase = true) -> {
                                    Log.d(TAG, "✅ DETECTADO inicio de pulso")
                                    _sensorDetected.value = true
                                }
                                data.contains("PULSE_END", ignoreCase = true) -> {
                                    Log.d(TAG, "❌ DETECTADO fin de pulso")
                                    _sensorDetected.value = false
                                }
                                data.startsWith("SENSOR:", ignoreCase = true) -> {
                                    val sensorValue = data.substringAfter("SENSOR:", "0").trim()
                                    Log.d(TAG, "📊 Valor del sensor: '$sensorValue'")
                                    val isDetected = sensorValue == "1"
                                    Log.d(TAG, if (isDetected) "✅ SENSOR DETECTADO" else "❌ SENSOR NO DETECTADO")
                                    _sensorDetected.value = isDetected
                                }
                                // Si no coincide con los formatos anteriores, intentar analizar como número directo
                                else -> {
                                    try {
                                        // Si es solo un número, tratar como valor directo
                                        if (data.all { it.isDigit() }) {
                                            val numericValue = data.toIntOrNull() ?: 0
                                            Log.d(TAG, "🔢 Valor numérico: $numericValue")
                                            _sensorDetected.value = numericValue > 0
                                        } 
                                        // Si es solo un byte, interpretar ese byte
                                        else if (value.isNotEmpty()) {
                                            val byteValue = value[0].toInt()
                                            Log.d(TAG, "🔢 Primer byte: $byteValue")
                                            _sensorDetected.value = byteValue != 0
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "⚠️ Error al interpretar datos numéricos: ${e.message}")
                                    }
                                }
                            }
                            
                            // Notificar el estado actual del sensor para depuración
                            Log.d(TAG, "Estado actual del sensor: ${if (_sensorDetected.value) "DETECTADO" else "NO DETECTADO"}")
                        } catch (e: Exception) {
                            Log.e(TAG, "⚠️ Error al procesar datos del sensor", e)
                        }
                    }

                    override fun onDescriptorWrite(
                        gatt: BluetoothGatt?,
                        descriptor: BluetoothGattDescriptor,
                        status: Int
                    ) {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            Log.d(TAG, "Descriptor escrito exitosamente")
                        } else {
                            Log.e(TAG, "Error al escribir descriptor: $status")
                        }
                    }
                })
            } ?: throw Exception("Dispositivo no encontrado")
        } catch (e: Exception) {
            Log.e(TAG, "Error al conectar", e)
            _isConnected.value = false
            throw e
        }
    }

    fun isDeviceConnected(): Boolean {
        return _isConnected.value
    }
} 