package com.mapmate.di

import android.content.Context
import com.mapmate.BuildConfig
import com.mapmate.data.alarm.AndroidDepartureAlarmScheduler
import com.mapmate.data.alarm.AndroidDepartureRecheckScheduler
import com.mapmate.data.alarm.DepartureAlarmCoordinator
import com.mapmate.data.location.AndroidCurrentLocationProvider
import com.mapmate.data.local.MapMateDatabase
import com.mapmate.data.preferences.DataStoreSettingsRepository
import com.mapmate.data.remote.api.GoogleRoutesApi
import com.mapmate.data.remote.api.KakaoLocalApi
import com.mapmate.data.remote.api.MapMateRetrofitFactory
import com.mapmate.data.remote.api.OdsayApi
import com.mapmate.data.remote.api.SeoulBusArrivalApi
import com.mapmate.data.remote.api.SeoulSubwayRealtimeApi
import com.mapmate.data.remote.config.RemoteApiConfig
import com.mapmate.data.remote.provider.CompositeTransitArrivalProvider
import com.mapmate.data.remote.provider.FallbackPlaceSearchProvider
import com.mapmate.data.remote.provider.FallbackRouteEstimateProvider
import com.mapmate.data.remote.provider.GoogleRoutesEstimateProvider
import com.mapmate.data.remote.provider.KakaoPlaceSearchProvider
import com.mapmate.data.remote.provider.OdsayRouteEstimateProvider
import com.mapmate.data.remote.provider.SeoulBusRealtimeArrivalProvider
import com.mapmate.data.remote.provider.SeoulSubwayRealtimeArrivalProvider
import com.mapmate.data.mock.MockPlaceSearchProvider
import com.mapmate.data.mock.MockRouteEstimateProvider
import com.mapmate.data.repository.RoomCommuteRecordRepository
import com.mapmate.data.repository.RoomRoutineRepository
import com.mapmate.domain.provider.CurrentLocationProvider
import com.mapmate.domain.provider.PlaceSearchProvider
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.domain.repository.CommuteRecordRepository
import com.mapmate.domain.repository.RoutineRepository
import com.mapmate.domain.repository.SettingsRepository

class AppContainer(
    context: Context,
) {
    private val applicationContext = context.applicationContext

    private val database: MapMateDatabase by lazy {
        MapMateDatabase.getInstance(applicationContext)
    }

    val routineRepository: RoutineRepository by lazy {
        RoomRoutineRepository(database.routineDao())
    }

    val commuteRecordRepository: CommuteRecordRepository by lazy {
        RoomCommuteRecordRepository(database.commuteRecordDao())
    }

    val settingsRepository: SettingsRepository by lazy {
        DataStoreSettingsRepository(applicationContext)
    }

    val currentLocationProvider: CurrentLocationProvider by lazy {
        AndroidCurrentLocationProvider(applicationContext)
    }

    val placeSearchProvider: PlaceSearchProvider by lazy {
        FallbackPlaceSearchProvider(
            primary = KakaoPlaceSearchProvider(
                api = MapMateRetrofitFactory.create(
                    baseUrl = KAKAO_BASE_URL,
                    serviceClass = KakaoLocalApi::class.java,
                ),
                config = remoteApiConfig,
            ),
            fallback = MockPlaceSearchProvider(),
        )
    }

    val routeEstimateProvider: RouteEstimateProvider by lazy {
        FallbackRouteEstimateProvider(
            primaryProviders = listOf(
                OdsayRouteEstimateProvider(
                    api = MapMateRetrofitFactory.create(
                        baseUrl = ODSAY_BASE_URL,
                        serviceClass = OdsayApi::class.java,
                    ),
                    config = remoteApiConfig,
                    transitArrivalProvider = transitArrivalProvider,
                ),
                GoogleRoutesEstimateProvider(
                    api = MapMateRetrofitFactory.create(
                        baseUrl = GOOGLE_ROUTES_BASE_URL,
                        serviceClass = GoogleRoutesApi::class.java,
                    ),
                    config = remoteApiConfig,
                ),
            ),
            fallback = MockRouteEstimateProvider(),
        )
    }

    private val transitArrivalProvider by lazy {
        CompositeTransitArrivalProvider(
            providers = listOf(
                SeoulBusRealtimeArrivalProvider(
                    api = MapMateRetrofitFactory.create(
                        baseUrl = SEOUL_BUS_BASE_URL,
                        serviceClass = SeoulBusArrivalApi::class.java,
                    ),
                    config = remoteApiConfig,
                ),
                SeoulSubwayRealtimeArrivalProvider(
                    api = MapMateRetrofitFactory.create(
                        baseUrl = SEOUL_SUBWAY_BASE_URL,
                        serviceClass = SeoulSubwayRealtimeApi::class.java,
                    ),
                    config = remoteApiConfig,
                ),
            ),
        )
    }

    val departureAlarmCoordinator: DepartureAlarmCoordinator by lazy {
        DepartureAlarmCoordinator(
            settingsRepository = settingsRepository,
            routineRepository = routineRepository,
            routeEstimateProvider = routeEstimateProvider,
            alarmScheduler = AndroidDepartureAlarmScheduler(applicationContext),
            recheckScheduler = AndroidDepartureRecheckScheduler(applicationContext),
        )
    }

    private val remoteApiConfig: RemoteApiConfig by lazy {
        RemoteApiConfig(
            kakaoRestApiKey = BuildConfig.KAKAO_REST_API_KEY,
            odsayApiKey = BuildConfig.ODSAY_API_KEY,
            googleRoutesApiKey = BuildConfig.GOOGLE_ROUTES_API_KEY,
            seoulOpenApiKey = BuildConfig.SEOUL_OPEN_API_KEY,
            seoulBusServiceKey = BuildConfig.SEOUL_BUS_SERVICE_KEY,
        )
    }

    private companion object {
        const val KAKAO_BASE_URL = "https://dapi.kakao.com/"
        const val ODSAY_BASE_URL = "https://api.odsay.com/"
        const val GOOGLE_ROUTES_BASE_URL = "https://routes.googleapis.com/"
        const val SEOUL_SUBWAY_BASE_URL = "http://swopenapi.seoul.go.kr/"
        const val SEOUL_BUS_BASE_URL = "http://ws.bus.go.kr/"
    }
}
