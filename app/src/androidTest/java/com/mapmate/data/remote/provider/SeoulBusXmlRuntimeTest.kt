package com.mapmate.data.remote.provider

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody

@RunWith(AndroidJUnit4::class)
class SeoulBusXmlRuntimeTest {
    @Test fun streamedBodyKeepsKoreanTextAndRejectsOversizedXmlOnAndroid() = runBlocking {
        val xml = "<ServiceResult><msgBody><itemList><stNm>숭실대입구역</stNm></itemList></msgBody></ServiceResult>"
        val body = xml.toByteArray(Charsets.UTF_16).toResponseBody("application/xml; charset=utf-16".toMediaType())
        assertEquals("숭실대입구역", SeoulBusArrivalXmlParser.parse(body.readSeoulBusXml()).single().stationName)
        val oversized = " ".repeat(2 * 1024 * 1024 + 1).toResponseBody()
        assertTrue(runCatching { oversized.readSeoulBusXml() }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test fun ordinaryBusXmlParsesOnAndroidWithoutDependingOnOptionalParserFeatures() {
        val xml = "<ServiceResult><msgBody><itemList><stNm>A &amp; B</stNm><stationNm>A &amp; B</stationNm>" +
            "<rtNm>753</rtNm><traTime1>180</traTime1><arrmsg1>3분 후 도착</arrmsg1>" +
            "</itemList></msgBody></ServiceResult>"
        assertEquals("A & B", SeoulBusArrivalXmlParser.parse(xml).single().stationName)
        assertEquals("A & B", SeoulBusPositionXmlParser.parse(xml).single().stationName)
    }

    @Test fun declarationsAreRejectedByAndroidParserWithoutResolvingExternalResources() {
        val xml = "<!DOCTYPE ServiceResult SYSTEM 'https://invalid.example/fixture.dtd'><ServiceResult/>"
        assertTrue(runCatching { SeoulBusArrivalXmlParser.parse(xml) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { SeoulBusPositionXmlParser.parse(xml) }.exceptionOrNull() is IllegalArgumentException)
    }
}
