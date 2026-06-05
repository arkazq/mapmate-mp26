package com.mapmate.di

import android.content.Context
import com.mapmate.BuildConfig
import com.mapmate.data.location.AndroidCurrentLocationProvider
import com.mapmate.data.local.MapMateDatabase
import com.mapmate.data.preferences.DataStoreSettingsRepository
import com.mapmate.data.remote.api.GoogleRoutesApi
import com.mapmate.data.remote.api.KakaoLocalApi
import com.mapmate.data.remote.api.MapMateRetrofitFactory
import com.mapmate.data.remote.api.OdsayApi
import com.mapmate.data.remote.config.RemoteApiConfig
import com.mapmate.data.remote.provider.FallbackPlaceSearchProvider
import com.mapmate.data.remote.provider.FallbackRouteEstimateProvider
import com.mapmate.data.remote.provider.GoogleRoutesEstimateProvider
import com.mapmate.data.remote.provider.KakaoPlaceSearchProvider
import com.mapmate.data.remote.provider.OdsayRouteEstimateProvider
import com.mapmate.data.mock.MockPlaceSearchProvider
import com.mapmate.data.mock.MockRouteEstimateProvider
import com.mapmate.data.repository.RoomRoutineRepository
import com.mapmate.domain.provider.CurrentLocationProvider
import com.mapmate.domain.provider.PlaceSearchProvider
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.domain.repository.RoutineRepository
import com.mapmate.domain.repository.SettingsRepository

class AppContainer(
    context: Context,
) {
    private val applicationContext = context.applicationContext

    val routineRepository: RoutineRepository by lazy {
        RoomRoutineRepository(MapMateDatabase.getInstance(applicationContext).routineDao())
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

    private val remoteApiConfig: RemoteApiConfig by lazy {
        RemoteApiConfig(
            kakaoRestApiKey = BuildConfig.KAKAO_REST_API_KEY,
            odsayApiKey = BuildConfig.ODSAY_API_KEY,
            googleRoutesApiKey = BuildConfig.GOOGLE_ROUTES_API_KEY,
        )
    }

    private companion object {
        const val KAKAO_BASE_URL = "https://dapi.kakao.com/"
        const val ODSAY_BASE_URL = "https://api.odsay.com/"
        const val GOOGLE_ROUTES_BASE_URL = "https://routes.googleapis.com/"
    }
}
