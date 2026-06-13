package com.mapmate.data.remote.provider

import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

internal object SeoulBusPositionXmlParser {
    fun parse(xml: String): List<SeoulBusPositionItem> {
        val document = documentBuilderFactory()
            .newDocumentBuilder()
            .parse(InputSource(StringReader(xml)))
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

internal data class SeoulBusPositionItem(
    val plainNo: String?,
    val stationName: String?,
    val isArriving: Boolean,
    val sectionOrder: Int?,
)
