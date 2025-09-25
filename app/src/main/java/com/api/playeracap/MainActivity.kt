package com.api.playeracap

import SensorConfigScreen
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.api.playeracap.ui.screens.*
import com.api.playeracap.ui.theme.PlayeraCapTheme
import com.api.playeracap.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PlayeraCapTheme {
                val navController = rememberNavController()
                
                LaunchedEffect(Unit) {
                    viewModel.capturedImages.collect { images ->
                        Log.d("MainActivity", "Lista de imágenes actualizada: ${images.size}")
                    }
                }

                val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = currentRoute == "home",
                                onClick = { navController.navigate("home") },
                                icon = { Icon(Icons.Default.Home, "Inicio") },
                                label = { Text("Inicio") }
                            )
                            NavigationBarItem(
                                selected = currentRoute == "camera",
                                onClick = { navController.navigate("camera") },
                                icon = { Icon(Icons.Default.Camera, "Cámara") },
                                label = { Text("Cámara") }
                            )
                            NavigationBarItem(
                                selected = currentRoute == "gallery",
                                onClick = { navController.navigate("gallery") },
                                icon = { Icon(Icons.Default.Image, "Galería") },
                                label = { Text("Galería") }
                            )
                            NavigationBarItem(
                                selected = currentRoute == "sensor_config",
                                onClick = { navController.navigate("sensor_config") },
                                icon = { Icon(Icons.Default.Settings, "Configuración del Sensor") },
                                label = { Text("Sensor") }
                            )
                        }
                    }
                ) { paddingValues ->
                    NavHost(
                        navController = navController,
                        startDestination = "camera",
                        modifier = Modifier.padding(paddingValues)
                    ) {
                        composable("home") { HomeScreen(viewModel) }
                        composable("camera") { 
                            CameraScreen(
                                viewModel = viewModel,
                                navController = navController
                            )
                        }
                        composable("gallery") { 
                            GalleryScreen(
                                viewModel = viewModel,
                                navController = navController
                            )
                        }
                        composable("device_status") { DeviceStatusScreen(viewModel) }
                        composable("sensor_config") { 
                            SensorConfigScreen(
                                viewModel = viewModel,
                                navController = navController
                            )
                        }
                    }
                }
            }
        }
    }
}