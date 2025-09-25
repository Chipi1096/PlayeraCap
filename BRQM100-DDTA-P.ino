#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

// UUIDs para el servicio y característica BLE
#define SERVICE_UUID        "180F"
#define CHARACTERISTIC_UUID "2A19"

// Pin del sensor y variables de estado
const int SENSOR_PIN = 13;
int lastSensorValue = -1;
unsigned long lastDebugTime = 0;

// Variables globales BLE
BLEServer* pServer = NULL;
BLECharacteristic* pCharacteristic = NULL;
bool deviceConnected = false;

class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
      deviceConnected = true;
      Serial.println("¡Dispositivo conectado!");
      // Detener advertising cuando se conecte
      pServer->getAdvertising()->stop();
    };

    void onDisconnect(BLEServer* pServer) {
      deviceConnected = false;
      Serial.println("Dispositivo desconectado");
      delay(500); // Pequeño delay antes de reiniciar advertising
      Serial.println("Reiniciando advertising...");
      pServer->getAdvertising()->start();
    }
};

void setup() {
  Serial.begin(115200);
  delay(1000);
  
  Serial.println("\n\n--- Iniciando ESP32 BLE Sensor ---");
  
  // Configurar pin del sensor
  pinMode(SENSOR_PIN, INPUT);
  Serial.println("Pin del sensor configurado");

  // Inicializar BLE con nombre más corto
  BLEDevice::init("ESP32");
  
  // Crear el servidor BLE
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  // Crear el servicio BLE
  BLEService *pService = pServer->createService(BLEUUID(SERVICE_UUID));

  // Crear característica BLE
  pCharacteristic = pService->createCharacteristic(
                      BLEUUID(CHARACTERISTIC_UUID),
                      BLECharacteristic::PROPERTY_READ   |
                      BLECharacteristic::PROPERTY_NOTIFY
                    );

  // Agregar descriptor para notificaciones
  pCharacteristic->addDescriptor(new BLE2902());

  // Iniciar el servicio
  pService->start();

  // Configurar advertising con parámetros más agresivos
  BLEAdvertising *pAdvertising = pServer->getAdvertising();
  pAdvertising->addServiceUUID(BLEUUID(SERVICE_UUID));
  pAdvertising->setScanResponse(false);
  pAdvertising->setMinInterval(0x20); // Intervalos más cortos
  pAdvertising->setMaxInterval(0x40); // para mejor detección
  pAdvertising->start();

  Serial.println("BLE iniciado y advertising...");
  Serial.print("Nombre del dispositivo: ");
  Serial.println("ESP32");
  Serial.print("Service UUID: ");
  Serial.println(SERVICE_UUID);
  Serial.println("ESP32 listo y esperando conexiones...");
}

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
    lastDebugTime = currentTime;
  }

  // Si el valor del sensor cambió
  if (sensorValue != lastSensorValue) {
    lastSensorValue = sensorValue;
    if (deviceConnected) {
      // Convertir el valor a string y enviarlo
      char txString[2];
      sprintf(txString, "%d", sensorValue);
      pCharacteristic->setValue((uint8_t*)txString, strlen(txString));
      pCharacteristic->notify();
      
      Serial.print("Valor enviado por BLE: ");
      Serial.println(sensorValue);
    }
  }

  delay(100); // Pequeño delay para estabilidad
} 