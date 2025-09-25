void loop() {
  // Leer el estado del sensor e invertirlo
  int rawSensorValue = digitalRead(SENSOR_PIN);
  int sensorValue = !rawSensorValue;  // Invertir el valor
  
  // Mostrar estado cada segundo
  unsigned long currentTime = millis();
  if (currentTime - lastDebugTime >= 1000) {
    Serial.println("\n--- Estado actual ---");
    Serial.print("Valor del sensor (invertido): ");
    Serial.println(sensorValue);
    Serial.print("Valor raw del sensor: ");
    Serial.println(rawSensorValue);
    Serial.print("Estado BLE: ");
    Serial.println(deviceConnected ? "Conectado" : "Sin conexión");
    Serial.print("Tiempo desde inicio: ");
    Serial.print(currentTime / 1000);
    Serial.println(" segundos");
    Serial.println("------------------\n");
    
    lastDebugTime = currentTime;
  }

  // Si el valor del sensor cambió
  if (sensorValue != lastSensorValue) {
    Serial.print("¡Cambio detectado! Nuevo valor (invertido): ");
    Serial.println(sensorValue);
    lastSensorValue = sensorValue;
  }

  // Si hay conexión BLE, enviar el valor invertido
  if (deviceConnected) {
    char txString[2];
    sprintf(txString, "%d", sensorValue);
    pCharacteristic->setValue(txString);
    pCharacteristic->notify();
  }

  delay(100); // Pequeño delay para estabilidad
} 