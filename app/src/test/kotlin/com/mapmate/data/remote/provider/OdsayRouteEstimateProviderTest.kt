package com.mapmate.data.remote.provider

import com.mapmate.data.remote.api.OdsayApi
import com.mapmate.data.remote.config.RemoteApiConfig
import com.mapmate.data.remote.dto.OdsayLane
import com.mapmate.data.remote.dto.OdsayPath
import com.mapmate.data.remote.dto.OdsayPathInfo
import com.mapmate.data.remote.dto.OdsayRouteResponse
import com.mapmate.data.remote.dto.OdsayRouteResult
import com.mapmate.data.remote.dto.OdsaySubPath
import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RouteRealtimeSnapshot
import com.mapmate.domain.model.TransitArrivalEstimate
import com.mapmate.domain.model.TransitArrivalQuery
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.provider.TransitArrivalProvider
import com.mapmate.domain.repository.RouteRealtimeSnapshotRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OdsayRouteEstimateProviderTest {
    @Test
    fun getRouteEstimate_addsRealtimeDelayForFirstBusLeg() = runTest {
        val transitArrivalProvider = RecordingTransitArrivalProvider(
            result = TransitArrivalEstimate(
                waitMinutes = 12,
                summary = "first bus in 12 min",
                providerName = "Realtime",
                reason = "test",
            ),
        )
        val provider = OdsayRouteEstimateProvider(
            api = FakeOdsayApi(busRouteResponse),
            config = remoteApiConfig,
            transitArrivalProvider = transitArrivalProvider,
            nowEpochMillis = { 0L },
        )

        val result = provider.getRouteEstimate(
            origin = origin,
            destination = destination,
            transportMode = TransportMode.TRANSIT,
            scheduledDepartureEpochMillis = 20 * 60 * 1000L,
        )

        assertEquals(49, result.estimatedMinutes)
        assertEquals("ODsay + Realtime", result.providerName)
        assertTrue(result.hasRealtimeAdjustment)
        assertTrue(result.reason.contains("Realtime first bus arrival 12 min"))

        val query = transitArrivalProvider.lastQuery as TransitArrivalQuery.Bus
        assertEquals("222", query.stationId)
        assertEquals("333", query.stationArsId)
        assertEquals("111", query.busRouteId)
        assertEquals("740", query.routeName)
    }

    @Test
    fun getRouteEstimate_skipsRealtimeArrivalWithoutScheduledDeparture() = runTest {
        val transitArrivalProvider = RecordingTransitArrivalProvider(result = realtimeArrivalEstimate)
        val provider = OdsayRouteEstimateProvider(
            api = FakeOdsayApi(busRouteResponse),
            config = remoteApiConfig,
            transitArrivalProvider = transitArrivalProvider,
        )

        val result = provider.getRouteEstimate(
            origin = origin,
            destination = destination,
            transportMode = TransportMode.TRANSIT,
        )

        assertEquals(42, result.estimatedMinutes)
        assertEquals("ODsay", result.providerName)
        assertEquals(false, result.hasRealtimeAdjustment)
        assertNull(transitArrivalProvider.lastQuery)
        assertTrue(result.reason.contains("applyRealtime=false"))
    }

    @Test
    fun getRouteEstimate_skipsRealtimeArrivalMoreThanThirtyMinutesBeforeDeparture() = runTest {
        val transitArrivalProvider = RecordingTransitArrivalProvider(result = realtimeArrivalEstimate)
        val provider = OdsayRouteEstimateProvider(
            api = FakeOdsayApi(busRouteResponse),
            config = remoteApiConfig,
            transitArrivalProvider = transitArrivalProvider,
            nowEpochMillis = { 0L },
        )

        val result = provider.getRouteEstimate(
            origin = origin,
            destination = destination,
            transportMode = TransportMode.TRANSIT,
            scheduledDepartureEpochMillis = 31 * 60 * 1000L,
        )

        assertEquals(42, result.estimatedMinutes)
        assertEquals("ODsay", result.providerName)
        assertEquals(false, result.hasRealtimeAdjustment)
        assertNull(transitArrivalProvider.lastQuery)
        assertTrue(result.reason.contains("SKIPPED_TOO_EARLY"))
    }

    @Test
    fun getRouteEstimate_savesRealtimeSnapshotWhenRealtimeArrivalSucceeds() = runTest {
        val snapshotRepository = FakeRouteRealtimeSnapshotRepository()
        val provider = OdsayRouteEstimateProvider(
            api = FakeOdsayApi(busRouteResponse),
            config = remoteApiConfig,
            transitArrivalProvider = RecordingTransitArrivalProvider(
                result = realtimeArrivalEstimate,
            ),
            routeRealtimeSnapshotRepository = snapshotRepository,
            nowEpochMillis = { 1_000L },
            snapshotTtlMillis = 10_000L,
        )

        provider.getRouteEstimate(
            origin = origin,
            destination = destination,
            transportMode = TransportMode.TRANSIT,
            scheduledDepartureEpochMillis = 1_000L + 20 * 60 * 1000L,
        )

        val snapshot = snapshotRepository.savedSnapshots.single()
        assertEquals(42, snapshot.baseRouteDurationMinutes)
        assertEquals(49, snapshot.adjustedRouteDurationMinutes)
        assertEquals(7, snapshot.realtimeDelayMinutes)
        assertEquals("Realtime", snapshot.providerName)
        assertEquals(1_000L, snapshot.capturedAtEpochMillis)
        assertEquals(11_000L, snapshot.expiresAtEpochMillis)
    }

    @Test
    fun getRouteEstimate_reusesFreshRealtimeSnapshotWhenRealtimeArrivalIsMissing() = runTest {
        val snapshotRepository = FakeRouteRealtimeSnapshotRepository()
        val transitArrivalProvider = MutableTransitArrivalProvider(realtimeArrivalEstimate)
        var now = 1_000L
        val provider = OdsayRouteEstimateProvider(
            api = FakeOdsayApi(busRouteResponse),
            config = remoteApiConfig,
            transitArrivalProvider = transitArrivalProvider,
            routeRealtimeSnapshotRepository = snapshotRepository,
            nowEpochMillis = { now },
            snapshotTtlMillis = 10_000L,
        )

        provider.getRouteEstimate(
            origin = origin,
            destination = destination,
            transportMode = TransportMode.TRANSIT,
            scheduledDepartureEpochMillis = 1_000L + 20 * 60 * 1000L,
        )
        transitArrivalProvider.result = null
        now = 5_000L

        val result = provider.getRouteEstimate(
            origin = origin,
            destination = destination,
            transportMode = TransportMode.TRANSIT,
            scheduledDepartureEpochMillis = 1_000L + 20 * 60 * 1000L,
        )

        assertEquals(49, result.estimatedMinutes)
        assertEquals("ODsay + Realtime snapshot", result.providerName)
        assertTrue(result.hasRealtimeAdjustment)
        assertTrue(result.reason.contains("Cached realtime snapshot added 7 min delay."))
        assertTrue(result.statusMessage.orEmpty().contains("최근 성공 보정값"))
    }

    @Test
    fun getRouteEstimate_ignoresExpiredRealtimeSnapshotWhenRealtimeArrivalIsMissing() = runTest {
        val snapshotRepository = FakeRouteRealtimeSnapshotRepository()
        val transitArrivalProvider = MutableTransitArrivalProvider(realtimeArrivalEstimate)
        var now = 1_000L
        val provider = OdsayRouteEstimateProvider(
            api = FakeOdsayApi(busRouteResponse),
            config = remoteApiConfig,
            transitArrivalProvider = transitArrivalProvider,
            routeRealtimeSnapshotRepository = snapshotRepository,
            nowEpochMillis = { now },
            snapshotTtlMillis = 1_000L,
        )

        provider.getRouteEstimate(
            origin = origin,
            destination = destination,
            transportMode = TransportMode.TRANSIT,
            scheduledDepartureEpochMillis = 1_000L + 20 * 60 * 1000L,
        )
        transitArrivalProvider.result = null
        now = 3_000L

        val result = provider.getRouteEstimate(
            origin = origin,
            destination = destination,
            transportMode = TransportMode.TRANSIT,
            scheduledDepartureEpochMillis = 1_000L + 20 * 60 * 1000L,
        )

        assertEquals(42, result.estimatedMinutes)
        assertEquals("ODsay", result.providerName)
        assertEquals(null, result.statusMessage)
    }

    @Test
    fun getRouteEstimate_selectsFasterRealtimeAdjustedCandidate() = runTest {
        val provider = OdsayRouteEstimateProvider(
            api = FakeOdsayApi(twoBusRouteResponse(firstTotalTime = 40, secondTotalTime = 43)),
            config = remoteApiConfig,
            transitArrivalProvider = RouteNameTransitArrivalProvider(
                waitMinutesByRouteName = mapOf(
                    "740" to 15,
                    "741" to 5,
                ),
            ),
            nowEpochMillis = { 0L },
        )

        val result = provider.getRouteEstimate(
            origin = origin,
            destination = destination,
            transportMode = TransportMode.TRANSIT,
            scheduledDepartureEpochMillis = 20 * 60 * 1000L,
        )

        assertEquals(43, result.estimatedMinutes)
        assertTrue(result.reason.contains("Selected ODsay candidate 2/2"))
    }

    @Test
    fun getRouteEstimate_keepsFirstCandidateWhenGainIsBelowSwitchThreshold() = runTest {
        val provider = OdsayRouteEstimateProvider(
            api = FakeOdsayApi(twoBusRouteResponse(firstTotalTime = 40, secondTotalTime = 38)),
            config = remoteApiConfig,
            nowEpochMillis = { 0L },
        )

        val result = provider.getRouteEstimate(
            origin = origin,
            destination = destination,
            transportMode = TransportMode.TRANSIT,
        )

        assertEquals(40, result.estimatedMinutes)
        assertTrue(result.reason.contains("Selected ODsay candidate 1/2"))
    }

    private class FakeOdsayApi(
        private val response: OdsayRouteResponse,
    ) : OdsayApi {
        override suspend fun searchPublicTransitPath(
            startLongitude: Double,
            startLatitude: Double,
            endLongitude: Double,
            endLatitude: Double,
            apiKey: String,
        ): OdsayRouteResponse {
            return response
        }
    }

    private class RecordingTransitArrivalProvider(
        private val result: TransitArrivalEstimate?,
    ) : TransitArrivalProvider {
        var lastQuery: TransitArrivalQuery? = null
            private set

        override suspend fun getArrivalEstimate(query: TransitArrivalQuery): TransitArrivalEstimate? {
            lastQuery = query
            return result
        }
    }

    private class MutableTransitArrivalProvider(
        var result: TransitArrivalEstimate?,
    ) : TransitArrivalProvider {
        override suspend fun getArrivalEstimate(query: TransitArrivalQuery): TransitArrivalEstimate? {
            return result
        }
    }

    private class RouteNameTransitArrivalProvider(
        private val waitMinutesByRouteName: Map<String, Int>,
    ) : TransitArrivalProvider {
        override suspend fun getArrivalEstimate(query: TransitArrivalQuery): TransitArrivalEstimate? {
            val routeName = (query as? TransitArrivalQuery.Bus)?.routeName ?: return null
            val waitMinutes = waitMinutesByRouteName[routeName] ?: return null
            return TransitArrivalEstimate(
                waitMinutes = waitMinutes,
                summary = "$routeName bus in $waitMinutes min",
                providerName = "Realtime",
                reason = "test",
            )
        }
    }

    private class FakeRouteRealtimeSnapshotRepository : RouteRealtimeSnapshotRepository {
        val savedSnapshots = mutableListOf<RouteRealtimeSnapshot>()
        private val snapshotsByKey = mutableMapOf<String, RouteRealtimeSnapshot>()

        override suspend fun saveSnapshot(snapshot: RouteRealtimeSnapshot) {
            savedSnapshots += snapshot
            snapshotsByKey[snapshot.cacheKey] = snapshot
        }

        override suspend fun findFreshSnapshot(
            cacheKey: String,
            nowEpochMillis: Long,
        ): RouteRealtimeSnapshot? {
            return snapshotsByKey[cacheKey]?.takeIf { it.isFresh(nowEpochMillis) }
        }

        override suspend fun deleteExpiredSnapshots(nowEpochMillis: Long) {
            snapshotsByKey.entries.removeAll { (_, snapshot) ->
                !snapshot.isFresh(nowEpochMillis)
            }
        }
    }

    private companion object {
        val realtimeArrivalEstimate = TransitArrivalEstimate(
            waitMinutes = 12,
            summary = "first bus in 12 min",
            providerName = "Realtime",
            reason = "test",
        )

        val remoteApiConfig = RemoteApiConfig(
            kakaoRestApiKey = "",
            odsayApiKey = "odsay-key",
            googleRoutesApiKey = "",
            seoulOpenApiKey = "",
            seoulBusServiceKey = "",
            tagoServiceKey = "",
        )

        val origin = Destination(
            name = "Origin",
            address = "Origin address",
            latitude = 37.4963,
            longitude = 126.9574,
        )

        val destination = Destination(
            name = "Destination",
            address = "Destination address",
            latitude = 37.4979,
            longitude = 127.0276,
        )

        val busRouteResponse = OdsayRouteResponse(
            result = OdsayRouteResult(
                path = listOf(
                    busPath(
                        totalTime = 42,
                        busNo = "740",
                        routeId = "111",
                        startId = "222",
                        startArsId = "333",
                    ),
                ),
            ),
        )

        fun twoBusRouteResponse(
            firstTotalTime: Int,
            secondTotalTime: Int,
        ): OdsayRouteResponse {
            return OdsayRouteResponse(
                result = OdsayRouteResult(
                    path = listOf(
                        busPath(
                            totalTime = firstTotalTime,
                            busNo = "740",
                            routeId = "111",
                            startId = "222",
                            startArsId = "333",
                        ),
                        busPath(
                            totalTime = secondTotalTime,
                            busNo = "741",
                            routeId = "112",
                            startId = "224",
                            startArsId = "335",
                        ),
                    ),
                ),
            )
        }

        fun busPath(
            totalTime: Int,
            busNo: String,
            routeId: String,
            startId: String,
            startArsId: String,
        ): OdsayPath {
            return OdsayPath(
                info = OdsayPathInfo(
                    totalTime = totalTime,
                    firstStartStation = "Start stop",
                    lastEndStation = "End station",
                    busTransitCount = 1,
                    subwayTransitCount = 0,
                ),
                subPath = listOf(
                    OdsaySubPath(
                        trafficType = 3,
                        sectionTime = 5,
                    ),
                    OdsaySubPath(
                        trafficType = 2,
                        startName = "Start stop",
                        startId = JsonPrimitive(startId),
                        startArsId = JsonPrimitive(startArsId),
                        lane = listOf(
                            OdsayLane(
                                busNo = busNo,
                                routeId = JsonPrimitive(routeId),
                            ),
                        ),
                    ),
                ),
            )
        }
    }
}
