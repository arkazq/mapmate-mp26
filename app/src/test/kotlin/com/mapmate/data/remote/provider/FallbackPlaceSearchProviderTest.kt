package com.mapmate.data.remote.provider

import com.mapmate.domain.model.Destination
import com.mapmate.domain.provider.PlaceSearchProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FallbackPlaceSearchProviderTest {
    @Test
    fun search_returnsFallbackWhenPrimaryFails() = runTest {
        val fallbackDestination = Destination(
            name = "숭실대학교",
            address = "서울특별시 동작구 상도로 369",
            latitude = 37.4963,
            longitude = 126.9574,
        )
        val provider = FallbackPlaceSearchProvider(
            primary = FailingPlaceSearchProvider,
            fallback = FixedPlaceSearchProvider(listOf(fallbackDestination)),
        )

        val result = provider.search("숭실")

        assertEquals(listOf(fallbackDestination), result)
    }

    @Test
    fun search_returnsFallbackWhenPrimaryIsEmpty() = runTest {
        val fallbackDestination = Destination(
            name = "강남역",
            address = "서울특별시 강남구 강남대로 지하396",
            latitude = 37.4979,
            longitude = 127.0276,
        )
        val provider = FallbackPlaceSearchProvider(
            primary = FixedPlaceSearchProvider(emptyList()),
            fallback = FixedPlaceSearchProvider(listOf(fallbackDestination)),
        )

        val result = provider.search("강남")

        assertEquals(listOf(fallbackDestination), result)
    }

    private object FailingPlaceSearchProvider : PlaceSearchProvider {
        override suspend fun search(query: String): List<Destination> {
            error("Remote failed.")
        }
    }

    private class FixedPlaceSearchProvider(
        private val destinations: List<Destination>,
    ) : PlaceSearchProvider {
        override suspend fun search(query: String): List<Destination> {
            return destinations
        }
    }
}
