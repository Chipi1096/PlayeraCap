package com.api.playeracap.sensor

interface SensorInterface {
    fun startSensing()
    fun stopSensing()
    fun isActive(): Boolean
    fun getCurrentDistance(): Float
} 