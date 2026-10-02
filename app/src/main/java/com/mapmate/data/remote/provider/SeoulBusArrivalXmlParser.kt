package com.mapmate.data.remote.provider

import org.w3c.dom.Element

internal object SeoulBusArrivalXmlParser {
    fun parse(xml: String): List<SeoulBusArrivalItem> {
        val document = parseSeoulBusXml(xml)
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
                        routeName = element.text("rtNm"),
                        routeShortName = element.text("busRouteAbrv"),
                        arrivalMessage1 = element.text("arrmsg1"),
                        arrivalMessage2 = element.text("arrmsg2"),
                        arrivalSeconds1 = element.firstInt("exps1", "traTime1"),
                        arrivalSeconds2 = element.firstInt("exps2", "traTime2"),
                    ),
                )
            }
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

    private fun Element.firstInt(vararg tagNames: String): Int? {
        val values = tagNames.mapNotNull { text(it)?.toIntOrNull() }
        return values.firstOrNull { it > 0 } ?: values.firstOrNull()
    }
}

internal data class SeoulBusArrivalItem(
    val stationId: String?,
    val stationArsId: String?,
    val stationName: String?,
    val routeId: String?,
    val routeName: String?,
    val routeShortName: String?,
    val arrivalMessage1: String?,
    val arrivalMessage2: String?,
    val arrivalSeconds1: Int?,
    val arrivalSeconds2: Int?,
)
