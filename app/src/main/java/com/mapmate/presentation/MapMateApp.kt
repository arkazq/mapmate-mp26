package com.mapmate.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
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
import com.mapmate.domain.repository.CommuteRecordRepository
import com.mapmate.domain.repository.RoutineRepository
import com.mapmate.domain.repository.SettingsRepository
import com.mapmate.presentation.common.MapMateBottomDestination
import com.mapmate.presentation.common.MapMateScaffold
import com.mapmate.presentation.history.RecordsRoute
import com.mapmate.presentation.home.HomeRoute
import com.mapmate.presentation.prediction.PredictionDetailRoute
import com.mapmate.presentation.routine.RoutineRegistrationRoute
import com.mapmate.presentation.routine.RoutinesRoute
import com.mapmate.presentation.settings.SettingsRoute
import com.mapmate.presentation.tracking.TrackingCompletionScreen
import com.mapmate.presentation.tracking.TrackingRoute

@Composable
fun MapMateApp(
    contentPadding: PaddingValues,
    routineRepository: RoutineRepository,
    commuteRecordRepository: CommuteRecordRepository,
    settingsRepository: SettingsRepository,
    placeSearchProvider: PlaceSearchProvider,
    routeEstimateProvider: RouteEstimateProvider,
    currentLocationProvider: CurrentLocationProvider,
) {
    var selectedMainDestinationName by rememberSaveable {
        mutableStateOf(MapMateBottomDestination.Home.name)
    }
    var screen by remember {
        mutableStateOf<MapMateScreen>(MapMateScreen.Home)
    }

    fun openMain(destination: MapMateBottomDestination) {
        selectedMainDestinationName = destination.name
        screen = destination.toScreen()
    }

    fun openRoutineRegistration(
        editingRoutine: Routine?,
        returnDestination: MapMateBottomDestination = MapMateBottomDestination.Routines,
    ) {
        screen = MapMateScreen.RoutineRegistration(
            editingRoutine = editingRoutine,
            returnDestination = returnDestination,
        )
    }

    fun openPrediction(
        routine: Routine,
        returnDestination: MapMateBottomDestination,
    ) {
        screen = MapMateScreen.PredictionDetail(
            routine = routine,
            returnDestination = returnDestination,
        )
    }

    val selectedMainDestination = MapMateBottomDestination.valueOf(selectedMainDestinationName)
    val currentMainDestination = screen.mainDestination ?: selectedMainDestination

    MapMateScaffold(
        modifier = Modifier.padding(contentPadding),
        selectedDestination = currentMainDestination,
        showBottomBar = screen.mainDestination != null,
        onDestinationSelected = ::openMain,
    ) { innerPadding ->
        when (val currentScreen = screen) {
            MapMateScreen.Home -> HomeRoute(
                contentPadding = innerPadding,
                routineRepository = routineRepository,
                routeEstimateProvider = routeEstimateProvider,
                onRegisterRoutineClick = {
                    openRoutineRegistration(
                        editingRoutine = null,
                        returnDestination = MapMateBottomDestination.Home,
                    )
                },
                onEditRoutineClick = {
                    openRoutineRegistration(
                        editingRoutine = it,
                        returnDestination = MapMateBottomDestination.Home,
                    )
                },
                onPredictionClick = {
                    openPrediction(
                        routine = it,
                        returnDestination = MapMateBottomDestination.Home,
                    )
                },
                onStartTrackingClick = {
                    screen = MapMateScreen.Tracking(
                        routine = it,
                        returnDestination = MapMateBottomDestination.Home,
                    )
                },
                onRoutinesClick = {
                    openMain(MapMateBottomDestination.Routines)
                },
            )

            MapMateScreen.Routines -> RoutinesRoute(
                contentPadding = innerPadding,
                routineRepository = routineRepository,
                routeEstimateProvider = routeEstimateProvider,
                onRegisterRoutineClick = {
                    openRoutineRegistration(
                        editingRoutine = null,
                        returnDestination = MapMateBottomDestination.Routines,
                    )
                },
                onEditRoutineClick = {
                    openRoutineRegistration(
                        editingRoutine = it,
                        returnDestination = MapMateBottomDestination.Routines,
                    )
                },
                onPredictionClick = {
                    openPrediction(
                        routine = it,
                        returnDestination = MapMateBottomDestination.Routines,
                    )
                },
            )

            MapMateScreen.Records -> RecordsRoute(
                contentPadding = innerPadding,
                commuteRecordRepository = commuteRecordRepository,
                onRegisterRoutineClick = {
                    openRoutineRegistration(
                        editingRoutine = null,
                        returnDestination = MapMateBottomDestination.Records,
                    )
                },
            )

            MapMateScreen.Settings -> SettingsRoute(
                contentPadding = innerPadding,
                settingsRepository = settingsRepository,
            )

            is MapMateScreen.RoutineRegistration -> RoutineRegistrationRoute(
                contentPadding = innerPadding,
                routineRepository = routineRepository,
                settingsRepository = settingsRepository,
                placeSearchProvider = placeSearchProvider,
                routeEstimateProvider = routeEstimateProvider,
                currentLocationProvider = currentLocationProvider,
                editingRoutine = currentScreen.editingRoutine,
                onBackClick = { openMain(currentScreen.returnDestination) },
                onSaveCompleted = { openMain(MapMateBottomDestination.Home) },
            )

            is MapMateScreen.PredictionDetail -> PredictionDetailRoute(
                contentPadding = innerPadding,
                routine = currentScreen.routine,
                routeEstimateProvider = routeEstimateProvider,
                onBackClick = { openMain(currentScreen.returnDestination) },
                onStartTrackingClick = {
                    screen = MapMateScreen.Tracking(
                        routine = it,
                        returnDestination = currentScreen.returnDestination,
                    )
                },
                onEditRoutineClick = {
                    openRoutineRegistration(
                        editingRoutine = it,
                        returnDestination = currentScreen.returnDestination,
                    )
                },
            )

            is MapMateScreen.Tracking -> TrackingRoute(
                contentPadding = innerPadding,
                routine = currentScreen.routine,
                routeEstimateProvider = routeEstimateProvider,
                commuteRecordRepository = commuteRecordRepository,
                settingsRepository = settingsRepository,
                onBackClick = {
                    screen = MapMateScreen.PredictionDetail(
                        routine = currentScreen.routine,
                        returnDestination = currentScreen.returnDestination,
                    )
                },
                onCompleted = { record ->
                    screen = MapMateScreen.TrackingComplete(
                        record = record,
                        returnDestination = currentScreen.returnDestination,
                    )
                },
            )

            is MapMateScreen.TrackingComplete -> TrackingCompletionScreen(
                contentPadding = innerPadding,
                record = currentScreen.record,
                onBackClick = { openMain(currentScreen.returnDestination) },
                onRecordsClick = { openMain(MapMateBottomDestination.Records) },
                onHomeClick = { openMain(MapMateBottomDestination.Home) },
            )
        }
    }
}

private sealed interface MapMateScreen {
    val mainDestination: MapMateBottomDestination?

    data object Home : MapMateScreen {
        override val mainDestination = MapMateBottomDestination.Home
    }

    data object Routines : MapMateScreen {
        override val mainDestination = MapMateBottomDestination.Routines
    }

    data object Records : MapMateScreen {
        override val mainDestination = MapMateBottomDestination.Records
    }

    data object Settings : MapMateScreen {
        override val mainDestination = MapMateBottomDestination.Settings
    }

    data class RoutineRegistration(
        val editingRoutine: Routine?,
        val returnDestination: MapMateBottomDestination,
    ) : MapMateScreen {
        override val mainDestination: MapMateBottomDestination? = null
    }

    data class PredictionDetail(
        val routine: Routine,
        val returnDestination: MapMateBottomDestination,
    ) : MapMateScreen {
        override val mainDestination: MapMateBottomDestination? = null
    }

    data class Tracking(
        val routine: Routine,
        val returnDestination: MapMateBottomDestination,
    ) : MapMateScreen {
        override val mainDestination: MapMateBottomDestination? = null
    }

    data class TrackingComplete(
        val record: com.mapmate.domain.model.CommuteRecord,
        val returnDestination: MapMateBottomDestination,
    ) : MapMateScreen {
        override val mainDestination: MapMateBottomDestination? = null
    }
}

private fun MapMateBottomDestination.toScreen(): MapMateScreen {
    return when (this) {
        MapMateBottomDestination.Home -> MapMateScreen.Home
        MapMateBottomDestination.Routines -> MapMateScreen.Routines
        MapMateBottomDestination.Records -> MapMateScreen.Records
        MapMateBottomDestination.Settings -> MapMateScreen.Settings
    }
}
