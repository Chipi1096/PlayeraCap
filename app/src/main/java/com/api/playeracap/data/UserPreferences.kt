package com.api.playeracap.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import android.util.Log

class UserPreferences(private val context: Context) {
    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
        private val SHUTTER_SPEED = stringPreferencesKey("shutter_speed")
        
        // Keys para reconexión automática
        private val LAST_CONNECTED_DEVICE_ADDRESS = stringPreferencesKey("last_connected_device_address")
        private val LAST_CONNECTED_DEVICE_NAME = stringPreferencesKey("last_connected_device_name")
        private val LAST_CONNECTION_TIME = longPreferencesKey("last_connection_time")
        private val AUTO_RECONNECT_ENABLED = booleanPreferencesKey("auto_reconnect_enabled")
        private val AUTO_RECONNECT_DELAY = longPreferencesKey("auto_reconnect_delay")
    }

    // SharedPreferences para funciones simples de reconexión automática
    private val sharedPreferences = context.getSharedPreferences("auto_reconnect_prefs", Context.MODE_PRIVATE)

    val shutterSpeed: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[SHUTTER_SPEED] ?: "1/125" // Valor por defecto
        }

    suspend fun saveShutterSpeed(speed: String) {
        context.dataStore.edit { preferences ->
            preferences[SHUTTER_SPEED] = speed
        }
    }

    // Configuración de Bluetooth - usando SharedPreferences para simplicidad
    fun saveLastConnectedDevice(address: String, name: String) {
        sharedPreferences.edit().apply {
            putString("last_connected_device_address", address)
            putString("last_connected_device_name", name)
            putLong("last_connection_time", System.currentTimeMillis())
            apply()
        }
        Log.d("UserPreferences", "💾 Dispositivo guardado: $name ($address)")
    }

    fun getLastConnectedDeviceAddress(): String? {
        return sharedPreferences.getString("last_connected_device_address", null)
    }

    fun getLastConnectedDeviceName(): String? {
        return sharedPreferences.getString("last_connected_device_name", null)
    }

    fun getLastConnectionTime(): Long {
        return sharedPreferences.getLong("last_connection_time", 0L)
    }

    fun clearLastConnectedDevice() {
        sharedPreferences.edit().apply {
            remove("last_connected_device_address")
            remove("last_connected_device_name")
            remove("last_connection_time")
            apply()
        }
        Log.d("UserPreferences", "🗑️ Información del último dispositivo eliminada")
    }

    fun shouldAutoReconnect(): Boolean {
        return sharedPreferences.getBoolean("auto_reconnect_enabled", true)
    }

    fun setAutoReconnectEnabled(enabled: Boolean) {
        sharedPreferences.edit().apply {
            putBoolean("auto_reconnect_enabled", enabled)
            apply()
        }
        Log.d("UserPreferences", "🔄 Auto-reconexión ${if (enabled) "habilitada" else "deshabilitada"}")
    }

    fun getAutoReconnectDelay(): Long {
        return sharedPreferences.getLong("auto_reconnect_delay", 2000L) // 2 segundos por defecto
    }

    fun setAutoReconnectDelay(delay: Long) {
        sharedPreferences.edit().apply {
            putLong("auto_reconnect_delay", delay)
            apply()
        }
        Log.d("UserPreferences", "⏱️ Delay de auto-reconexión configurado: ${delay}ms")
    }

    // Configuración del tipo de sensor
    fun saveSensorType(sensorType: String) {
        sharedPreferences.edit().apply {
            putString("sensor_type", sensorType)
            apply()
        }
        Log.d("UserPreferences", "📱 Tipo de sensor guardado: $sensorType")
    }

    fun getSensorType(): String {
        return sharedPreferences.getString("sensor_type", "SIMULATED") ?: "SIMULATED"
    }

    // Configuración de calibración del sensor Bluetooth
    fun saveBluetoothSensorCalibration(captureDelay: Long) {
        sharedPreferences.edit().apply {
            putLong("bluetooth_sensor_capture_delay", captureDelay)
            apply()
        }
        Log.d("UserPreferences", "🎯 Calibración del sensor Bluetooth guardada: ${captureDelay}ms")
    }

    fun getBluetoothSensorCalibration(): Long {
        return sharedPreferences.getLong("bluetooth_sensor_capture_delay", 0L) // 0ms por defecto (configuración actual)
    }

               fun resetBluetoothSensorCalibration() {
               sharedPreferences.edit().apply {
                   remove("bluetooth_sensor_capture_delay")
                   apply()
               }
               Log.d("UserPreferences", "🔄 Calibración del sensor Bluetooth restablecida")
           }

           // Configuración de lógica del sensor Bluetooth
           fun saveBluetoothSensorLogic(logic: String) {
               sharedPreferences.edit().apply {
                   putString("bluetooth_sensor_logic", logic)
                   apply()
               }
               Log.d("UserPreferences", "🔄 Lógica del sensor Bluetooth guardada: $logic")
           }

           fun getBluetoothSensorLogic(): String {
               return sharedPreferences.getString("bluetooth_sensor_logic", "bajada") ?: "bajada" // "bajada" por defecto (configuración actual)
           }

           fun resetBluetoothSensorLogic() {
               sharedPreferences.edit().apply {
                   remove("bluetooth_sensor_logic")
                   apply()
               }
               Log.d("UserPreferences", "🔄 Lógica del sensor Bluetooth restablecida")
           }
} 