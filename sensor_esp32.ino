#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

// Definición de UUIDs para BLE
#define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"

// Pin del sensor
const int SENSOR_PIN = 13;
const int DEBOUNCE_DELAY = 50; // Tiempo de debounce en ms

BLEServer* pServer = NULL;
BLECharacteristic* pCharacteristic = NULL;
bool deviceConnected = false;
int lastSensorState = HIGH; // Estado anterior del sensor
unsigned long lastDebounceTime = 0;

// Clase para manejar callbacks de conexión BLE
class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
      deviceConnected = true;
      Serial.println("Cliente conectado");
    };

    void onDisconnect(BLEServer* pServer) {
      deviceConnected = false;
      Serial.println("Cliente desconectado");
      // Reiniciar advertising
      BLEDevice::startAdvertising();
    }
};

void setup() {
  Serial.begin(115200);
  
  // Configurar el pin del sensor como entrada
  // El sensor BRQM100-DDTA-P tiene salida PNP, por lo que usamos INPUT_PULLDOWN
  pinMode(SENSOR_PIN, INPUT_PULLDOWN);

  // Crear dispositivo BLE
  BLEDevice::init("SensorBRQM");
  
  // Crear el servidor BLE
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  // Crear el servicio BLE
  BLEService *pService = pServer->createService(SERVICE_UUID);

  // Crear característica BLE
  pCharacteristic = pService->createCharacteristic(
                      CHARACTERISTIC_UUID,
                      BLECharacteristic::PROPERTY_READ   |
                      BLECharacteristic::PROPERTY_NOTIFY
                    );

  // Agregar descriptor
  pCharacteristic->addDescriptor(new BLE2902());

  // Iniciar el servicio
  pService->start();

  // Iniciar advertising
  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(false);
  pAdvertising->setMinPreferred(0x0);
  BLEDevice::startAdvertising();
  
  Serial.println("BRQM100-DDTA-P iniciado, esperando conexión...");
}

void loop() {
  if (deviceConnected) {
    // Leer el estado del sensor con debounce
    int reading = digitalRead(SENSOR_PIN);
    
    if (reading != lastSensorState) {
      lastDebounceTime = millis();
    }
    
    if ((millis() - lastDebounceTime) > DEBOUNCE_DELAY) {
      // Si el estado ha cambiado después del debounce
      if (reading != lastSensorState) {
        lastSensorState = reading;
        
        // Convertir el valor a string
        char txString[2];
        sprintf(txString, "%d", reading);
        
        // Enviar el valor por BLE
        pCharacteristic->setValue(txString);
        pCharacteristic->notify();
        
        // Mostrar en el monitor serial
        Serial.print("Estado del sensor: ");
        Serial.println(reading == HIGH ? "Detectado" : "No detectado");
      }
    }
  }
  
  delay(10); // Pequeño delay para estabilidad
} 