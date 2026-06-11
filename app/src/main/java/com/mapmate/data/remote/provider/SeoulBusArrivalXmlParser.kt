package com.mapmate.data.remote.provider

import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

internal object SeoulBusArrivalXmlParser {
    fun parse(xml: String): List<SeoulBusArrivalItem> {
        val document = documentBuilderFactory()
            .newDocumentBuilder()
            .parse(InputSource(StringReader(xml)))
        val nodes = document.getElementsByTagName("itemList")

        return buildList {
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as? Element ?: continue
                add(
                    SeoulBusArrivalItem(
                        stationId = element.text("stId"),
                        stationArsId = element.text("arsId"),
                        stationName = element.text("stNm"),
                        routeId = element.text("busRouteId"),
                        routeName = element.text("rtNm")
                            ?: element.text("busRouteAbrv"),
                        arrivalMessage1 = element.text("arrmsg1"),
                        arrivalMessage2 = element.text("arrmsg2"),
                        arrivalSeconds1 = element.text("exps1")?.toIntOrNull(),
                        arrivalSeconds2 = element.text("exps2")?.toIntOrNull(),
                    ),
                )
            }
        }
    }

    private fun documentBuilderFactory(): DocumentBuilderFactory {
        return DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
    }

    private fun Element.text(tagName: String): String? {
        val value = getElementsByTagName(tagName)
            .item(0)
            ?.textContent
            ?.trim()
            .orEmpty()
        return value.takeIf(String::isNotBlank)
    }
}

internal data class SeoulBusArrivalItem(
    val stationId: String?,
    val stationArsId: String?,
    val stationName: String?,
    val routeId: String?,
    val routeName: String?,
    val arrivalMessage1: String?,
    val arrivalMessage2: String?,
    val arrivalSeconds1: Int?,
    val arrivalSeconds2: Int?,
)
