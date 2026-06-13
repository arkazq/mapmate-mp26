package com.mapmate.data.remote.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeoulBusPositionXmlParserTest {
    @Test
    fun parse_readsPositionItems() {
        val xml = """
            <ServiceResult>
                <msgBody>
                    <itemList>
                        <plainNo>서울70사1234</plainNo>
                        <stationNm>숭실대입구역</stationNm>
                        <isArrive>1</isArrive>
                        <sectOrd>5</sectOrd>
                    </itemList>
                </msgBody>
            </ServiceResult>
        """.trimIndent()

        val result = SeoulBusPositionXmlParser.parse(xml)

        assertEquals(1, result.size)
        assertEquals("서울70사1234", result.first().plainNo)
        assertEquals("숭실대입구역", result.first().stationName)
        assertTrue(result.first().isArriving)
        assertEquals(5, result.first().sectionOrder)
    }
}
