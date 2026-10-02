package com.mapmate.presentation

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mapmate.data.local.MapMateDatabase
import com.mapmate.data.preferences.DataStoreTrackingSessionStore
import com.mapmate.data.repository.RoomCommuteRecordRepository
import com.mapmate.data.repository.RoomRoutineRepository
import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.RouteSegment
import com.mapmate.domain.model.RouteSegmentStatus
import com.mapmate.domain.model.RouteSegmentType
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.presentation.tracking.TrackingScreen
import com.mapmate.presentation.tracking.TrackingViewModel
import com.mapmate.presentation.home.HomeScreen
import com.mapmate.presentation.home.HomeViewModel
import com.mapmate.ui.theme.MapMateTheme
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Run preparation, restoration, and completion verification in separate processes with force-stop between them.
@RunWith(AndroidJUnit4::class)
class TrackingRestartTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun database() = Room.databaseBuilder(context, MapMateDatabase::class.java, "quality-tracking-restart.db").build()
    private val sessionStore = DataStoreTrackingSessionStore(context)

    @Test
    fun prepareMeasuredSession() {
        val db = database()
        try {
            runBlocking { sessionStore.clear(ID) }
            db.clearAllTables()
            val clock = ZonedDateTime.now()
            val routine = testRoutine(ID, "재시작 검증").copy(targetArrivalTime = clock.plusHours(1).toLocalTime().withSecond(0).withNano(0))
            val routines = RoomRoutineRepository(db.routineDao())
            runBlocking { routines.saveRoutine(routine) }
            val provider = object : RouteEstimateProvider {
                override suspend fun getRouteEstimate(origin: Destination, destination: Destination, transportMode: TransportMode,
                    routineId: Long?, scheduledDepartureEpochMillis: Long?, targetArrivalEpochMillis: Long?) = RouteEstimate(
                    20, "walk then 753", "Test", "", segments = listOf(
                        RouteSegment(segmentIndex = 0, segmentType = RouteSegmentType.WALK_TO_TRANSIT, plannedDurationMinutes = 3),
                        RouteSegment(segmentIndex = 1, segmentType = RouteSegmentType.BUS_RIDE, routeName = "753", plannedDurationMinutes = 17),
                    ),
                )
            }
            lateinit var model: TrackingViewModel
            compose.runOnUiThread {
                model = TrackingViewModel(routine, provider, RoomCommuteRecordRepository(db), routines,
                    NavigationTestData().settingsRepository, sessionStore, nowProvider = { clock })
            }
            show(model)
            compose.waitUntil(5_000) { !model.uiState.value.isLoading }
            compose.onNodeWithText("시작").performClick()
            compose.waitUntil(5_000) { model.uiState.value.currentSegment?.status == RouteSegmentStatus.IN_PROGRESS }
            compose.onNodeWithText("완료").performClick()
            compose.waitUntil(5_000) { model.uiState.value.currentSegment?.segmentIndex == 1 }
            compose.onNodeWithText("탑승").performClick()
            compose.waitUntil(5_000) { model.uiState.value.currentSegment?.status == RouteSegmentStatus.IN_PROGRESS }
            val stored = runBlocking { requireNotNull(sessionStore.read(ID)) }
            assertEquals(RouteSegmentStatus.COMPLETED, stored.routeSegments.first().status)
            assertEquals(RouteSegmentStatus.IN_PROGRESS, stored.routeSegments.last().status)
        } finally { db.close() }
    }

    @Test
    fun resumeAfterProcessRestartAndPersistCompletion() {
        val db = database()
        try {
            val stored = runBlocking { requireNotNull(sessionStore.read(ID)) }
            val routines = RoomRoutineRepository(db.routineDao())
            val routine = runBlocking { routines.observeRoutines().first().single { it.id == ID } }
            val records = RoomCommuteRecordRepository(db)
            val forbiddenProvider = object : RouteEstimateProvider {
                override suspend fun getRouteEstimate(origin: Destination, destination: Destination, transportMode: TransportMode,
                    routineId: Long?, scheduledDepartureEpochMillis: Long?, targetArrivalEpochMillis: Long?): RouteEstimate =
                    error("Restored measurement must not fetch a new route")
            }
            lateinit var model: TrackingViewModel
            compose.runOnUiThread {
                model = TrackingViewModel(routine, forbiddenProvider, records, routines,
                    NavigationTestData().settingsRepository, sessionStore)
            }
            show(model)
            compose.waitUntil(5_000) { !model.uiState.value.isLoading }
            assertTrue(model.uiState.value.isRestoredSession)
            assertEquals(stored.routeSegments, model.uiState.value.routeSegments)
            compose.onNodeWithText("이전 측정을 이어서 기록합니다").assertIsDisplayed()
            compose.onNodeWithText("하차").performClick()
            compose.waitUntil(5_000) { model.uiState.value.isAllSegmentsFinished }
            compose.onNodeWithText("도착 완료").performClick()
            compose.waitUntil(5_000) { model.uiState.value.completedRecord != null }
            val saved = runBlocking { records.getRecentRecords(5, ID).single() }
            assertEquals(stored.startedAtEpochMillis, saved.startedAtEpochMillis)
            assertEquals(stored.targetArrivalAtEpochMillis, saved.targetArrivalAtEpochMillis)
            assertTrue(saved.routeSegments.all { it.status == RouteSegmentStatus.COMPLETED })
            assertEquals(null, runBlocking { sessionStore.read(ID) })
        } finally {
            runBlocking { sessionStore.clear(ID) }
            db.close()
        }
    }

    private fun show(model: TrackingViewModel) {
        compose.setContent {
            val state by model.uiState.collectAsStateWithLifecycle()
            MapMateTheme {
                TrackingScreen(state, {}, model::onPrimaryActionClick, model::onSegmentStart, model::onSegmentComplete)
            }
        }
    }

    @Test
    fun completionSurvivesAnotherRestartAndHomeExcludesTheSavedArrivalEvent() {
        val db = database()
        var home: HomeViewModel? = null
        try {
            val routines = RoomRoutineRepository(db.routineDao())
            val records = RoomCommuteRecordRepository(db)
            val saved = runBlocking { records.getRecentRecords(5, ID).single() }
            assertTrue(saved.routeSegments.all { it.status == RouteSegmentStatus.COMPLETED })
            assertEquals(null, runBlocking { sessionStore.read(ID) })
            val provider = object : RouteEstimateProvider {
                override suspend fun getRouteEstimate(origin: Destination, destination: Destination, transportMode: TransportMode,
                    routineId: Long?, scheduledDepartureEpochMillis: Long?, targetArrivalEpochMillis: Long?) =
                    RouteEstimate(20, "Saved commute", "Test", "")
            }
            lateinit var model: HomeViewModel
            compose.runOnUiThread {
                model = HomeViewModel(routines, records, provider, trackingSessionStore = sessionStore)
                home = model
                model.setActive(true)
            }
            compose.setContent {
                val state by model.uiState.collectAsStateWithLifecycle()
                MapMateTheme { HomeScreen(state, {}, {}, {}, {}, {}) }
            }
            compose.waitUntil(5_000) { !model.uiState.value.isLoading }
            compose.onNodeWithText("오늘 이동 완료").assertIsDisplayed()
            compose.onNodeWithText("이동 시작").assertDoesNotExist()
            compose.onNodeWithText("측정 이어하기").assertDoesNotExist()
            assertTrue(model.uiState.value.dashboardRecommendation!!.targetArrivalAtEpochMillis != saved.targetArrivalAtEpochMillis)

            val routine = runBlocking { routines.observeRoutines().first().single { it.id == ID } }
            lateinit var tracking: TrackingViewModel
            compose.runOnUiThread {
                tracking = TrackingViewModel(routine, provider, records, routines,
                    NavigationTestData().settingsRepository, sessionStore)
            }
            compose.waitUntil(5_000) { !tracking.uiState.value.isLoading }
            assertTrue(!tracking.uiState.value.canRecord)
            compose.runOnUiThread { tracking.onPrimaryActionClick() }
            assertEquals(1, runBlocking { records.getRecentRecords(5, ID).size })
        } finally {
            compose.runOnUiThread { home?.setActive(false) }
            db.close()
        }
    }

    private companion object { const val ID = 7_777L }
}
