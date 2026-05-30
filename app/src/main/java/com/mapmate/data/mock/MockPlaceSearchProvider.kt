package com.mapmate.data.mock

import com.mapmate.domain.model.Destination
import com.mapmate.domain.provider.PlaceSearchProvider

class MockPlaceSearchProvider : PlaceSearchProvider {
    private val candidates = listOf(
        Destination(
            name = "숭실대학교",
            address = "서울특별시 동작구 상도로 369",
            latitude = 37.4963,
            longitude = 126.9574,
        ),
        Destination(
            name = "강남역",
            address = "서울특별시 강남구 강남대로 지하396",
            latitude = 37.4979,
            longitude = 127.0276,
        ),
        Destination(
            name = "서울역",
            address = "서울특별시 중구 한강대로 405",
            latitude = 37.5547,
            longitude = 126.9706,
        ),
        Destination(
            name = "홍대입구역",
            address = "서울특별시 마포구 양화로 지하160",
            latitude = 37.5572,
            longitude = 126.9245,
        ),
    )

    override suspend fun search(query: String): List<Destination> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return candidates

        return candidates.filter { destination ->
            destination.name.contains(normalizedQuery, ignoreCase = true) ||
                destination.address.contains(normalizedQuery, ignoreCase = true)
        }.ifEmpty {
            candidates
        }
    }
}
