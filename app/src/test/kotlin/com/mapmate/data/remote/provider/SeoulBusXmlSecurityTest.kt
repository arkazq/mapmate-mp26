package com.mapmate.data.remote.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeoulBusXmlSecurityTest {
    @Test fun normalDataAndPredefinedXmlEscapesStillParse() {
        val xml = "<ServiceResult><msgBody><itemList><stNm>A &amp; B</stNm><stationNm>A &amp; B</stationNm></itemList></msgBody></ServiceResult>"
        assertEquals("A & B", SeoulBusArrivalXmlParser.parse(xml).single().stationName)
        assertEquals("A & B", SeoulBusPositionXmlParser.parse(xml).single().stationName)
    }

    @Test fun externalDeclarationsAndEntityExpansionAreRejectedBeforeParsing() {
        for (declaration in listOf(
            "<!DOCTYPE ServiceResult SYSTEM 'https://invalid.example/fixture.dtd'>",
            "<!DOCTYPE ServiceResult [<!ENTITY fixture SYSTEM 'file:///nonexistent-qa-fixture'>]>",
            "<!DOCTYPE ServiceResult [<!ENTITY a 'value'><!ENTITY b '&a;&a;&a;'>]>",
            "<!ENTITY fixture 'value'>",
        )) {
            val xml = declaration + "<ServiceResult/>"
            assertTrue(runCatching { SeoulBusArrivalXmlParser.parse(xml) }.exceptionOrNull() is IllegalArgumentException)
            assertTrue(runCatching { SeoulBusPositionXmlParser.parse(xml) }.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test fun oversizedResponseIsRejectedInsteadOfAllocatingADomTree() {
        val xml = "<ServiceResult>" + " ".repeat(2 * 1024 * 1024) + "</ServiceResult>"
        assertTrue(runCatching { parseSeoulBusXml(xml) }.exceptionOrNull() is IllegalArgumentException)
    }
}
