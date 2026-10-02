package com.mapmate.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mapmate.domain.model.CommuteRecord
import com.mapmate.domain.model.Routine
import com.mapmate.domain.provider.CurrentLocationProvider
import com.mapmate.domain.provider.PlaceSearchProvider
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.domain.provider.ScheduledRouteProvider
import com.mapmate.domain.provider.AlarmAccessProvider
import com.mapmate.domain.repository.CommuteRecordRepository
import com.mapmate.domain.repository.RoutineRepository
import com.mapmate.domain.repository.SettingsRepository
import com.mapmate.domain.repository.TrackingSessionStore
import com.mapmate.presentation.common.MapMateBottomDestination
import com.mapmate.presentation.common.MapMateScaffold
import com.mapmate.presentation.common.NavigationDataViewModel
import com.mapmate.presentation.common.StoredRecordViewModel
import com.mapmate.presentation.history.RecordsRoute
import com.mapmate.presentation.home.HomeRoute
import com.mapmate.presentation.prediction.PredictionDetailRoute
import com.mapmate.presentation.routine.RoutineRegistrationRoute
import com.mapmate.presentation.routine.RoutinesRoute
import com.mapmate.presentation.segmentedit.RouteSegmentEditRoute
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
    trackingSessionStore: TrackingSessionStore,
    alarmAccessProvider: AlarmAccessProvider,
    scheduledRouteProvider: ScheduledRouteProvider? = null,
) {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: MapMateBottomDestination.Home.name
    val mainDestination = MapMateBottomDestination.entries.firstOrNull { it.name == route }
    val navigationViewModel: NavigationDataViewModel = viewModel(factory = NavigationDataViewModel.factory(routineRepository))
    val navigationData by navigationViewModel.uiState.collectAsStateWithLifecycle()
    val routines = navigationData.routines.orEmpty()

    @Composable
    fun routineUnavailable() {
        if (navigationData.routines == null && navigationData.errorMessage == null) {
            LoadingDestination(contentPadding)
        } else {
            UnavailableDestination(
                message = navigationData.errorMessage ?: "루틴이 삭제되었거나 더 이상 사용할 수 없어요.",
                contentPadding = contentPadding,
                onBackClick = { navController.popBackStack() },
                onRetryClick = navigationData.errorMessage?.let { { navigationViewModel.retry() } },
            )
        }
    }

    fun openRegistration(routine: Routine? = null) {
        navController.navigate("registration/${routine?.id ?: 0L}") { launchSingleTop = true }
    }

    fun openPrediction(routine: Routine) {
        routine.id?.let { navController.navigate("prediction/$it") { launchSingleTop = true } }
    }

    fun openTracking(routine: Routine) {
        routine.id?.let { navController.navigate("tracking/$it") { launchSingleTop = true } }
    }

    MapMateScaffold(
        modifier = Modifier.padding(contentPadding),
        selectedDestination = mainDestination,
        showBottomBar = mainDestination != null,
        onDestinationSelected = navController::openMain,
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = MapMateBottomDestination.Home.name,
        ) {
            composable(MapMateBottomDestination.Home.name) {
                HomeRoute(
                    contentPadding = innerPadding,
                    routineRepository = routineRepository,
                    commuteRecordRepository = commuteRecordRepository,
                    routeEstimateProvider = routeEstimateProvider,
                    onRegisterRoutineClick = { openRegistration() },
                    onEditRoutineClick = ::openRegistration,
                    onPredictionClick = ::openPrediction,
                    onStartTrackingClick = ::openTracking,
                    onRoutinesClick = { navController.openMain(MapMateBottomDestination.Routines) },
                    onSettingsClick = { navController.openMain(MapMateBottomDestination.Settings) },
                    scheduledRouteProvider = scheduledRouteProvider,
                    trackingSessionStore = trackingSessionStore,
                )
            }
            composable(MapMateBottomDestination.Routines.name) {
                RoutinesRoute(
                    contentPadding = innerPadding,
                    routineRepository = routineRepository,
                    routeEstimateProvider = routeEstimateProvider,
                    commuteRecordRepository = commuteRecordRepository,
                    trackingSessionStore = trackingSessionStore,
                    scheduledRouteProvider = scheduledRouteProvider,
                    onRegisterRoutineClick = { openRegistration() },
                    onEditRoutineClick = ::openRegistration,
                    onPredictionClick = ::openPrediction,
                )
            }
            composable(MapMateBottomDestination.Records.name) {
                RecordsRoute(
                    contentPadding = innerPadding,
                    commuteRecordRepository = commuteRecordRepository,
                    onRegisterRoutineClick = { openRegistration() },
                    onEditSegmentsClick = { record ->
                        record.id?.let {
                            navController.navigate("segmentEdit/$it") { launchSingleTop = true }
                        }
                    },
                )
            }
            composable(MapMateBottomDestination.Settings.name) {
                SettingsRoute(contentPadding = innerPadding, settingsRepository = settingsRepository,
                    alarmAccessProvider = alarmAccessProvider)
            }
            composable(
                route = "registration/{routineId}",
                arguments = listOf(navArgument("routineId") { type = NavType.LongType }),
            ) { backStackEntry ->
                val routineId = backStackEntry.arguments?.getLong("routineId") ?: 0L
                val routine = routines.firstOrNull { it.id == routineId }
                if (routineId == 0L || routine != null) {
                    RoutineRegistrationRoute(
                        contentPadding = innerPadding,
                        routineRepository = routineRepository,
                        settingsRepository = settingsRepository,
                        placeSearchProvider = placeSearchProvider,
                        routeEstimateProvider = routeEstimateProvider,
                        currentLocationProvider = currentLocationProvider,
                        scheduledRouteProvider = scheduledRouteProvider,
                        editingRoutine = routine,
                        onBackClick = { navController.popBackStack() },
                        onSaveCompleted = { navController.finishFlow(MapMateBottomDestination.Home) },
                    )
                } else {
                    routineUnavailable()
                }
            }
            composable(
                route = "prediction/{routineId}",
                arguments = listOf(navArgument("routineId") { type = NavType.LongType }),
            ) { backStackEntry ->
                val routine = routines.firstOrNull {
                    it.id == backStackEntry.arguments?.getLong("routineId")
                }
                if (routine != null) {
                    PredictionDetailRoute(
                        contentPadding = innerPadding,
                        routine = routine,
                        routeEstimateProvider = routeEstimateProvider,
                        commuteRecordRepository = commuteRecordRepository,
                        onBackClick = { navController.popBackStack() },
                        onStartTrackingClick = ::openTracking,
                        onEditRoutineClick = ::openRegistration,
                        scheduledRouteProvider = scheduledRouteProvider,
                    )
                } else {
                    routineUnavailable()
                }
            }
            composable(
                route = "tracking/{routineId}",
                arguments = listOf(navArgument("routineId") { type = NavType.LongType }),
            ) { backStackEntry ->
                val routine = routines.firstOrNull {
                    it.id == backStackEntry.arguments?.getLong("routineId")
                }
                if (routine != null) {
                    TrackingRoute(
                        contentPadding = innerPadding,
                        routine = routine,
                        routeEstimateProvider = routeEstimateProvider,
                        commuteRecordRepository = commuteRecordRepository,
                        routineRepository = routineRepository,
                        settingsRepository = settingsRepository,
                        trackingSessionStore = trackingSessionStore,
                        scheduledRouteProvider = scheduledRouteProvider,
                        onBackClick = { navController.popBackStack() },
                        onCompleted = { record ->
                            record.id?.let {
                                navController.navigate("completion/$it") {
                                    popUpTo(backStackEntry.destination.id) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        },
                    )
                } else {
                    routineUnavailable()
                }
            }
            composable(
                route = "completion/{recordId}",
                arguments = listOf(navArgument("recordId") { type = NavType.LongType }),
            ) { backStackEntry ->
                BackHandler { navController.finishFlow(MapMateBottomDestination.Home) }
                StoredRecordDestination(
                    recordId = backStackEntry.arguments?.getLong("recordId") ?: 0L,
                    repository = commuteRecordRepository,
                    contentPadding = innerPadding,
                    onBackClick = { navController.finishFlow(MapMateBottomDestination.Home) },
                ) { record ->
                    TrackingCompletionScreen(
                        contentPadding = innerPadding,
                        record = record,
                        onBackClick = { navController.finishFlow(MapMateBottomDestination.Home) },
                        onEditSegmentsClick = {
                            navController.navigate("segmentEdit/${record.id}") { launchSingleTop = true }
                        },
                        onRecordsClick = { navController.finishFlow(MapMateBottomDestination.Records) },
                        onHomeClick = { navController.finishFlow(MapMateBottomDestination.Home) },
                    )
                }
            }
            composable(
                route = "segmentEdit/{recordId}",
                arguments = listOf(navArgument("recordId") { type = NavType.LongType }),
            ) { backStackEntry ->
                StoredRecordDestination(
                    recordId = backStackEntry.arguments?.getLong("recordId") ?: 0L,
                    repository = commuteRecordRepository,
                    contentPadding = innerPadding,
                    onBackClick = { navController.popBackStack() },
                ) { record ->
                    RouteSegmentEditRoute(
                        contentPadding = innerPadding,
                        record = record,
                        commuteRecordRepository = commuteRecordRepository,
                        onBackClick = { navController.popBackStack() },
                        onSaveCompleted = {
                            val previousRoute = navController.previousBackStackEntry?.destination?.route
                            if (previousRoute == "completion/{recordId}") {
                                navController.finishFlow(MapMateBottomDestination.Home)
                            } else {
                                navController.popBackStack()
                            }
                        },
                    )
                }
            }
        }
    }
}

private fun NavHostController.openMain(destination: MapMateBottomDestination) {
    navigate(destination.name) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.finishFlow(destination: MapMateBottomDestination) {
    navigate(destination.name) {
        popUpTo(graph.startDestinationId)
        launchSingleTop = true
    }
}

@Composable
private fun StoredRecordDestination(
    recordId: Long,
    repository: CommuteRecordRepository,
    contentPadding: PaddingValues,
    onBackClick: () -> Unit,
    content: @Composable (CommuteRecord) -> Unit,
) {
    val recordViewModel: StoredRecordViewModel = viewModel(factory = StoredRecordViewModel.factory(repository, recordId))
    val recordState by recordViewModel.uiState.collectAsStateWithLifecycle()
    val record = recordState.record
    when {
        recordState.isLoading -> LoadingDestination(contentPadding)
        record != null -> content(record)
        else -> UnavailableDestination(
            message = recordState.errorMessage ?: "이동 기록을 찾을 수 없어요.",
            contentPadding = contentPadding,
            onBackClick = onBackClick,
            onRetryClick = recordState.errorMessage?.let { { recordViewModel.retry() } },
        )
    }
}

@Composable
private fun UnavailableDestination(message: String, contentPadding: PaddingValues, onBackClick: () -> Unit, onRetryClick: (() -> Unit)?) {
    Column(
        Modifier.fillMaxSize().padding(contentPadding).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(message)
        onRetryClick?.let { TextButton(onClick = it) { Text("다시 시도") } }
        TextButton(onClick = onBackClick) { Text("돌아가기") }
    }
}

@Composable
private fun LoadingDestination(contentPadding: PaddingValues) {
    Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
