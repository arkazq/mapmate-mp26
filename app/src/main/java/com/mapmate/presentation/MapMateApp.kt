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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mapmate.domain.repository.RoutineRepository
import com.mapmate.domain.repository.SettingsRepository
import com.mapmate.presentation.routine.RoutineRegistrationRoute
import com.mapmate.presentation.settings.SettingsRoute

@Composable
fun MapMateApp(
    contentPadding: PaddingValues,
    routineRepository: RoutineRepository,
    settingsRepository: SettingsRepository,
) {
    var selectedDestinationName by rememberSaveable {
        mutableStateOf(MapMateDestination.Routine.name)
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
                MapMateDestination.Routine -> RoutineRegistrationRoute(
                    contentPadding = PaddingValues(0.dp),
                    routineRepository = routineRepository,
                    settingsRepository = settingsRepository,
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
    Routine(label = "루틴"),
    Settings(label = "설정"),
}
