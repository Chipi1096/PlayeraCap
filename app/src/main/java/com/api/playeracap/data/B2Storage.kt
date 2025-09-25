package com.api.playeracap.data

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Base64
import android.util.Log

class B2Storage(
    private val applicationKeyId: String,
    private val applicationKey: String,
    private val bucketId: String
) {
    private val client = OkHttpClient()
    private var authToken: String? = null
    private var apiUrl: String? = null
    private var downloadUrl: String? = null

    private suspend fun authorize() {
        try {
            val credentials = "$applicationKeyId:$applicationKey"
            val encodedCredentials = Base64.getEncoder().encodeToString(credentials.toByteArray())

            val request = Request.Builder()
                .url("https://api.backblazeb2.com/b2api/v2/b2_authorize_account")
                .header("Authorization", "Basic $encodedCredentials")
                .build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw Exception("Error de autorización: ${response.body?.string()}")
                    }
                    val responseBody = response.body?.string() ?: throw Exception("Respuesta vacía")
                    Log.d("B2Storage", "Respuesta de autorización: $responseBody")
                    
                    try {
                        val body = JSONObject(responseBody)
                        authToken = body.getString("authorizationToken")
                        apiUrl = body.getString("apiUrl")
                        downloadUrl = body.getString("downloadUrl")
                    } catch (e: Exception) {
                        Log.e("B2Storage", "Error parseando respuesta", e)
                        throw Exception("Error parseando respuesta de autorización: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("B2Storage", "Error en autorización", e)
            throw Exception("Error en autorización: ${e.message}")
        }
    }

    suspend fun uploadFile(file: File, fileName: String): String {
        Log.d("B2Storage", "Iniciando subida de archivo: $fileName")
        
        try {
            // Verificar archivo
            if (!file.exists()) {
                throw Exception("El archivo no existe: ${file.absolutePath}")
            }
            Log.d("B2Storage", "Archivo encontrado, tamaño: ${file.length()} bytes")

            // Autorizar si es necesario
            if (authToken == null) {
                Log.d("B2Storage", "No hay token, autorizando...")
                authorize()
                Log.d("B2Storage", "Autorización exitosa")
            }

            // Obtener URL de subida
            Log.d("B2Storage", "Solicitando URL de subida para bucket: $bucketId")
            val uploadUrlPair = getUploadUrl()
            Log.d("B2Storage", "URL de subida obtenida")

            // Subir archivo
            Log.d("B2Storage", "Iniciando subida del archivo...")
            return uploadFileToUrl(file, fileName, uploadUrlPair.first, uploadUrlPair.second)
        } catch (e: Exception) {
            Log.e("B2Storage", "Error en uploadFile", e)
            throw Exception("Error al subir archivo: ${e.message}")
        }
    }

    private suspend fun getUploadUrl(): Pair<String, String> {
        if (authToken == null || apiUrl == null) {
            Log.d("B2Storage", "No hay token, autorizando...")
            authorize()
        }

        val request = Request.Builder()
            .url("$apiUrl/b2api/v2/b2_get_upload_url")
            .header("Authorization", authToken!!)
            .post(
                RequestBody.create(
                    "application/json".toMediaType(),
                    """{"bucketId": "$bucketId"}"""
                )
            )
            .build()

        return withContext(Dispatchers.IO) {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (!response.isSuccessful) {
                Log.e("B2Storage", "Error en getUploadUrl: $responseBody")
                throw Exception("Error al obtener URL de subida: $responseBody")
            }
            
            try {
                val json = JSONObject(responseBody)
                json.getString("uploadUrl") to json.getString("authorizationToken")
            } catch (e: Exception) {
                Log.e("B2Storage", "Error parseando respuesta de uploadUrl", e)
                throw Exception("Error parseando respuesta de URL de subida: ${e.message}")
            }
        }
    }

    private suspend fun uploadFileToUrl(
        file: File,
        fileName: String,
        uploadUrl: String,
        uploadAuthToken: String
    ): String {
        val request = Request.Builder()
            .url(uploadUrl)
            .header("Authorization", uploadAuthToken)
            .header("X-Bz-File-Name", fileName)
            .header("Content-Type", "image/jpeg")
            .header("X-Bz-Content-Sha1", file.sha1())
            .header("X-Bz-File-Info", """{"Access": "public"}""")
            .post(RequestBody.create("image/jpeg".toMediaType(), file))
            .build()

        return withContext(Dispatchers.IO) {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (!response.isSuccessful) {
                Log.e("B2Storage", "Error en la respuesta: $responseBody")
                throw Exception("Error al subir archivo: $responseBody")
            }
            
            val json = JSONObject(responseBody)
            val fileId = json.getString("fileId")
            val fileName = json.getString("fileName")
            
            Log.d("B2Storage", """
                Archivo subido exitosamente:
                - FileId: $fileId
                - FileName: $fileName
                - Bucket: $bucketId
                - URL: $downloadUrl/file/$bucketId/$fileName
            """.trimIndent())
            
            "$downloadUrl/file/$bucketId/$fileName"
        }
    }

    private fun File.sha1(): String {
        return java.security.MessageDigest.getInstance("SHA-1")
            .digest(readBytes())
            .joinToString("") { "%02x".format(it) }
    }

    suspend fun listFiles(): List<B2File> {
        try {
            if (authToken == null) {
                Log.d("B2Storage", "No hay token, autorizando primero...")
                authorize()
            }

            Log.d("B2Storage", "Listando archivos con token: ${authToken?.take(10)}...")

            val request = Request.Builder()
                .url("$apiUrl/b2api/v2/b2_list_file_names")
                .header("Authorization", authToken!!)
                .post(
                    RequestBody.create(
                        "application/json".toMediaType(),
                        """
                        {
                            "bucketId": "$bucketId",
                            "prefix": "images/",
                            "maxFileCount": 1000
                        }
                        """.trimIndent()
                    )
                )
                .build()

            return withContext(Dispatchers.IO) {
                try {
                    val response = client.newCall(request).execute()
                    val responseBody = response.body?.string()
                    
                    if (!response.isSuccessful) {
                        Log.e("B2Storage", "Error al listar archivos: $responseBody")
                        throw Exception("Error al listar archivos: ${response.code}")
                    }
                    
                    val json = JSONObject(responseBody)
                    val files = json.getJSONArray("files")
                    val fileList = mutableListOf<B2File>()
                    
                    Log.d("B2Storage", "Encontrados ${files.length()} archivos")
                    
                    for (i in 0 until files.length()) {
                        val file = files.getJSONObject(i)
                        val fileName = file.getString("fileName")
                        val fileId = file.getString("fileId")
                        val size = file.getLong("contentLength")
                        val uploadTimestamp = file.getLong("uploadTimestamp")
                        // Usar la URL directa del archivo
                        val url = "$downloadUrl/file/$bucketId/$fileName"
                        
                        Log.d("B2Storage", "Procesando archivo: $fileName")
                        
                        fileList.add(
                            B2File(
                                id = fileId,
                                name = fileName,
                                size = size,
                                uploadTimestamp = uploadTimestamp,
                                url = url
                            )
                        )
                    }
                    
                    fileList
                } catch (e: Exception) {
                    Log.e("B2Storage", "Error al procesar respuesta: ${e.message}")
                    throw e
                }
            }
        } catch (e: Exception) {
            Log.e("B2Storage", "Error al listar archivos: ${e.message}")
            throw e
        }
    }
    
    // Función para probar la conexión a Black Baze
    suspend fun testConnection(): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                // Intentar autorizar para verificar las credenciales
                authorize()
                Log.d("B2Storage", "✅ Conexión a Black Baze verificada exitosamente")
                true
            }
        } catch (e: Exception) {
            Log.e("B2Storage", "❌ Error al verificar conexión a Black Baze: ${e.message}")
            false
        }
    }
} 