package com.mapmate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.mapmate.data.local.MapMateDatabase
import com.mapmate.data.repository.RoomRoutineRepository
import com.mapmate.domain.repository.RoutineRepository
import com.mapmate.presentation.routine.RoutineRegistrationRoute
import com.mapmate.ui.theme.MapMateTheme

class MainActivity : ComponentActivity() {
    private val routineRepository: RoutineRepository by lazy {
        RoomRoutineRepository(MapMateDatabase.getInstance(this).routineDao())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MapMateTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RoutineRegistrationRoute(
                        contentPadding = innerPadding,
                        routineRepository = routineRepository,
                    )
                }
            }
        }
    }
}
