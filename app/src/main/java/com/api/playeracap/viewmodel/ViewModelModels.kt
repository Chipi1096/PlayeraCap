package com.api.playeracap.viewmodel

import android.net.Uri
import com.api.playeracap.data.ProductionInfo

sealed class UploadStatus {
    object Idle : UploadStatus()
    data class Uploading(val progress: Int) : UploadStatus()
    object Success : UploadStatus()
    data class Error(val message: String) : UploadStatus()
}

sealed class ClearImagesResult {
    object Success : ClearImagesResult()
    data class Error(val message: String) : ClearImagesResult()
}

data class CapturedImage(
    val uri: Uri,
    val metadata: String,
    var b2Url: String? = null,
    val productionInfo: ProductionInfo? = null
)

sealed class StorageStatus {
    object OK : StorageStatus()
    object Critical : StorageStatus()
}

sealed class DeleteImagesResult {
    object Success : DeleteImagesResult()
    data class Error(val message: String) : DeleteImagesResult()
}

enum class ServerConnectionStatus {
    CONNECTED,
    DISCONNECTED,
    UNKNOWN
} 