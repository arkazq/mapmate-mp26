package com.mapmate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.PaddingValues
import androidx.lifecycle.lifecycleScope
import com.mapmate.di.AppContainer
import com.mapmate.domain.model.AlarmAccessState
import com.mapmate.data.alarm.AndroidAlarmAccess
import com.mapmate.data.alarm.DepartureRecheckWorker
import com.mapmate.presentation.MapMateApp
import com.mapmate.ui.theme.MapMateTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var previousAlarmAccess: AlarmAccessState? = null
    private val appContainer: AppContainer by lazy {
        AppContainer.getInstance(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            appContainer.departureAlarmCoordinator.keepAlarmsInSync()
        }
        setContent {
            MapMateTheme {
                    MapMateApp(
                        contentPadding = PaddingValues(),
                        routineRepository = appContainer.routineRepository,
                        commuteRecordRepository = appContainer.commuteRecordRepository,
                        settingsRepository = appContainer.settingsRepository,
                        placeSearchProvider = appContainer.placeSearchProvider,
                        routeEstimateProvider = appContainer.routeEstimateProvider,
                        currentLocationProvider = appContainer.currentLocationProvider,
                        trackingSessionStore = appContainer.trackingSessionStore,
                        alarmAccessProvider = appContainer.alarmAccessProvider,
                        scheduledRouteProvider = appContainer.departureAlarmCoordinator,
                    )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val access = AndroidAlarmAccess.read(this)
        if (previousAlarmAccess != null && previousAlarmAccess != access) {
            DepartureRecheckWorker.enqueueReschedule(this)
        }
        previousAlarmAccess = access
    }
}
