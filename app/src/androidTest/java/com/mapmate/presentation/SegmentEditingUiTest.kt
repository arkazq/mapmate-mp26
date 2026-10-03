package com.mapmate.presentation

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mapmate.domain.model.CommuteRecord
import com.mapmate.domain.model.RouteSegment
import com.mapmate.domain.model.RouteSegmentStatus
import com.mapmate.domain.model.RouteSegmentType
import com.mapmate.domain.model.TransportMode
import com.mapmate.presentation.history.RecordsScreen
import com.mapmate.presentation.history.RecordsStats
import com.mapmate.presentation.history.RecordsUiState
import com.mapmate.presentation.segmentedit.RouteSegmentEditScreen
import com.mapmate.presentation.segmentedit.RouteSegmentEditViewModel
import com.mapmate.ui.theme.MapMateTheme
import java.io.File
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SegmentEditingUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun datePickerPreservesFollowingDateWhenEditingAnOvernightRecord() {
        val model = showEditor(1f)
        compose.onNodeWithContentDescription("1구간 종료 시각 수정").performScrollTo().performClick()
        compose.onNodeWithText("종료 시각 선택").assertIsDisplayed()
        screenshot("segment-time-picker")
        compose.onNodeWithText("2026.10.02").performClick()
        screenshot("segment-date-picker")
        compose.onNodeWithText("날짜 선택").performClick()
        compose.onNodeWithText("확인").performClick()
        compose.runOnIdle {
            assertEquals(epoch(2, 0, 7), model.uiState.value.segments.single().actualEndedAtEpochMillis)
        }
    }

    @Test fun doubleFontTimeInputUpdatesDurationAndBackRequiresDiscardConfirmation() {
        var backClicks = 0
        val model = showEditor(2f) { backClicks++ }
        compose.onNodeWithContentDescription("1구간 종료 시각 수정").performScrollTo().performClick()
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("12")
        screenshot("segment-time-input-double-font")
        compose.onNodeWithText("2026.10.02").performClick()
        screenshot("segment-date-input-double-font")
        compose.onNodeWithText("날짜 선택").performClick()
        compose.onNodeWithText("확인").performClick()
        compose.runOnIdle {
            assertEquals(14, model.uiState.value.segments.single().actualDurationMinutes)
        }
        compose.onNodeWithText("수정 전 전체 이동").performScrollTo().assertIsDisplayed()
        screenshot("segment-edit-double-font")
        compose.onNodeWithContentDescription("뒤로").performScrollTo().performClick()
        compose.onNodeWithText("시간 수정을 버릴까요?").assertIsDisplayed()
        compose.onNodeWithText("계속 수정").performClick()
        compose.runOnIdle { assertEquals(0, backClicks) }
        compose.onNodeWithContentDescription("뒤로").performClick()
        compose.onNodeWithText("버리기").performClick()
        compose.runOnIdle { assertEquals(1, backClicks) }
    }

    @Test fun manualTimingSavePersistsOnlyChangedTimingAndReturnsTheUpdatedRecord() {
        val model = showEditor(1.5f)
        compose.onNodeWithContentDescription("1구간 종료 시각 수정").performScrollTo().performClick()
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("12")
        compose.onNodeWithText("확인").performClick()
        compose.onNodeWithText("수정 저장").performScrollTo().performClick()
        compose.waitUntil(5_000) { model.uiState.value.savedRecord != null }
        compose.runOnIdle {
            val segment = model.uiState.value.savedRecord!!.routeSegments.single()
            assertEquals(epoch(2, 0, 12), segment.actualEndedAtEpochMillis)
            assertEquals(14, segment.actualDurationMinutes)
            assertTrue(segment.isUserEdited)
            assertEquals(RouteSegmentStatus.COMPLETED, segment.status)
        }
    }

    @Test fun recordsShowBothDatesAndAnAccessibleEditActionAtDoubleFontSize() {
        val record = overnightRecord()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                MapMateTheme {
                    Box(Modifier.width(360.dp).fillMaxHeight()) {
                        RecordsScreen(RecordsUiState(records = listOf(record), isLoading = false,
                            stats = RecordsStats.from(listOf(record))), PaddingValues(), {}, {}, {})
                    }
                }
            }
        }
        compose.onNodeWithText("2026.10.01 23:58").performScrollTo().assertIsDisplayed()
        screenshot("records-overnight-double-font")
        compose.onNodeWithText("구간별 시간 수정").performScrollTo().assertIsDisplayed()
        screenshot("records-edit-action-double-font")
    }

    private fun showEditor(fontScale: Float, onBack: () -> Unit = {}): RouteSegmentEditViewModel {
        val record = overnightRecord()
        val data = NavigationTestData()
        data.records.value = listOf(record)
        lateinit var model: RouteSegmentEditViewModel
        compose.runOnIdle { model = RouteSegmentEditViewModel(record, data.recordRepository) }
        compose.setContent {
            val state by model.uiState.collectAsState()
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MapMateTheme {
                    Box(Modifier.width(360.dp).fillMaxHeight()) {
                        RouteSegmentEditScreen(state, PaddingValues(), onBack,
                            model::onStartDateTimeSelected, model::onEndDateTimeSelected, model::onSaveClick)
                    }
                }
            }
        }
        return model
    }

    private fun overnightRecord() = CommuteRecord(id = 1, routineId = 1, routineName = "심야 귀가",
        originName = "서울역버스환승센터", destinationName = "숭실대학교 정문",
        transportMode = TransportMode.TRANSIT, targetArrivalTime = LocalTime.of(0, 30),
        targetArrivalAtEpochMillis = epoch(2, 0, 30), recommendedDepartureTime = LocalTime.of(23, 58),
        routeDurationMinutes = 20, routeSummary = "753번 버스", startedAtEpochMillis = epoch(1, 23, 58),
        arrivedAtEpochMillis = epoch(2, 0, 7), arrivalDeltaMinutes = -23,
        routeSegments = listOf(RouteSegment(id = 101, commuteRecordId = 1, routineId = 1, segmentIndex = 0,
            segmentType = RouteSegmentType.BUS_RIDE, routeName = "753", startName = "서울역버스환승센터",
            endName = "숭실대입구역", plannedDurationMinutes = 20, actualStartedAtEpochMillis = epoch(1, 23, 58),
            actualEndedAtEpochMillis = epoch(2, 0, 7), actualDurationMinutes = 9,
            status = RouteSegmentStatus.COMPLETED)))

    private fun epoch(day: Int, hour: Int, minute: Int) =
        ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun screenshot(name: String) {
        compose.waitForIdle()
        // System dialog and IME animations are outside Compose's test clock.
        android.os.SystemClock.sleep(600)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val directory = File(context.getExternalFilesDir(null), "quality-screenshots").apply { mkdirs() }
        val roots = compose.onAllNodes(isRoot())
        val bitmap = if (roots.fetchSemanticsNodes().size == 1) roots[0].captureToImage().asAndroidBitmap()
            else checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
