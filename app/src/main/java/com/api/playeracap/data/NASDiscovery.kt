package com.api.playeracap.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.*
import java.io.IOException
import java.util.concurrent.TimeUnit

data class NASDevice(
    val ipAddress: String,
    val hostname: String? = null,
    val isSynology: Boolean = false,
    val services: List<String> = emptyList(),
    val responseTime: Long = 0
)

class NASDiscovery {
    private val TAG = "NASDiscovery"
    
    // Puertos comunes para servicios NAS
    private val commonPorts = listOf(21, 22, 80, 443, 5000, 5001, 8080)
    
    // Servicios Synology conocidos
    private val synologyServices = listOf(
        "DSM" to 5000,  // DSM Web Interface
        "SSH" to 22,    // SSH/SFTP
        "FTP" to 21,    // FTP
        "HTTPS" to 443  // HTTPS
    )
    
    suspend fun discoverNASDevices(): List<NASDevice> = withContext(Dispatchers.IO) {
        val discoveredDevices = mutableListOf<NASDevice>()
        try {
            // Obtener la dirección IP local
            val localIP = getLocalIPAddress()
            if (localIP == null) {
                Log.e(TAG, "No se pudo obtener la dirección IP local")
                return@withContext emptyList()
            }
            Log.d(TAG, "Iniciando descubrimiento desde: $localIP")
            // Escanear toda la subred (254 IPs)
            val ipRanges = generateIPRanges(localIP)
            withTimeout(30000) { // 30 segundos de timeout máximo
                for (ipRange in ipRanges) {
                    try {
                        if (pingHost(ipRange)) {
                            Log.d(TAG, "Dispositivo encontrado: $ipRange")
                            val isSynology = detectSynologyDevice(ipRange)
                            val hostname = getHostname(ipRange)
                            val device = NASDevice(
                                ipAddress = ipRange,
                                hostname = hostname,
                                isSynology = isSynology,
                                services = if (isSynology) listOf("DSM") else emptyList()
                            )
                            discoveredDevices.add(device)
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Error escaneando $ipRange: ${e.message}")
                    }
                }
            }
            Log.d(TAG, "Descubrimiento completado. Dispositivos encontrados: ${discoveredDevices.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Error durante el descubrimiento", e)
        }
        discoveredDevices
    }
    
    private fun getLocalIPAddress(): String? {
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress("8.8.8.8", 80))
            val localIP = socket.localAddress.hostAddress
            socket.close()
            localIP
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo IP local", e)
            null
        }
    }
    
    private fun generateIPRanges(localIP: String): List<String> {
        // Ignorar localIP y siempre usar la subred 192.168.100.x
        val baseIP = "192.168.100"
        return (1..254).map { "$baseIP.$it" }
    }
    
    private suspend fun scanIPRange(ipRange: String): List<NASDevice> = withContext(Dispatchers.IO) {
        val devices = mutableListOf<NASDevice>()
        
        try {
            // Verificar si el dispositivo responde al ping
            if (pingHost(ipRange)) {
                Log.d(TAG, "Dispositivo encontrado: $ipRange")
                
                // Detectar servicios disponibles
                val services = detectServices(ipRange)
                val isSynology = detectSynologyDevice(ipRange)
                val hostname = getHostname(ipRange)
                
                if (services.isNotEmpty() || isSynology) {
                    val device = NASDevice(
                        ipAddress = ipRange,
                        hostname = hostname,
                        isSynology = isSynology,
                        services = services
                    )
                    devices.add(device)
                    Log.d(TAG, "NAS detectado: $ipRange - Synology: $isSynology - Servicios: $services")
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error escaneando $ipRange: ${e.message}")
        }
        
        devices
    }
    
    private fun pingHost(ipAddress: String): Boolean {
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress(ipAddress, 80), 100) // 100ms timeout para ser muy rápido
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    private suspend fun detectServices(ipAddress: String): List<String> = withContext(Dispatchers.IO) {
        val detectedServices = mutableListOf<String>()
        
        // Solo verificar los puertos más importantes para NAS
        val importantPorts = listOf(21, 22, 5000, 5001)
        
        for (port in importantPorts) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(ipAddress, port), 200) // 200ms timeout para ser más rápido
                socket.close()
                
                val serviceName = when (port) {
                    21 -> "FTP"
                    22 -> "SSH/SFTP"
                    5000 -> "DSM"
                    5001 -> "DSM-HTTPS"
                    else -> "Puerto-$port"
                }
                
                detectedServices.add(serviceName)
                Log.d(TAG, "Servicio detectado en $ipAddress:$port - $serviceName")
                
            } catch (e: Exception) {
                // Puerto cerrado o no accesible
            }
        }
        
        detectedServices
    }
    
    private suspend fun detectSynologyDevice(ipAddress: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Solo verificar el puerto DSM (5000) para ser más rápido
            val socket = Socket()
            socket.connect(InetSocketAddress(ipAddress, 5000), 100) // 100ms timeout
            socket.close()
            
            Log.d(TAG, "Dispositivo Synology detectado en: $ipAddress")
            true
            
        } catch (e: Exception) {
            false
        }
    }
    
    private suspend fun getHostname(ipAddress: String): String? = withContext(Dispatchers.IO) {
        try {
            val inetAddress = InetAddress.getByName(ipAddress)
            inetAddress.hostName
        } catch (e: Exception) {
            null
        }
    }
    
    // Función para probar la conectividad a un NAS específico
    suspend fun testNASConnection(ipAddress: String, port: Int, username: String, password: String, useSFTP: Boolean): Boolean {
        return try {
            val storage = SynologyStorage(
                serverAddress = ipAddress,
                port = port,
                username = username,
                password = password,
                useSFTP = useSFTP
            )
            
            storage.testConnection()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error probando conexión a $ipAddress:$port", e)
            false
        }
    }
    
    // Función para obtener información detallada de un dispositivo
    suspend fun getDeviceInfo(ipAddress: String): NASDevice? = withContext(Dispatchers.IO) {
        try {
            if (!pingHost(ipAddress)) {
                return@withContext null
            }
            
            val services = detectServices(ipAddress)
            val isSynology = detectSynologyDevice(ipAddress)
            val hostname = getHostname(ipAddress)
            
            NASDevice(
                ipAddress = ipAddress,
                hostname = hostname,
                isSynology = isSynology,
                services = services
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo información del dispositivo $ipAddress", e)
            null
        }
    }
} 