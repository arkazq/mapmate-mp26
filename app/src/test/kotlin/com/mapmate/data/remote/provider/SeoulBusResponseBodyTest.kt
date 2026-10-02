package com.mapmate.data.remote.provider

import com.mapmate.data.remote.api.SeoulBusArrivalApi
import com.mapmate.data.remote.api.SeoulBusPositionApi
import com.mapmate.data.remote.config.RemoteApiConfig
import com.mapmate.domain.model.TransitArrivalQuery
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.Retrofit

class SeoulBusResponseBodyTest {
    @Test fun retrofitResponseConversionDoesNotReadTheWholeXmlBody() = runTest {
        val generated = GeneratedSpacesSource(8L * 1024 * 1024)
        val body = responseBody(generated)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            // This interceptor returns a local fixture without proceeding to DNS or a network socket.
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(body).build()
        }.build()
        try {
            val api = Retrofit.Builder().baseUrl("https://invalid.example/").client(client)
                .build().create(SeoulBusArrivalApi::class.java)
            val response = api.getArrivalsByStationUid("fixture", "20170")
            assertEquals("Retrofit must leave body consumption to the bounded reader", 0L, generated.consumed)
            assertTrue(runCatching { response.readSeoulBusXml() }.exceptionOrNull() is IllegalArgumentException)
            assertTrue(generated.consumed <= 2L * 1024 * 1024 + 64 * 1024)
            assertTrue(generated.closed)
        } finally {
            body.close()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    @Test fun boundedReaderKeepsKoreanTextAndHonorsACharsetBom() = runTest {
        val xml = "<ServiceResult><stNm>숭실대입구역</stNm></ServiceResult>"
        val body = xml.toByteArray(Charsets.UTF_16).toResponseBody("application/xml; charset=utf-16".toMediaType())
        assertEquals(xml, body.readSeoulBusXml())
    }

    @Test fun exactlyTheCharacterLimitIsAllowed() = runTest {
        val value = " ".repeat(2 * 1024 * 1024)
        assertEquals(value, value.toResponseBody().readSeoulBusXml())
    }

    @Test fun oversizedUnknownLengthResponseStopsReadingAndClosesTheBody() = runTest {
        val generated = GeneratedSpacesSource(8L * 1024 * 1024)
        val body = responseBody(generated)
        val api = object : SeoulBusArrivalApi {
            override suspend fun getArrivalsByStationUid(serviceKey: String, stationArsId: String) = body
            override suspend fun getArrivalsByRouteAll(serviceKey: String, busRouteId: String): ResponseBody =
                error("No route fallback target was supplied")
        }
        val provider = SeoulBusRealtimeArrivalProvider(api, RemoteApiConfig(
            kakaoRestApiKey = "", odsayApiKey = "", googleRoutesApiKey = "", seoulOpenApiKey = "",
            seoulBusServiceKey = "fixture", tagoServiceKey = "",
        ))
        val result = provider.getArrivalEstimate(TransitArrivalQuery.Bus(
            stationName = "Stop", stationId = null, stationArsId = "20170", busRouteId = null, routeName = "753",
        ))
        assertNull(result)
        assertTrue("A large response must not be fully buffered", generated.consumed <= 2L * 1024 * 1024 + 64 * 1024)
        assertTrue("Failure must close the streaming response", generated.closed)
    }

    private fun responseBody(generated: Source) = object : ResponseBody() {
        private val buffered = generated.buffer()
        override fun contentType() = "application/xml; charset=utf-8".toMediaType()
        override fun contentLength() = -1L
        override fun source(): BufferedSource = buffered
    }

    @Test fun everySeoulXmlEndpointOptsOutOfRetrofitWholeBodyBuffering() {
        val endpoints = listOf(SeoulBusArrivalApi::class.java, SeoulBusPositionApi::class.java)
            .flatMap { it.declaredMethods.toList() }.filter { it.isAnnotationPresent(GET::class.java) }
        assertTrue(endpoints.isNotEmpty())
        endpoints.forEach { endpoint ->
            assertTrue(endpoint.name, endpoint.isAnnotationPresent(Streaming::class.java))
        }
    }

    private class GeneratedSpacesSource(private val size: Long) : Source {
        var consumed = 0L
            private set
        var closed = false
            private set
        override fun read(sink: Buffer, byteCount: Long): Long {
            if (consumed == size) return -1
            val count = minOf(byteCount, size - consumed, 8_192L).toInt()
            sink.write(ByteArray(count) { 32 })
            consumed += count
            return count.toLong()
        }
        override fun timeout() = Timeout.NONE
        override fun close() { closed = true }
    }
}
