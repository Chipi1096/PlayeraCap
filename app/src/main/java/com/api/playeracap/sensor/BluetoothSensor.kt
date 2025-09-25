package com.api.playeracap.sensor

import android.bluetooth.*
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*
import com.api.playeracap.utils.PermissionManager

class BluetoothSensor(private val context: Context) : SensorInterface {
    private var bluetoothGatt: BluetoothGatt? = null
    private var isRunning = false
    private var currentDistance: Float = 0f

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    // Agregar el StateFlow para la detección del sensor
    private val _sensorDetected = MutableStateFlow(false)
    val sensorDetected: StateFlow<Boolean> = _sensorDetected.asStateFlow()

    companion object {
        // UUID específicos para el servicio y características del sensor
        private val SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        private val CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
        private const val TAG = "BluetoothSensor"
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "Conectado al dispositivo GATT.")
                    _isConnected.value = true
                    // Descubrir servicios después de una conexión exitosa
                    try {
                        if (PermissionManager.hasBluetoothPermissions(context)) {
                            gatt.discoverServices()
                        }
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Error de permisos al descubrir servicios", e)
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "Desconectado del dispositivo GATT.")
                    _isConnected.value = false
                    _sensorDetected.value = false
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Servicios descubiertos")
                
                // Intentar con los UUIDs específicos
                var service = gatt.getService(SERVICE_UUID)
                
                if (service == null) {
                    Log.d(TAG, "Servicio específico no encontrado, buscando servicios disponibles...")
                    // Listar todos los servicios disponibles
                    gatt.services.forEach { availableService ->
                        Log.d(TAG, "Servicio disponible: ${availableService.uuid}")
                        availableService.characteristics.forEach { characteristic ->
                            Log.d(TAG, "  Característica: ${characteristic.uuid}")
                        }
                    }
                    
                    // Si hay servicios disponibles, intentar con el primero
                    if (gatt.services.isNotEmpty()) {
                        service = gatt.services.firstOrNull()
                        Log.d(TAG, "Usando primer servicio disponible: ${service?.uuid}")
                    }
                }
                
                if (service == null) {
                    Log.e(TAG, "No se encontraron servicios")
                    return
                }
                
                // Intentar con el UUID específico de la característica
                var characteristic = service.getCharacteristic(CHARACTERISTIC_UUID)
                
                if (characteristic == null) {
                    Log.d(TAG, "Característica específica no encontrada, usando la primera disponible...")
                    // Si no se encuentra la característica específica, usar la primera disponible
                    characteristic = service.characteristics.firstOrNull()
                    Log.d(TAG, "Usando primera característica disponible: ${characteristic?.uuid}")
                }
                
                if (characteristic != null) {
                    try {
                        if (PermissionManager.hasBluetoothPermissions(context)) {
                            // Habilitar notificaciones para recibir actualizaciones del sensor
                            val success = gatt.setCharacteristicNotification(characteristic, true)
                            Log.d(TAG, "Notificaciones habilitadas: $success")
                            
                            // Configurar descriptor
                            characteristic.getDescriptor(
                                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                            )?.let { descriptor ->
                                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                val writeSuccess = gatt.writeDescriptor(descriptor)
                                Log.d(TAG, "Descriptor escrito: $writeSuccess")
                            }
                        }
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Error de permisos al configurar notificaciones", e)
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
                val data = String(value)
                Log.d(TAG, "Datos recibidos: $data")
                
                if (data.contains("PULSE_START")) {
                    currentDistance = 1f
                    _sensorDetected.value = true
                    Log.d(TAG, "Detectado inicio de pulso")
                } else if (data.contains("PULSE_END")) {
                    currentDistance = 0f
                    _sensorDetected.value = false
                    Log.d(TAG, "Detectado fin de pulso")
                } else if (data.startsWith("SENSOR:")) {
                    val sensorValue = data.substringAfter("SENSOR:").trim()
                    currentDistance = if (sensorValue == "1") 1f else 0f
                    _sensorDetected.value = sensorValue == "1"
                    Log.d(TAG, "Valor del sensor: $sensorValue")
                } else {
                    // Intentar interpretar el valor como un byte si no coincide con los formatos anteriores
                    try {
                        val detected = value[0] != 0.toByte()
                        currentDistance = if (detected) 1f else 0f
                        _sensorDetected.value = detected
                        Log.d(TAG, "Interpretado como byte: ${if (detected) "Detectado" else "No detectado"}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error al interpretar datos: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al procesar datos recibidos", e)
            }
        }
    }

    fun connect(address: String) {
        try {
            if (!PermissionManager.hasBluetoothPermissions(context)) {
                throw SecurityException("Permisos Bluetooth no concedidos")
            }

            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            val device = bluetoothAdapter?.getRemoteDevice(address)
            
            device?.let {
                Log.d(TAG, "Intentando conectar a: ${it.address}")
                bluetoothGatt = it.connectGatt(context, false, gattCallback)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Error de permisos al conectar", e)
            throw e
        }
    }

    override fun startSensing() {
        Log.d(TAG, "Iniciando BluetoothSensor")
        isRunning = true
    }

    override fun stopSensing() {
        Log.d(TAG, "Deteniendo BluetoothSensor")
        isRunning = false
        try {
            if (PermissionManager.hasBluetoothPermissions(context)) {
                bluetoothGatt?.close()
                bluetoothGatt = null
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Error de permisos al detener el sensor", e)
        }
        _sensorDetected.value = false
    }

    override fun isActive(): Boolean = isRunning

    override fun getCurrentDistance(): Float = currentDistance

    fun disconnect() {
        try {
            if (PermissionManager.hasBluetoothPermissions(context)) {
                bluetoothGatt?.disconnect()
                _sensorDetected.value = false
                _isConnected.value = false
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Error de permisos al desconectar", e)
        }
    }
} 