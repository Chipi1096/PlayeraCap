package com.api.playeracap.data

// Modelo para representar un bucket almacenado
data class B2Bucket(
    val id: String,
    val name: String,
    val applicationKeyId: String,
    val applicationKey: String,
    val isDefault: Boolean = false
)
