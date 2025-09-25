package com.api.playeracap.utils

import android.content.Context
import android.util.Log
import com.api.playeracap.data.UserPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

/**
 * Utilidad para probar la funcionalidad de reconexión automática
 */
class AutoReconnectTest(private val context: Context) {
    
    companion object {
        private const val TAG = "AutoReconnectTest"
    }
    
    private val userPreferences = UserPreferences(context)
    
    /**
     * Simula el proceso de reconexión automática
     */
    fun simulateAutoReconnect(scope: CoroutineScope, onReconnectAttempt: (Int) -> Unit) {
        scope.launch {
            Log.d(TAG, "🧪 Iniciando simulación de reconexión automática")
            
            // Verificar si hay dispositivo guardado
            val lastDeviceAddress = userPreferences.getLastConnectedDeviceAddress()
            if (lastDeviceAddress == null) {
                Log.d(TAG, "❌ No hay dispositivo guardado para reconexión")
                return@launch
            }
            
            val lastDeviceName = userPreferences.getLastConnectedDeviceName()
            Log.d(TAG, "📱 Dispositivo guardado: $lastDeviceName ($lastDeviceAddress)")
            
            // Verificar si la reconexión automática está habilitada
            if (!userPreferences.shouldAutoReconnect()) {
                Log.d(TAG, "⚠️ Reconexión automática deshabilitada")
                return@launch
            }
            
            // Simular intentos de reconexión
            val maxAttempts = 3
            val reconnectDelay = userPreferences.getAutoReconnectDelay()
            
            for (attempt in 1..maxAttempts) {
                Log.d(TAG, "🔄 Intento de reconexión #$attempt")
                onReconnectAttempt(attempt)
                
                delay(reconnectDelay)
                
                // Simular éxito en el segundo intento
                if (attempt == 2) {
                    Log.d(TAG, "✅ Reconexión exitosa en intento #$attempt")
                    break
                } else if (attempt < maxAttempts) {
                    Log.d(TAG, "❌ Reconexión fallida, intentando de nuevo...")
                } else {
                    Log.d(TAG, "❌ Máximo de intentos alcanzado")
                }
            }
        }
    }
    
    /**
     * Prueba la funcionalidad de guardado y recuperación de dispositivos
     */
    fun testDeviceStorage() {
        Log.d(TAG, "🧪 Probando almacenamiento de dispositivos")
        
        // Guardar un dispositivo de prueba
        val testAddress = "AA:BB:CC:DD:EE:FF"
        val testName = "ESP32_Test_Device"
        
        userPreferences.saveLastConnectedDevice(testAddress, testName)
        Log.d(TAG, "💾 Dispositivo de prueba guardado")
        
        // Recuperar el dispositivo guardado
        val savedAddress = userPreferences.getLastConnectedDeviceAddress()
        val savedName = userPreferences.getLastConnectedDeviceName()
        val savedTime = userPreferences.getLastConnectionTime()
        
        Log.d(TAG, "📱 Dispositivo recuperado: $savedName ($savedAddress)")
        Log.d(TAG, "⏰ Tiempo de conexión: ${java.util.Date(savedTime)}")
        
        // Verificar que los datos coinciden
        if (savedAddress == testAddress && savedName == testName) {
            Log.d(TAG, "✅ Prueba de almacenamiento exitosa")
        } else {
            Log.d(TAG, "❌ Prueba de almacenamiento fallida")
        }
        
        // Limpiar datos de prueba
        userPreferences.clearLastConnectedDevice()
        Log.d(TAG, "🗑️ Datos de prueba eliminados")
    }
    
    /**
     * Prueba la configuración de reconexión automática
     */
    fun testAutoReconnectConfig() {
        Log.d(TAG, "🧪 Probando configuración de reconexión automática")
        
        // Probar habilitar/deshabilitar
        userPreferences.setAutoReconnectEnabled(true)
        Log.d(TAG, "🔄 Auto-reconexión habilitada: ${userPreferences.shouldAutoReconnect()}")
        
        userPreferences.setAutoReconnectEnabled(false)
        Log.d(TAG, "🛑 Auto-reconexión deshabilitada: ${userPreferences.shouldAutoReconnect()}")
        
        // Restaurar configuración por defecto
        userPreferences.setAutoReconnectEnabled(true)
        
        // Probar configuración de delay
        val testDelay = 5000L
        userPreferences.setAutoReconnectDelay(testDelay)
        Log.d(TAG, "⏱️ Delay configurado: ${userPreferences.getAutoReconnectDelay()}ms")
        
        if (userPreferences.getAutoReconnectDelay() == testDelay) {
            Log.d(TAG, "✅ Prueba de configuración exitosa")
        } else {
            Log.d(TAG, "❌ Prueba de configuración fallida")
        }
    }
    
    /**
     * Ejecuta todas las pruebas
     */
    fun runAllTests(scope: CoroutineScope) {
        Log.d(TAG, "🚀 Iniciando todas las pruebas de reconexión automática")
        
        testDeviceStorage()
        testAutoReconnectConfig()
        
        simulateAutoReconnect(scope) { attempt ->
            Log.d(TAG, "🔄 Simulando intento de reconexión #$attempt")
        }
        
        Log.d(TAG, "✅ Todas las pruebas completadas")
    }
}
