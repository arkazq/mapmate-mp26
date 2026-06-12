package com.mapmate.data.remote.provider

import com.mapmate.data.remote.dto.KakaoReverseGeocodeAddress
import com.mapmate.data.remote.dto.KakaoReverseGeocodeDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KakaoReverseGeocodingProviderTest {
    @Test
    fun selectAddress_prefersRoadAddress() {
        val document = KakaoReverseGeocodeDocument(
            roadAddress = KakaoReverseGeocodeAddress("서울특별시 동작구 상도로 369"),
            address = KakaoReverseGeocodeAddress("서울 동작구 상도동 511"),
        )

        val address = KakaoReverseGeocodingProvider.selectAddress(document)

        assertEquals("서울특별시 동작구 상도로 369", address)
    }

    @Test
    fun selectAddress_usesAddressWhenRoadAddressIsBlank() {
        val document = KakaoReverseGeocodeDocument(
            roadAddress = KakaoReverseGeocodeAddress(" "),
            address = KakaoReverseGeocodeAddress("서울 동작구 상도동 511"),
        )

        val address = KakaoReverseGeocodingProvider.selectAddress(document)

        assertEquals("서울 동작구 상도동 511", address)
    }

    @Test
    fun selectAddress_returnsNullWhenNoAddressExists() {
        val document = KakaoReverseGeocodeDocument(
            roadAddress = null,
            address = KakaoReverseGeocodeAddress(""),
        )

        val address = KakaoReverseGeocodingProvider.selectAddress(document)

        assertNull(address)
    }
}
