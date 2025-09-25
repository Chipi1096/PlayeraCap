package com.api.playeracap.data

data class NASConfig(
    val serverAddress: String = "",
    val port: Int = 22, // Puerto por defecto SFTP para Synology
    val username: String = "EstampadosSC",
    val password: String = "NAS_Txt!2025",
    val quickConnect: String = "EstampadosSC",
    val useSFTP: Boolean = true, // true = SFTP por defecto para Synology
    val isEnabled: Boolean = false,
    val baseFolder: String = "PlayeraCap/linea_1" // Carpeta base por línea
) 