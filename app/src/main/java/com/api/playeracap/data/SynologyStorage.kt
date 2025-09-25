package com.api.playeracap.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.Socket
import java.net.InetAddress
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class SynologyStorage(
    private val serverAddress: String,
    private val port: Int,
    private val username: String,
    private val password: String,
    private val useSFTP: Boolean = false,
    private val baseFolder: String = "PlayeraCap"
) {
    private val TAG = "SynologyStorage"
    
    suspend fun uploadFile(file: File, fileName: String): String {
        Log.d(TAG, "Iniciando subida a Synology NAS: $fileName")
        
        // Tratar 'fileName' como una ruta relativa potencialmente anidada (p.ej. "2025/08/08/IMG.jpg")
        val normalizedRelativePath = fileName.trim().trimStart('/')
        val configuredBase = baseFolder.trim().trim('/').ifEmpty { "PlayeraCap" }
        val dirPart = normalizedRelativePath.substringBeforeLast('/', "")
        val leafName = normalizedRelativePath.substringAfterLast('/')
        val targetFolder = if (dirPart.isNotEmpty()) "$configuredBase/$dirPart" else configuredBase
        val targetRemotePath = "$targetFolder/$leafName"
        
        return try {
            if (!file.exists()) {
                throw Exception("El archivo no existe: ${file.absolutePath}")
            }
            
            Log.d(TAG, "Archivo encontrado, tamaño: ${file.length()} bytes")
            Log.d(TAG, "Carpeta base: $configuredBase | Carpeta destino: $targetFolder | Archivo: $leafName")
            
            if (useSFTP) {
                uploadViaSFTP(file, targetFolder, leafName)
            } else {
                uploadViaFTP(file, targetFolder, leafName)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error en uploadFile", e)
            throw Exception("Error al subir archivo a Synology: ${e.message}")
        }
    }
    
    private suspend fun uploadViaFTP(file: File, targetFolder: String, leafName: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val ftpClient = org.apache.commons.net.ftp.FTPClient()
                
                Log.d(TAG, "Conectando a FTP: $serverAddress:$port")
                ftpClient.connect(serverAddress, port)
                
                if (!ftpClient.login(username, password)) {
                    throw Exception("Error de autenticación FTP")
                }
                
                Log.d(TAG, "Conexión FTP establecida exitosamente")
                
                // Configurar modo binario y pasivo
                ftpClient.setFileType(org.apache.commons.net.ftp.FTP.BINARY_FILE_TYPE)
                ftpClient.enterLocalPassiveMode()
                
                // Crear estructura de carpetas completa bajo baseFolder
                createFTPDirectories(ftpClient, targetFolder)
                
                val remoteFilePath = "$targetFolder/$leafName"
                Log.d(TAG, "Subiendo archivo a (FTP): $remoteFilePath")
                
                FileInputStream(file).use { inputStream ->
                    val success = ftpClient.storeFile(remoteFilePath, inputStream)
                    if (!success) {
                        throw Exception("Error al subir archivo via FTP a $remoteFilePath")
                    }
                }
                
                Log.d(TAG, "Archivo subido exitosamente via FTP: $remoteFilePath")
                
                ftpClient.logout()
                ftpClient.disconnect()
                
                "ftp://$serverAddress:$port/$remoteFilePath"
                
            } catch (e: Exception) {
                Log.e(TAG, "Error en uploadViaFTP", e)
                throw Exception("Error en subida FTP: ${e.message}")
            }
        }
    }
    
    private suspend fun uploadViaSFTP(file: File, targetFolder: String, leafName: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val jsch = com.jcraft.jsch.JSch()
                val session = jsch.getSession(username, serverAddress, port)
                session.setPassword(password)
                
                val config = java.util.Properties()
                config.put("StrictHostKeyChecking", "no")
                session.setConfig(config)
                
                Log.d(TAG, "Conectando a SFTP: $serverAddress:$port")
                session.connect(10_000) // timeout 10s
                
                val channel = session.openChannel("sftp")
                channel.connect(10_000)
                val sftpChannel = channel as com.jcraft.jsch.ChannelSftp
                
                Log.d(TAG, "Conexión SFTP establecida exitosamente")
                
                val pwd = runCatching { sftpChannel.pwd() }.getOrNull().orEmpty()
                val home = runCatching { sftpChannel.home }.getOrNull().orEmpty()
                Log.d(TAG, "SFTP pwd: '$pwd' | home: '$home'")
                val writableBase = resolveWritableBasePath(sftpChannel)
                Log.d(TAG, "Usando base escribible: '$writableBase'")
                
                // Crear estructura de carpetas paso a paso bajo la base actual
                createSFTPDirectories(sftpChannel, targetFolder)
                Log.d(TAG, "Subiendo archivo a (SFTP cwd final): ${runCatching { sftpChannel.pwd() }.getOrNull()} | nombre: $leafName")
                sftpChannel.put(file.absolutePath, leafName)
                
                val finalPath = runCatching { sftpChannel.pwd() }.getOrNull()?.let { "$it/$leafName" } ?: leafName
                Log.d(TAG, "Archivo subido exitosamente via SFTP: $finalPath")
                
                sftpChannel.exit()
                session.disconnect()
                
                "sftp://$serverAddress:$port/$finalPath"
                
            } catch (e: Exception) {
                Log.e(TAG, "Error en uploadViaSFTP", e)
                throw Exception("Error en subida SFTP: ${e.message}")
            }
        }
    }
    
    private fun resolveWritableBasePath(sftp: com.jcraft.jsch.ChannelSftp): String {
        // Intenta detectar un directorio donde el usuario pueda escribir
        val candidates = listOf(
            ".",
            "home",
            "homes/$username",
            "homes",
            "volume1/homes/$username",
            "var/services/homes/$username",
            username
        )
        for (base in candidates) {
            try {
                // Comprobar existencia/cambiando directorio; si no existe, intentar crearlo
                val exists = runCatching { sftp.cd(base); true }.getOrDefault(false)
                if (!exists) {
                    runCatching { sftp.mkdir(base) }
                    runCatching { sftp.cd(base) }.getOrThrow()
                }
                // Probar escritura creando y borrando un archivo temporal
                val testName = ".__write_test_${System.currentTimeMillis()}__"
                val testPath = "$base/$testName"
                val baos = java.io.ByteArrayInputStream(ByteArray(0))
                runCatching { sftp.put(baos, testPath) }.getOrThrow()
                runCatching { sftp.rm(testPath) }.getOrThrow()
                // Volver a pwd base original no es necesario; devolvemos base válido
                return base.trimEnd('/')
            } catch (_: Throwable) {
                // Intentar siguiente candidato
                continue
            }
        }
        // Si ninguno funcionó, devolver vacío (usaremos rutas relativas y que falle con log claro)
        return ""
    }
    
    private fun createFTPDirectories(ftpClient: org.apache.commons.net.ftp.FTPClient, path: String) {
        val directories = path.split("/")
        var currentPath = ""
        
        for (dir in directories) {
            if (dir.isNotEmpty()) {
                currentPath = if (currentPath.isEmpty()) dir else "$currentPath/$dir"
                try {
                    val changed = ftpClient.changeWorkingDirectory(currentPath)
                    if (!changed) {
                        val created = ftpClient.makeDirectory(currentPath)
                        if (created) {
                            Log.d(TAG, "Directorio creado: $currentPath")
                        } else {
                            Log.w(TAG, "No se pudo crear directorio (puede que ya exista o no haya permisos): $currentPath")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error al crear/cambiar a directorio: $currentPath", e)
                }
            }
        }
        // No cambiamos el directorio de trabajo de forma permanente; usamos rutas completas al subir
    }
    
    private fun createSFTPDirectories(sftpChannel: com.jcraft.jsch.ChannelSftp, path: String) {
        if (path.isBlank()) return
        var workingAtRoot = false
        var trimmed = path
        if (path.startsWith('/')) {
            try { sftpChannel.cd("/"); workingAtRoot = true } catch (_: Exception) {}
            trimmed = path.trim('/')
        }
        val parts = trimmed.split('/').filter { it.isNotEmpty() }
        for (part in parts) {
            try {
                sftpChannel.cd(part)
            } catch (_: Exception) {
                try {
                    sftpChannel.mkdir(part)
                    Log.d(TAG, "Directorio SFTP creado: ${if (workingAtRoot) "/" else ""}${part}")
                    try { sftpChannel.cd(part) } catch (_: Exception) {}
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo crear/cambiar a directorio SFTP: $part", e)
                }
            }
        }
    }
    
    suspend fun testConnection(): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                if (useSFTP) {
                    testSFTPConnection()
                } else {
                    testFTPConnection()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en testConnection", e)
            false
        }
    }
    
    private fun testFTPConnection(): Boolean {
        val ftpClient = org.apache.commons.net.ftp.FTPClient()
        return try {
            ftpClient.connect(serverAddress, port)
            val success = ftpClient.login(username, password)
            ftpClient.logout()
            ftpClient.disconnect()
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error en testFTPConnection", e)
            false
        }
    }
    
    private fun testSFTPConnection(): Boolean {
        val jsch = com.jcraft.jsch.JSch()
        return try {
            val session = jsch.getSession(username, serverAddress, port)
            session.setPassword(password)
            
            val config = java.util.Properties()
            config.put("StrictHostKeyChecking", "no")
            session.setConfig(config)
            
            session.connect(5000) // 5 segundos timeout
            session.disconnect()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error en testSFTPConnection", e)
            false
        }
    }
} 