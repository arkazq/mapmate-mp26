package com.mapmate.data.remote.provider

import org.junit.Assert.assertEquals
import org.junit.Test

class SeoulBusArrivalXmlParserTest {
    @Test
    fun parse_readsArrivalItems() {
        val xml = """
            <ServiceResult>
                <msgBody>
                    <itemList>
                        <stId>123456</stId>
                        <arsId>11111</arsId>
                        <stNm>Test Stop</stNm>
                        <busRouteId>987654</busRouteId>
                        <rtNm>740</rtNm>
                        <arrmsg1>6분 후 도착</arrmsg1>
                        <arrmsg2>12분 후 도착</arrmsg2>
                        <exps1>360</exps1>
                        <exps2>720</exps2>
                    </itemList>
                </msgBody>
            </ServiceResult>
        """.trimIndent()

        val result = SeoulBusArrivalXmlParser.parse(xml)

        assertEquals(1, result.size)
        assertEquals("123456", result.first().stationId)
        assertEquals("11111", result.first().stationArsId)
        assertEquals("Test Stop", result.first().stationName)
        assertEquals("987654", result.first().routeId)
        assertEquals("740", result.first().routeName)
        assertEquals(360, result.first().arrivalSeconds1)
        assertEquals(720, result.first().arrivalSeconds2)
    }
}
