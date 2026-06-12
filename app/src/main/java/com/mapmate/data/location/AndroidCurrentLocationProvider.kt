package com.mapmate.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import com.mapmate.domain.model.Destination
import com.mapmate.domain.provider.CurrentLocationProvider
import com.mapmate.domain.provider.ReverseGeocodingProvider
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class AndroidCurrentLocationProvider(
    context: Context,
    private val reverseGeocodingProvider: ReverseGeocodingProvider? = null,
) : CurrentLocationProvider {
    private val applicationContext = context.applicationContext
    private val locationManager = applicationContext.getSystemService(LocationManager::class.java)

    override suspend fun getCurrentLocation(): Destination {
        check(hasLocationPermission()) {
            "Location permission is missing."
        }

        val location = getFreshLocationOrLastKnown()
        val resolvedAddress = runCatching {
            reverseGeocodingProvider?.getAddress(
                latitude = location.latitude,
                longitude = location.longitude,
            )
        }.getOrNull()?.trim()?.takeIf(String::isNotBlank)

        return Destination(
            name = "현재 위치",
            address = resolvedAddress ?: "휴대폰 위치 기반 출발지",
            latitude = location.latitude,
            longitude = location.longitude,
        )
    }

    private fun hasLocationPermission(): Boolean {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        return fineLocationGranted || coarseLocationGranted
    }

    @SuppressLint("MissingPermission")
    private suspend fun getFreshLocationOrLastKnown(): Location {
        return runCatching {
            requestCurrentLocation()
        }.getOrNull()
            ?: getBestLastKnownLocation()
            ?: error("Current location is unavailable.")
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestCurrentLocation(): Location {
        return suspendCancellableCoroutine { continuation ->
            val provider = bestEnabledProvider()
            if (provider == null) {
                continuation.resumeWithException(IllegalStateException("Location provider is disabled."))
                return@suspendCancellableCoroutine
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val cancellationSignal = CancellationSignal()
                locationManager.getCurrentLocation(
                    provider,
                    cancellationSignal,
                    applicationContext.mainExecutor,
                ) { location ->
                    if (location != null && continuation.isActive) {
                        continuation.resume(location)
                    } else if (continuation.isActive) {
                        continuation.resumeWithException(IllegalStateException("Location is null."))
                    }
                }
                continuation.invokeOnCancellation { cancellationSignal.cancel() }
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        locationManager.removeUpdates(this)
                        if (continuation.isActive) continuation.resume(location)
                    }

                    override fun onProviderDisabled(provider: String) = Unit
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                }

                locationManager.requestSingleUpdate(provider, listener, null)
                continuation.invokeOnCancellation {
                    locationManager.removeUpdates(listener)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun getBestLastKnownLocation(): Location? {
        return listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
        ).mapNotNull { provider ->
            runCatching {
                locationManager.getLastKnownLocation(provider)
            }.getOrNull()
        }.maxByOrNull { it.time }
    }

    private fun bestEnabledProvider(): String? {
        return when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
    }
}
