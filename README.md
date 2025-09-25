# PlayeraCap

**PlayeraCap** es una app Android en **Kotlin** para capturar fotos de producción textil con metadatos (modelo, orden, cliente, color, operador), optimizando flujo y automatizando la captura.

---

## 🚀 Tecnologías

- **Lenguaje:** Kotlin (Android)  
- **UI:** Jetpack Compose (Material3, Scaffold, Card, Icon, Text, Dialog)  
- **Cámara:** CameraX (ImageCapture, Preview, ProcessCameraProvider)  
- **Interop:** AndroidView + PreviewView, Camera2 (Camera2CameraControl, CaptureRequest)  
- **Permisos:** Activity Result API (`rememberLauncherForActivityResult`, `RequestPermission`)  
- **Almacenamiento:** MediaStore/ContentResolver (`Pictures/PlayeraCap`)  
- **Estado/Reactivo:** Compose `remember`/`mutableStateOf`, `LaunchedEffect`, Kotlin Flow (`collectAsState`)  
- **Arquitectura:** MVVM (`MainViewModel`), navegación con `NavController`  
- **Concurrencia:** Coroutines (`rememberCoroutineScope`, `launch`, `delay`)  
- **Preferencias:** UserPreferences (DataStore) para velocidad de obturación  
- **Sensores:** SensorType para automatización de captura  
- **Subida de imágenes:** `viewModel.uploadAndClearImages()`

---

## ✨ Características

- Captura **manual o automática** por sensor  
- **Zoom dinámico** y control de velocidad  
- Vista previa y modo expandido  
- Logs y notificaciones (toasts)  
- Gestión de **metadatos completos**  
- Subida eficiente al servidor y almacenamiento local

---

## 💡 Por qué es relevante

- Código **modular y mantenible** (MVVM + Compose)  
- Integración completa de **hardware y software** de cámara  
- Optimizada para **producción textil**  
- Experiencia en **concurrencia y flujos reactivos** con Kotlin Flow y Coroutines
