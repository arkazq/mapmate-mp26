package com.mapmate.data.remote.provider

import org.w3c.dom.Element

internal object SeoulBusPositionXmlParser {
    fun parse(xml: String): List<SeoulBusPositionItem> {
        val document = parseSeoulBusXml(xml)
        val nodes = document.getElementsByTagName("itemList")

        return buildList {
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as? Element ?: continue
                add(
                    SeoulBusPositionItem(
                        plainNo = element.text("plainNo"),
                        stationName = element.text("stationNm") ?: element.text("stNm"),
                        isArriving = element.text("isArrive")?.toIntOrNull() == 1,
                        sectionOrder = element.text("sectOrd")?.toIntOrNull(),
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
}

internal data class SeoulBusPositionItem(
    val plainNo: String?,
    val stationName: String?,
    val isArriving: Boolean,
    val sectionOrder: Int?,
)
