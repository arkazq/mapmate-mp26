package com.mapmate.data.mock

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockPlaceSearchProviderTest {
    @Test fun unmatchedQueryDoesNotShowUnrelatedPlaces() = runTest {
        assertTrue(MockPlaceSearchProvider().search("not-a-known-place-123").isEmpty())
    }
    @Test fun matchingAndBlankQueriesKeepKnownCoordinateFallback() = runTest {
        val provider = MockPlaceSearchProvider()
        assertEquals(listOf("서울역"), provider.search("서울역").map { it.name })
        assertEquals(4, provider.search("").size)
    }
}
