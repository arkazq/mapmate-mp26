package com.mapmate.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mapmate.domain.model.Routine
import com.mapmate.domain.provider.CurrentLocationProvider
import com.mapmate.domain.provider.PlaceSearchProvider
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.domain.repository.RoutineRepository
import com.mapmate.domain.repository.SettingsRepository
import com.mapmate.presentation.home.HomeRoute
import com.mapmate.presentation.routine.RoutineRegistrationRoute
import com.mapmate.presentation.settings.SettingsRoute

@Composable
fun MapMateApp(
    contentPadding: PaddingValues,
    routineRepository: RoutineRepository,
    settingsRepository: SettingsRepository,
    placeSearchProvider: PlaceSearchProvider,
    routeEstimateProvider: RouteEstimateProvider,
    currentLocationProvider: CurrentLocationProvider,
) {
    var selectedDestinationName by rememberSaveable {
        mutableStateOf(MapMateDestination.Home.name)
    }
    var editingRoutine by remember {
        mutableStateOf<Routine?>(null)
    }
    val selectedDestination = MapMateDestination.valueOf(selectedDestinationName)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        PrimaryTabRow(selectedTabIndex = selectedDestination.ordinal) {
            MapMateDestination.entries.forEach { destination ->
                Tab(
                    selected = destination == selectedDestination,
                    onClick = { selectedDestinationName = destination.name },
                    text = { Text(destination.label) },
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when (selectedDestination) {
                MapMateDestination.Home -> HomeRoute(
                    contentPadding = PaddingValues(0.dp),
                    routineRepository = routineRepository,
                    onRegisterRoutineClick = {
                        editingRoutine = null
                        selectedDestinationName = MapMateDestination.Routine.name
                    },
                    onEditRoutineClick = { routine ->
                        editingRoutine = routine
                        selectedDestinationName = MapMateDestination.Routine.name
                    },
                )

                MapMateDestination.Routine -> RoutineRegistrationRoute(
                    contentPadding = PaddingValues(0.dp),
                    routineRepository = routineRepository,
                    settingsRepository = settingsRepository,
                    placeSearchProvider = placeSearchProvider,
                    routeEstimateProvider = routeEstimateProvider,
                    currentLocationProvider = currentLocationProvider,
                    editingRoutine = editingRoutine,
                )

                MapMateDestination.Settings -> SettingsRoute(
                    contentPadding = PaddingValues(0.dp),
                    settingsRepository = settingsRepository,
                )
            }
        }
    }
}

private enum class MapMateDestination(
    val label: String,
) {
    Home(label = "홈"),
    Routine(label = "루틴"),
    Settings(label = "설정"),
}
