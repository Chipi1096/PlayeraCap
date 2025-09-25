package com.api.playeracap.sensor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

class SimulatedSensor : SensorInterface {
    private var isRunning = false
    private var intervalMs: Long = 3000 // Cambiado de 1000 a 3000
    private var currentDistance: Float = 0f
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    fun setInterval(milliseconds: Long) {
        intervalMs = milliseconds
    }

    override fun startSensing() {
        if (!isRunning) {
            isRunning = true
            job = scope.launch {
                while (isActive) {
                    // Generar un pulso (detectado)
                    currentDistance = 1f
                    delay(1000) // Duración del pulso (1 segundo)
                    
                    // Volver a no detectado
                    currentDistance = 0f
                    
                    // Esperar el intervalo configurado antes del siguiente pulso
                    delay(intervalMs)
                }
            }
        }
    }

    override fun stopSensing() {
        isRunning = false
        job?.cancel()
        job = null
        currentDistance = 0f
    }

    override fun isActive(): Boolean = isRunning

    override fun getCurrentDistance(): Float = currentDistance
} 