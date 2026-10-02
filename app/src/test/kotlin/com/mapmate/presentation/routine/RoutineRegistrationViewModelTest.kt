package com.mapmate.presentation.routine

import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.provider.PlaceSearchProvider
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.data.mock.MockPlaceSearchProvider
import com.mapmate.data.mock.MockRouteEstimateProvider
import com.mapmate.testing.MainDispatcherRule
import com.mapmate.testing.TestRoutineRepository
import com.mapmate.testing.TestSettingsRepository
import com.mapmate.testing.sampleRoutine
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoutineRegistrationViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun newViewModelRestoresDraftWithoutReapplyingOriginalRoutineOrDefaults() = runTest {
        val handle = SavedStateHandle()
        val settings = TestSettingsRepository()
        val first = RoutineRegistrationViewModel(TestRoutineRepository(), settings,
            MockPlaceSearchProvider(), MockRouteEstimateProvider(), savedStateHandle = handle)
        first.initialize(sampleRoutine())
        runCurrent()
        first.onEvent(RoutineRegistrationEvent.RoutineNameChanged("Unsaved rename"))
        first.onEvent(RoutineRegistrationEvent.PersonalBufferChanged("11"))
        first.onEvent(RoutineRegistrationEvent.ArrivalTimeChanged("10:25"))
        val restored = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })

        val second = RoutineRegistrationViewModel(TestRoutineRepository(), settings,
            MockPlaceSearchProvider(), MockRouteEstimateProvider(), savedStateHandle = restored)
        second.initialize(sampleRoutine())
        runCurrent()

        assertEquals("Unsaved rename", second.uiState.value.routineName)
        assertEquals("11", second.uiState.value.personalBufferMinutes)
        assertEquals("10:25", second.uiState.value.targetArrivalTimeText)
        assertEquals(sampleRoutine().origin, second.uiState.value.selectedOrigin)
        assertEquals(true, second.uiState.value.hasUnsavedChanges)
        assertEquals(true, second.uiState.value.isSaveEnabled)
    }

    @Test
    fun unselectedSearchTextCannotBeSavedAsCoordinateLessPlace() = runTest {
        val repository = TestRoutineRepository()
        val model = RoutineRegistrationViewModel(repository, TestSettingsRepository(), MockPlaceSearchProvider(), MockRouteEstimateProvider())
        model.initialize(sampleRoutine().copy(id = null))
        runCurrent()
        model.onEvent(RoutineRegistrationEvent.DestinationQueryChanged("Unselected place"))
        model.onEvent(RoutineRegistrationEvent.SaveClicked)
        runCurrent()
        assertFalse(model.uiState.value.isSaveEnabled)
        assertEquals(0, repository.saveCount)
        assertEquals("검색 결과에서 목적지를 선택해 주세요.", model.uiState.value.errorMessage)
    }

    @Test
    fun editingFormDefaultsDoesNotPersistGlobalSettingsBeforeSave() = runTest {
        val settings = TestSettingsRepository()
        val model = RoutineRegistrationViewModel(TestRoutineRepository(), settings, MockPlaceSearchProvider(), MockRouteEstimateProvider())
        runCurrent()
        val initial = settings.settings.value
        model.onEvent(RoutineRegistrationEvent.PersonalBufferChanged("11"))
        model.onEvent(RoutineRegistrationEvent.SafetyMarginChanged("9"))
        model.onEvent(RoutineRegistrationEvent.TransportModeSelected(TransportMode.CAR))
        runCurrent()
        assertEquals(initial, settings.settings.value)
    }

    @Test
    fun invalidSelectedCoordinatesCannotBeSavedOrCrashDraftPersistence() = runTest {
        val repository = TestRoutineRepository()
        val model = RoutineRegistrationViewModel(repository, TestSettingsRepository(), MockPlaceSearchProvider(), MockRouteEstimateProvider())
        model.initialize(sampleRoutine().copy(id = null))
        runCurrent()
        for (latitude in listOf(null, Double.NaN, 90.1)) {
            model.onEvent(RoutineRegistrationEvent.OriginSelected(Destination("Invalid", "Address", latitude, 127.0)))
            model.onEvent(RoutineRegistrationEvent.SaveClicked)
            runCurrent()
            assertFalse(model.uiState.value.isSaveEnabled)
            assertEquals(0, repository.saveCount)
        }
    }

    @Test
    fun failedSearchEndsLoadingAndShowsErrorInsteadOfEmptyResultOnly() = runTest {
        val search = object : PlaceSearchProvider {
            override suspend fun search(query: String): List<Destination> = error("offline")
        }
        val model = RoutineRegistrationViewModel(TestRoutineRepository(), TestSettingsRepository(), search, MockRouteEstimateProvider())
        runCurrent()
        model.onEvent(RoutineRegistrationEvent.DestinationQueryChanged("Office"))
        assertEquals(true, model.uiState.value.isSearchingDestination)
        advanceTimeBy(350)
        runCurrent()
        assertFalse(model.uiState.value.isSearchingDestination)
        assertEquals("장소 검색에 실패했습니다. 다시 시도해 주세요.", model.uiState.value.destinationSearchError)
    }

    @Test
    fun rapidTypingDebouncesAndQueriesOnlyLatestText() = runTest {
        val requests = mutableListOf<String>()
        val search = object : PlaceSearchProvider {
            override suspend fun search(query: String): List<Destination> {
                requests += query
                return listOf(Destination(query, "Address", 37.5, 127.0))
            }
        }
        val viewModel = RoutineRegistrationViewModel(TestRoutineRepository(), TestSettingsRepository(), search, MockRouteEstimateProvider())
        runCurrent()
        requests.clear()
        viewModel.onEvent(RoutineRegistrationEvent.OriginQueryChanged("S"))
        advanceTimeBy(100)
        viewModel.onEvent(RoutineRegistrationEvent.OriginQueryChanged("Se"))
        advanceTimeBy(100)
        viewModel.onEvent(RoutineRegistrationEvent.OriginQueryChanged("Seoul"))
        advanceTimeBy(350)
        runCurrent()
        assertEquals(listOf("Seoul"), requests)
        assertEquals("Seoul", viewModel.uiState.value.originCandidates.single().name)
    }

    @Test
    fun typingAgainCancelsOldRequestAndDoesNotReplaceNewResults() = runTest {
        var cancelledOldRequest = false
        val search = object : PlaceSearchProvider {
            override suspend fun search(query: String): List<Destination> {
                if (query == "old") {
                    try { awaitCancellation() } finally { cancelledOldRequest = true }
                }
                return listOf(Destination(query, "Address", 37.5, 127.0))
            }
        }
        val viewModel = RoutineRegistrationViewModel(TestRoutineRepository(), TestSettingsRepository(), search, MockRouteEstimateProvider())
        runCurrent()
        viewModel.onEvent(RoutineRegistrationEvent.DestinationQueryChanged("old"))
        advanceTimeBy(350)
        runCurrent()
        viewModel.onEvent(RoutineRegistrationEvent.DestinationQueryChanged("new"))
        advanceTimeBy(350)
        runCurrent()
        assertEquals(true, cancelledOldRequest)
        assertEquals("new", viewModel.uiState.value.destinationCandidates.single().name)
    }

    @Test
    fun initializationOnRecreationDoesNotDiscardUnsavedEdits() = runTest {
        val viewModel = RoutineRegistrationViewModel(TestRoutineRepository(), TestSettingsRepository(), MockPlaceSearchProvider(), MockRouteEstimateProvider())
        viewModel.initialize(sampleRoutine())
        viewModel.onEvent(RoutineRegistrationEvent.RoutineNameChanged("Edited"))
        viewModel.initialize(sampleRoutine())
        runCurrent()
        assertEquals("Edited", viewModel.uiState.value.routineName)
    }

    @Test
    fun doubleTapSaveCreatesOneRecordEvenBeforeCoroutineStarts() = runTest {
        val repository = TestRoutineRepository()
        val gate = CompletableDeferred<Unit>()
        repository.saveGate = gate
        val viewModel = RoutineRegistrationViewModel(repository, TestSettingsRepository(), MockPlaceSearchProvider(), MockRouteEstimateProvider())
        viewModel.initialize(sampleRoutine().copy(id = null))
        runCurrent()
        viewModel.onEvent(RoutineRegistrationEvent.SaveClicked)
        viewModel.onEvent(RoutineRegistrationEvent.SaveClicked)
        runCurrent()
        assertEquals(1, repository.saveCount)
        gate.complete(Unit)
        runCurrent()
        assertEquals(1, repository.routines.value.size)
    }

    @Test
    fun settingsEmissionDoesNotDiscardOtherUnsavedInput() = runTest {
        val settings = TestSettingsRepository()
        val viewModel = RoutineRegistrationViewModel(TestRoutineRepository(), settings, MockPlaceSearchProvider(), MockRouteEstimateProvider())
        runCurrent()
        viewModel.onEvent(RoutineRegistrationEvent.PersonalBufferChanged("9"))
        viewModel.onEvent(RoutineRegistrationEvent.SafetyMarginChanged("7"))
        runCurrent()
        assertEquals("9", viewModel.uiState.value.personalBufferMinutes)
        assertEquals("7", viewModel.uiState.value.safetyMarginMinutes)
    }

    @Test
    fun changingDestinationCancelsCalculationSoOldRouteIsNotDisplayed() = runTest {
        var cancelledCalculation = false
        val provider = object : RouteEstimateProvider {
            override suspend fun getRouteEstimate(
                origin: Destination, destination: Destination, transportMode: TransportMode,
                routineId: Long?, scheduledDepartureEpochMillis: Long?, targetArrivalEpochMillis: Long?,
            ): RouteEstimate {
                try { awaitCancellation() } finally { cancelledCalculation = true }
            }
        }
        val viewModel = RoutineRegistrationViewModel(
            TestRoutineRepository(), TestSettingsRepository(), placeSearchProvider = MockPlaceSearchProvider(), routeEstimateProvider = provider,
        )
        viewModel.initialize(sampleRoutine())
        runCurrent()
        viewModel.onEvent(RoutineRegistrationEvent.CalculateClicked)
        runCurrent()
        viewModel.onEvent(RoutineRegistrationEvent.DestinationQueryChanged("New destination"))
        runCurrent()
        assertEquals(true, cancelledCalculation)
        assertFalse(viewModel.uiState.value.isCalculating)
        assertNull(viewModel.uiState.value.routeEstimate)
    }
}
