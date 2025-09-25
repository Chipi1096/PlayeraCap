#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

// Definir el pin donde conectaremos la salida del sensor
#define SENSOR_PIN 13  // Ajusta este pin según tu conexión

// Estructura de datos del sensor
struct SensorData {
    bool detected;      // Estado de detección (true/false)
    uint32_t timestamp; // Timestamp en milisegundos
    uint8_t battery;    // Nivel de batería
} __attribute__((packed));

// UUIDs para el servicio BLE
#define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"

BLEServer* pServer = nullptr;
BLECharacteristic* pCharacteristic = nullptr;
bool deviceConnected = false;
bool oldDeviceConnected = false;
SensorData sensorData;

// Variables para el debouncing del sensor
unsigned long lastDebounceTime = 0;
unsigned long debounceDelay = 50;    // Ajusta según necesidades
bool lastSensorState = false;

class ServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
        deviceConnected = true;
    };

    void onDisconnect(BLEServer* pServer) {
        deviceConnected = false;
    }
};

void setup() {
    Serial.begin(115200);
    
    // Configurar el pin del sensor como entrada
    pinMode(SENSOR_PIN, INPUT);
    
    // Crear dispositivo BLE
    BLEDevice::init("BRQM100_Sensor");
    pServer = BLEDevice::createServer();
    pServer->setCallbacks(new ServerCallbacks());
    
    // Crear servicio BLE
    BLEService *pService = pServer->createService(SERVICE_UUID);
    
    // Crear característica
    pCharacteristic = pService->createCharacteristic(
        CHARACTERISTIC_UUID,
        BLECharacteristic::PROPERTY_READ |
        BLECharacteristic::PROPERTY_NOTIFY
    );
    
    pCharacteristic->addDescriptor(new BLE2902());
    
    // Iniciar el servicio
    pService->start();
    
    // Iniciar advertising
    BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
    pAdvertising->addServiceUUID(SERVICE_UUID);
    pAdvertising->setScanResponse(true);
    pAdvertising->setMinPreferred(0x06);
    BLEDevice::startAdvertising();
}

void loop() {
    if (deviceConnected) {
        // Leer el sensor con debouncing
        bool reading = digitalRead(SENSOR_PIN);
        
        if (reading != lastSensorState) {
            lastDebounceTime = millis();
        }
        
        if ((millis() - lastDebounceTime) > debounceDelay) {
            // Actualizar datos del sensor
            sensorData.detected = reading;
            sensorData.timestamp = millis();
            sensorData.battery = getBatteryLevel();
            
            // Enviar datos
            pCharacteristic->setValue((uint8_t*)&sensorData, sizeof(SensorData));
            pCharacteristic->notify();
            
            // Debug por Serial
            Serial.print("Estado sensor: ");
            Serial.println(reading ? "Detectado" : "No detectado");
        }
        
        lastSensorState = reading;
    }
    
    // Manejar reconexión
    if (!deviceConnected && oldDeviceConnected) {
        delay(500);
        pServer->startAdvertising();
        oldDeviceConnected = deviceConnected;
    }
    if (deviceConnected && !oldDeviceConnected) {
        oldDeviceConnected = deviceConnected;
    }
}

uint8_t getBatteryLevel() {
    // Implementa la lógica de medición de batería si es necesario
    return 100;
} 