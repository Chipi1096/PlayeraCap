package com.api.playeracap.data

data class ProductionInfo(
    val modelo: String = "",
    val ordenProducto: String = "",
    val nombreCliente: String = "",
    val colorTela: String = "",
    val encargadoMaquina: String = "",
    // Nuevos campos para configuración del bucket
    val bucketId: String = "",
    val bucketName: String = "",
    val applicationKeyId: String = "",
    val applicationKey: String = "",
    val selectedBucketId: String = "", // ID del bucket seleccionado actualmente
    // Configuración del servidor
    val serverType: ServerType = ServerType.BLACK_BAZE,
    val nasConfig: NASConfig = NASConfig()
) 