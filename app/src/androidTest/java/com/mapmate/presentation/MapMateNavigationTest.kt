package com.mapmate.presentation

import android.graphics.Bitmap
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mapmate.ui.theme.MapMateTheme
import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.RouteSegment
import com.mapmate.domain.model.RouteSegmentType
import com.mapmate.data.alarm.AndroidAlarmAccessProvider
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapMateNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun segmentEditorSaveReturnsHomeWithoutReopeningCompletedTracking() {
        val estimate = RouteEstimate(20, "테스트 경로", "Test", "테스트", segments = listOf(
            RouteSegment(segmentIndex = 0, segmentType = RouteSegmentType.BUS_RIDE,
                routeName = "753", plannedDurationMinutes = 20),
        ))
        val data = NavigationTestData(listOf(testRoutine()), estimate)
        showApp(data)
        compose.onNodeWithText("이동 시작").performClick()
        compose.onNodeWithText("탑승").performScrollTo().performClick()
        compose.onNodeWithText("하차").performScrollTo().performClick()
        compose.onNodeWithText("도착 완료").performScrollTo().performClick()
        compose.onNodeWithText("구간별 시간 수정").performScrollTo().performClick()
        compose.onNodeWithText("수정 저장").performScrollTo().performClick()
        compose.onNodeWithText("오늘 이동 완료").assertIsDisplayed()
        compose.onNodeWithText("이동 시작").assertDoesNotExist()
        compose.runOnIdle { org.junit.Assert.assertEquals(1, data.records.value.size) }
    }

    @Test
    fun measurementCanResumeFromHomeAndCompletedEventDoesNotOfferAnotherStart() {
        val data = NavigationTestData(listOf(testRoutine()))
        showApp(data)
        compose.onNodeWithText("이동 시작").performClick()
        compose.onNodeWithText("권장 출발 시각은 ", true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("이후 출발", true).assertDoesNotExist()
        screenshot("tracking-planned-final")
        compose.onNodeWithText("버스에 탑승했어요").performScrollTo().performClick()
        pressBack()
        compose.onNodeWithText("이동 시작").assertDoesNotExist()
        compose.onNodeWithText("측정 이어하기").assertIsDisplayed().performClick()
        compose.onNodeWithText("이전 측정을 이어서 기록합니다").assertIsDisplayed()
        compose.onNodeWithText("목적지에 도착했어요").performScrollTo().performClick()
        compose.onNodeWithText("오늘 기록이 저장되었어요!").assertIsDisplayed()
        compose.onNodeWithText("저장된 내역은 기록 화면에서 확인할 수 있어요.").assertIsDisplayed()
        screenshot("tracking-completion-final")
        compose.onNodeWithText("홈으로 돌아가기").performScrollTo().performClick()
        compose.onNodeWithText("오늘 이동 완료").assertIsDisplayed()
        compose.onNodeWithText("이동 시작").assertDoesNotExist()
        compose.onNodeWithText("측정 이어하기").assertDoesNotExist()
        compose.runOnIdle { org.junit.Assert.assertEquals(1, data.records.value.size) }
    }

    @Test
    fun routineDeletionRequiresConfirmationAndCancelKeepsTheRoutine() {
        val data = NavigationTestData(listOf(testRoutine()))
        showApp(data)
        compose.onNode(hasText("루틴") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .performClick()
        compose.onNodeWithContentDescription("등교 삭제").performClick()
        compose.onNodeWithText("등교 루틴을 삭제할까요?").assertIsDisplayed()
        compose.onNodeWithText("취소").performClick()
        compose.onNodeWithContentDescription("등교 수정").assertIsDisplayed()
        compose.onNodeWithContentDescription("등교 삭제").performClick()
        compose.onNodeWithText("삭제").performClick()
        compose.onNodeWithText("저장된 루틴이 없습니다").assertIsDisplayed()
    }

    @Test
    fun unsavedRegistrationRequiresDiscardConfirmationAndCanKeepEditing() {
        showApp(NavigationTestData())
        compose.onNodeWithText("루틴 등록", true).performClick()
        compose.onNodeWithText("다음").assertIsNotEnabled()
        compose.onNodeWithText("루틴 이름", true).performTextReplacement("작성 중인 루틴")
        compose.onNodeWithContentDescription("뒤로").performClick()
        compose.onNodeWithText("수정 내용을 버릴까요?").assertIsDisplayed()
        compose.onNodeWithText("계속 수정").performClick()
        compose.onNodeWithText("루틴 이름", true).assertTextContains("작성 중인 루틴")
        compose.onNodeWithContentDescription("뒤로").performClick()
        compose.onNodeWithText("버리기").performClick()
        compose.onNodeWithText("MapMate", true).assertIsDisplayed()
    }

    @Test
    fun registrationSystemBackReturnsHomeInsteadOfExitingApp() {
        val data = NavigationTestData()
        showApp(data)
        compose.onNodeWithText("루틴 등록", true).performClick()
        compose.onNodeWithText("루틴 이름", true).assertIsDisplayed()
        pressBack()
        compose.onNodeWithText("MapMate", true).assertIsDisplayed()
        compose.onNodeWithText("홈", true).assertIsSelected()
        screenshot("navigation-home")
    }

    @Test
    fun editingTwoDifferentRoutinesDoesNotReuseFirstRoutineState() {
        val data = NavigationTestData(listOf(testRoutine(name = "등교"), testRoutine(2, "출근")))
        showApp(data)
        compose.onNodeWithContentDescription("등교 수정").performClick()
        compose.onNodeWithText("루틴 이름", true).assertTextContains("등교")
        pressBack()
        compose.onNodeWithContentDescription("출근 수정").performClick()
        compose.onNodeWithText("루틴 이름", true).assertTextContains("출근")
    }

    @Test
    fun registrationDestinationAndDraftSurviveSavedStateRestoration() {
        val data = NavigationTestData()
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            MapMateTheme {
                MapMateApp(PaddingValues(), data.routineRepository, data.recordRepository,
                    data.settingsRepository, data.placeSearchProvider, data.routeProvider, data.locationProvider, data.trackingSessionStore,
                    AndroidAlarmAccessProvider(InstrumentationRegistry.getInstrumentation().targetContext))
            }
        }
        compose.onNodeWithText("루틴 등록", true).performClick()
        compose.onNodeWithText("루틴 이름", true).performTextReplacement("보존할 루틴")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("루틴 이름", true).assertTextContains("보존할 루틴")
        screenshot("registration-restored")
    }

    @Test
    fun notificationShortcutOpensSettingsAndMissingRoutineDoesNotLoadForever() {
        val data = NavigationTestData(listOf(testRoutine()))
        showApp(data)
        compose.onNodeWithContentDescription("알림 설정").performClick()
        compose.onNode(hasText("설정") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .assertIsSelected()
        compose.onNodeWithText("홈", true).performClick()
        compose.onNodeWithText("상세 경로", true).performClick()
        compose.runOnIdle { data.routines.value = emptyList() }
        compose.onNodeWithText("루틴이 삭제되었거나 더 이상 사용할 수 없어요.", true).assertIsDisplayed()
        compose.onNodeWithText("돌아가기", true).performClick()
        compose.onNodeWithText("MapMate", true).assertIsDisplayed()
    }

    private fun showApp(data: NavigationTestData) {
        compose.setContent {
            MapMateTheme {
                MapMateApp(PaddingValues(), data.routineRepository, data.recordRepository,
                    data.settingsRepository, data.placeSearchProvider, data.routeProvider, data.locationProvider, data.trackingSessionStore,
                    AndroidAlarmAccessProvider(InstrumentationRegistry.getInstrumentation().targetContext))
            }
        }
    }

    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "quality-screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { stream ->
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
    }
}
