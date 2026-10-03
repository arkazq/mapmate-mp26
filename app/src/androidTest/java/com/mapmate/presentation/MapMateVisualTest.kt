package com.mapmate.presentation

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mapmate.domain.model.RouteBoardingAdvice
import com.mapmate.domain.model.RouteBoardingStatus
import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.RouteSegment
import com.mapmate.domain.model.RouteSegmentType
import com.mapmate.domain.model.RouteSegmentStatus
import com.mapmate.domain.model.RouteBoardingAlternative
import com.mapmate.presentation.common.MapMateBottomDestination
import com.mapmate.presentation.common.MapMateScaffold
import com.mapmate.presentation.common.toRecommendationUiModel
import com.mapmate.presentation.home.HomeScreen
import com.mapmate.presentation.home.HomeUiState
import com.mapmate.presentation.prediction.PredictionDetailScreen
import com.mapmate.presentation.prediction.PredictionDetailUiState
import com.mapmate.presentation.routine.RoutineRegistrationScreen
import com.mapmate.presentation.routine.RoutineRegistrationUiState
import com.mapmate.presentation.routine.RoutinesScreen
import com.mapmate.presentation.routine.RoutinesUiState
import com.mapmate.presentation.tracking.TrackingScreen
import com.mapmate.presentation.tracking.TrackingUiState
import com.mapmate.presentation.settings.SettingsScreen
import com.mapmate.presentation.settings.SettingsUiState
import com.mapmate.domain.model.AlarmAccessState
import com.mapmate.ui.theme.MapMateTheme
import java.io.File
import java.time.ZonedDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapMateVisualTest {
    @get:Rule val compose = createComposeRule()
    private val now = ZonedDateTime.now()
    private val routine = testRoutine().copy(targetArrivalTime = now.plusMinutes(48).toLocalTime().withSecond(0).withNano(0))
    private val departure = now.plusMinutes(20).toInstant().toEpochMilli()
    private val estimate = RouteEstimate(
        estimatedMinutes = 20, summary = "서울역 → 숭실대학교", providerName = "Test", reason = "Test",
        segments = listOf(
            RouteSegment(segmentIndex = 0, segmentType = RouteSegmentType.WALK_TO_TRANSIT,
                startName = "서울역", endName = "서울역버스환승센터", plannedDurationMinutes = 3),
            RouteSegment(segmentIndex = 1, segmentType = RouteSegmentType.BUS_RIDE, routeName = "753",
                startName = "서울역버스환승센터", endName = "숭실대입구역", plannedDurationMinutes = 15),
            RouteSegment(segmentIndex = 2, segmentType = RouteSegmentType.WALK_TO_DESTINATION,
                startName = "숭실대입구역", endName = "숭실대학교", plannedDurationMinutes = 2),
        ),
        boardingAdvice = RouteBoardingAdvice(1, 5, "753", "서울역버스환승센터", 3, 25, 2,
            RouteBoardingStatus.TIGHT, 20, safeDepartureEpochMillis = departure, earlyDepartureRequiredMinutes = 1),
    )
    private val recommendation = routine.toRecommendationUiModel(
        estimate, now = now.toLocalTime(), recommendedDepartureAtEpochMillis = departure,
        targetArrivalAtEpochMillis = now.plusMinutes(48).toInstant().toEpochMilli(),
        displayedDepartureTime = now.plusMinutes(20).toLocalTime(),
    )

    @Test
    fun homeShowsActualRecommendationWithoutHardcodedHistoryClaims() {
        showHome(false, 1f)
        compose.onNodeWithText("상세 경로").assertIsDisplayed()
        compose.onNodeWithText("이동 시작").assertIsDisplayed()
        saveScreenshot("home-populated-light")
    }

    @Test
    fun narrowDarkHomeKeepsPrimaryActionsVisibleWithLargeText() {
        showHome(true, 1.5f)
        compose.onNodeWithText("상세 경로").assertIsDisplayed()
        compose.onNodeWithText("이동 시작").assertIsDisplayed()
        saveScreenshot("home-populated-dark-large-text")
    }

    @Test
    fun detailShowsRealSegmentsAndSeparatesBasicAndFinalDeparture() {
        compose.setContent {
            MapMateTheme {
                PredictionDetailScreen(PredictionDetailUiState(routine, recommendation, isLoading = false), {}, {}, {})
            }
        }
        compose.onNode(hasText("753번 버스") and hasAnySibling(hasText("서울역버스환승센터 → 숭실대입구역")))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("서울역버스환승센터 → 숭실대입구역").assertIsDisplayed()
        saveScreenshot("prediction-real-segments")
    }

    @Test
    fun detailTabsKeepStartAccessibleAndCalculationRequiresExplicitExpansion() {
        var starts = 0
        var edits = 0
        compose.setContent {
            MapMateTheme {
                PredictionDetailScreen(PredictionDetailUiState(routine, recommendation, isLoading = false),
                    {}, { starts++ }, { edits++ })
            }
        }
        compose.onNodeWithText("이동 시작").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("루틴 수정").performClick()
        compose.onNodeWithText("탑승·출발").performClick()
        compose.onNodeWithText("첫 탑승").assertIsDisplayed()
        compose.onNodeWithText("목표 도착 기준 출발").assertDoesNotExist()
        compose.onNodeWithText("출발 시각 계산 근거 보기").performScrollTo().performClick()
        compose.onNodeWithText("목표 도착 기준 출발").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("이동 시작").assertIsDisplayed()
        saveScreenshot("prediction-calculation-expanded")
        compose.runOnIdle { assertEquals(1, starts); assertEquals(1, edits) }
    }

    @Test
    fun multipleTransitLegsAreOrderedAndWalkingAndTransferTotalsAreReal() {
        val legs = estimate.segments.toMutableList().apply {
            add(2, RouteSegment(segmentIndex = 2, segmentType = RouteSegmentType.TRANSFER_WALK,
                startName = "환승 정류장", endName = "환승역", plannedDurationMinutes = 4))
            add(3, RouteSegment(segmentIndex = 3, segmentType = RouteSegmentType.SUBWAY_RIDE,
                routeName = "7호선", startName = "환승역", endName = "숭실대입구역", plannedDurationMinutes = 10))
        }.mapIndexed { index, segment -> segment.copy(segmentIndex = index) }
        compose.setContent {
            MapMateTheme {
                HomeScreen(HomeUiState(listOf(routine), recommendation.copy(routeSegments = legs, routeDurationMinutes = 34),
                    isLoading = false), {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("환승 1회").assertIsDisplayed()
        compose.onNodeWithText("도보 9분").assertIsDisplayed()
        compose.onNodeWithText("7호선").assertIsDisplayed()
        compose.onNodeWithText("이동 시작").assertIsDisplayed()
        saveScreenshot("home-transfer-route")
    }

    @Test
    fun longPlaceNamesAt320dpAndDoubleFontKeepActionsUsableWithoutTruncatingNames() {
        val longRoutine = routine.copy(origin = routine.origin.copy(name = "서울역버스환승센터 제4승강장 출발지"),
            destination = routine.destination.copy(name = "숭실대학교 정보과학관 정문 앞"))
        var starts = 0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                MapMateTheme {
                    Box(Modifier.width(320.dp).fillMaxHeight()) {
                        HomeScreen(HomeUiState(listOf(longRoutine), recommendation.copy(routine = longRoutine), isLoading = false),
                            {}, {}, {}, { starts++ }, {})
                    }
                }
            }
        }
        compose.onNodeWithText(longRoutine.origin.name).assertIsDisplayed()
        compose.onNodeWithText(longRoutine.destination.name).assertIsDisplayed()
        compose.onNodeWithText("상세 경로").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText("이동 시작").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, starts) }
        listOf("이동 시작", "상세 경로").forEach { label ->
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals("Large-font action label must stay on one line", 1, layouts.single().lineCount)
        }
        saveScreenshot("home-long-names-320-double-font")
    }

    @Test
    fun unavailableRealtimeDoesNotInventArrivalAndFallbackOmitsSegmentBar() {
        val fallback = recommendation.copy(routeSegments = emptyList(), isFallbackEstimate = true,
            boardingAdvice = estimate.boardingAdvice?.copy(realtimeWaitMinutes = null, slackMinutes = null,
                status = RouteBoardingStatus.REALTIME_UNAVAILABLE))
        compose.setContent {
            MapMateTheme {
                HomeScreen(HomeUiState(listOf(routine), fallback, isLoading = false), {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("기본 예상").assertIsDisplayed()
        compose.onNodeWithText("정보 없음").assertIsDisplayed()
        compose.onNodeWithText("25분 후").assertDoesNotExist()
        compose.onNodeWithText("환승 0회").assertDoesNotExist()
        compose.onNodeWithText("도보 5분").assertDoesNotExist()
        saveScreenshot("home-fallback-no-realtime")
    }

    @Test
    fun emptyHomeHasWorkingRegistrationWithoutFakeRoute() {
        var registrations = 0
        compose.setContent {
            MapMateTheme { HomeScreen(HomeUiState(isLoading = false), { registrations++ }, {}, {}, {}, {}) }
        }
        compose.onNodeWithText("루틴 등록").performClick()
        compose.onNodeWithText("이동 시작").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, registrations) }
        saveScreenshot("home-empty")
    }

    @Test
    fun routeLoadFailureHasRetryAndNoActionForNonexistentRecommendation() {
        var retries = 0
        compose.setContent {
            MapMateTheme {
                HomeScreen(HomeUiState(isLoading = false, errorMessage = "경로를 불러오지 못했습니다"),
                    {}, {}, {}, {}, {}, onRefreshClick = { retries++ })
            }
        }
        compose.onNodeWithText("다시 시도").performClick()
        compose.onNodeWithText("이동 시작").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, retries) }
        saveScreenshot("home-error")
    }

    @Test
    fun subwayOnlyRouteDoesNotShowFictitiousFirstBus() {
        val subway = recommendation.copy(routeSegments = listOf(RouteSegment(segmentIndex = 0,
            segmentType = RouteSegmentType.SUBWAY_RIDE, routeName = "7호선", plannedDurationMinutes = 20)),
            boardingAdvice = estimate.boardingAdvice?.copy(routeName = null, stationName = null,
                realtimeWaitMinutes = null, slackMinutes = null, status = RouteBoardingStatus.NO_FIRST_BUS))
        compose.setContent {
            MapMateTheme { HomeScreen(HomeUiState(listOf(routine), subway, isLoading = false), {}, {}, {}, {}, {}) }
        }
        compose.onNodeWithText("7호선").assertIsDisplayed()
        compose.onNodeWithText("실시간 탑승 정보 없음").assertIsDisplayed()
        compose.onNodeWithText("첫 버스").assertDoesNotExist()
        compose.onNodeWithText("버스 도착").assertDoesNotExist()
        saveScreenshot("home-subway-route")
    }

    @Test
    fun currentSegmentActionsAdvanceToTheNextLegAndArrivalRemainsAvailable() {
        val state = mutableStateOf(TrackingUiState(routine, recommendation, isLoading = false,
            routeSegments = estimate.segments.mapIndexed { index, segment -> segment.copy(id = index + 1L) }))
        var arrivals = 0
        compose.setContent {
            MapMateTheme {
                TrackingScreen(state.value, {}, { arrivals++ }, { id ->
                    state.value = state.value.copy(routeSegments = state.value.routeSegments.map {
                        if (it.id == id) it.copy(status = RouteSegmentStatus.IN_PROGRESS,
                            actualStartedAtEpochMillis = System.currentTimeMillis() - 65_000L) else it
                    })
                }, { id ->
                    state.value = state.value.copy(routeSegments = state.value.routeSegments.map {
                        if (it.id == id) it.copy(status = RouteSegmentStatus.COMPLETED,
                            actualEndedAtEpochMillis = System.currentTimeMillis(), actualDurationMinutes = 1) else it
                    })
                })
            }
        }
        compose.onNodeWithText("시작").performClick()
        compose.onNodeWithText("완료").assertIsDisplayed()
        saveScreenshot("tracking-walking-active")
        compose.onNodeWithText("완료").performClick()
        compose.onNodeWithText("현재 구간 2 / 3").assertIsDisplayed()
        compose.onNodeWithText("탑승").performClick()
        compose.onNodeWithText("하차").assertIsDisplayed()
        saveScreenshot("tracking-bus-active")
        compose.onNodeWithText("하차").performClick()
        compose.onNodeWithText("시작").performClick()
        compose.onNodeWithText("완료").performClick()
        compose.onNodeWithText("모든 구간 측정 완료").assertIsDisplayed()
        compose.onNodeWithText("도착 완료").performClick()
        compose.runOnIdle { assertEquals(1, arrivals) }
        saveScreenshot("tracking-all-segments-finished")
    }

    @Test
    fun alternativeRoutesRemainReadableWithLargeTextAndPreserveRiskLabel() {
        val advice = requireNotNull(estimate.boardingAdvice).copy(alternatives = listOf(
            RouteBoardingAlternative(2, "동작03", "대방동주공아파트", 6, 3, -3,
                RouteBoardingStatus.MISS_RISK, 23),
            RouteBoardingAlternative(3, "740", "서울역버스환승센터", 4, 12, 4,
                RouteBoardingStatus.BOARDABLE, 27),
        ))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                MapMateTheme(darkTheme = true) {
                    Box(Modifier.width(320.dp).fillMaxHeight()) {
                        PredictionDetailScreen(PredictionDetailUiState(routine, recommendation.copy(boardingAdvice = advice), isLoading = false), {}, {}, {})
                    }
                }
            }
        }
        compose.onNodeWithText("탑승·출발").performScrollTo().performClick()
        compose.onNodeWithText("동작03번 버스").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("3분 부족").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("740번 버스").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("이동 시작").assertIsDisplayed()
        saveScreenshot("prediction-alternatives-dark-double-font")
    }

    @Test
    fun completedHomeShowsNextDayAndDoesNotOfferAnotherTrackingStart() {
        val next = recommendation.copy(
            recommendedDepartureAtEpochMillis = departure + 24 * 60 * 60_000L,
            targetArrivalAtEpochMillis = requireNotNull(recommendation.targetArrivalAtEpochMillis) + 24 * 60 * 60_000L,
            boardingAdvice = null,
        )
        compose.setContent {
            MapMateTheme {
                HomeScreen(HomeUiState(listOf(routine), next, hasCompletedTodayCommute = true, isLoading = false), {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("오늘 이동 완료").assertIsDisplayed()
        compose.onNodeWithText("수고하셨습니다").assertIsDisplayed()
        compose.onNodeWithText("이동 시작").assertDoesNotExist()
        saveScreenshot("home-completed-next-day")
    }

    @Test
    fun pendingMeasurementRemainsReadableAtDoubleFontSizeWithoutDuplicateStart() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                MapMateTheme {
                    Box(Modifier.width(360.dp).fillMaxHeight()) {
                        HomeScreen(HomeUiState(listOf(routine), recommendation, isLoading = false,
                            pendingTrackingRoutines = listOf(routine)), {}, {}, {}, {}, {})
                    }
                }
            }
        }
        compose.onNodeWithText("측정 이어하기").assertIsDisplayed()
        compose.onNodeWithText("이동 시작").assertDoesNotExist()
        saveScreenshot("home-resume-double-font")
    }

    @Test
    fun narrowRegistrationKeepsStepAndNavigationVisibleAtDoubleFontSize() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                MapMateTheme {
                    Box(Modifier.width(360.dp).fillMaxHeight()) {
                        RoutineRegistrationScreen(
                            RoutineRegistrationUiState(routineName = "등교 루틴"), {}, {},
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("1 / 6 · 기본 정보").assertIsDisplayed()
        compose.onNodeWithText("다음").assertIsDisplayed()
        saveScreenshot("registration-double-font")
    }

    @Test
    fun narrowRoutineListKeepsEditAndDeleteActionsAccessibleAtDoubleFontSize() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                MapMateTheme {
                    Box(Modifier.width(360.dp).fillMaxHeight()) {
                        RoutinesScreen(RoutinesUiState(listOf(recommendation), isLoading = false), {}, {}, {}, {})
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("${routine.name} 수정").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("${routine.name} 삭제").assertIsDisplayed()
        saveScreenshot("routines-double-font")
    }

    @Test
    fun trackingFocusesOnCurrentSegmentAndDoesNotInventCurrentLocation() {
        compose.setContent {
            MapMateTheme {
                TrackingScreen(TrackingUiState(routine, recommendation, isLoading = false,
                    routeSegments = estimate.segments), {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("현재 구간 1 / 3").assertIsDisplayed()
        compose.onNodeWithText("시작").assertIsDisplayed()
        compose.onNodeWithText("도착 완료").assertIsDisplayed()
        compose.onNodeWithText("현재 이동 정보").assertDoesNotExist()
        saveScreenshot("tracking-current-segment")
    }

    @Test
    fun blockedNotificationsAndExactAlarmsShowWorkingSystemSettingsActions() {
        var notificationSettingsClicks = 0
        var exactAlarmSettingsClicks = 0
        compose.setContent {
            MapMateTheme {
                SettingsScreen(SettingsUiState(notificationsEnabled = true), {},
                    alarmAccess = AlarmAccessState(appNotificationsEnabled = false, exactAlarmAllowed = false),
                    onOpenNotificationSettings = { notificationSettingsClicks++ },
                    onOpenExactAlarmSettings = { exactAlarmSettingsClicks++ })
            }
        }
        compose.onNodeWithText("Android에서 출발 알림이 차단되어 있습니다.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("시스템 알림 설정").performScrollTo().performClick()
        compose.onNodeWithText("정확한 알람 허용").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, notificationSettingsClicks)
            assertEquals(1, exactAlarmSettingsClicks)
        }
        compose.onNodeWithText("루틴 초기화").assertDoesNotExist()
        saveScreenshot("settings-notifications-blocked")
    }

    @Test
    fun settingsSaveAndNumericFieldsRemainAccessibleAtDoubleFontSize() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                MapMateTheme {
                    Box(Modifier.width(360.dp).fillMaxHeight()) {
                        SettingsScreen(SettingsUiState(personalBufferMinutes = "12", safetyMarginMinutes = "8",
                            hasUnsavedBufferDefaults = true), {})
                    }
                }
            }
        }
        compose.onNodeWithText("기본 보정 저장").performScrollTo().assertIsDisplayed()
        saveScreenshot("settings-double-font")
    }

    private fun showHome(dark: Boolean, fontScale: Float) {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                MapMateTheme(darkTheme = dark) {
                    Box(Modifier.width(360.dp).fillMaxHeight()) {
                        MapMateScaffold(MapMateBottomDestination.Home, {}) { padding ->
                            HomeScreen(HomeUiState(listOf(routine), recommendation, isLoading = false), {}, {}, {}, {}, {},
                                modifier = Modifier.padding(padding))
                        }
                    }
                }
            }
        }
    }

    private fun saveScreenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "quality-screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
