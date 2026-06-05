package com.mapmate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.mapmate.di.AppContainer
import com.mapmate.presentation.MapMateApp
import com.mapmate.ui.theme.MapMateTheme

class MainActivity : ComponentActivity() {
    private val appContainer: AppContainer by lazy {
        AppContainer(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MapMateTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MapMateApp(
                        contentPadding = innerPadding,
                        routineRepository = appContainer.routineRepository,
                        settingsRepository = appContainer.settingsRepository,
                        placeSearchProvider = appContainer.placeSearchProvider,
                        routeEstimateProvider = appContainer.routeEstimateProvider,
                        currentLocationProvider = appContainer.currentLocationProvider,
                    )
                }
            }
        }
    }
}
