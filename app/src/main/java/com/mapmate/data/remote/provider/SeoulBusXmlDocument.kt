package com.mapmate.data.remote.provider

import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody

internal suspend fun ResponseBody.readSeoulBusXml(): String = use { body ->
    withContext(Dispatchers.IO) {
        val reader = body.charStream()
        val buffer = CharArray(8_192)
        val result = StringBuilder()
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = reader.read(buffer, 0, minOf(buffer.size, MAX_SEOUL_BUS_XML_CHARACTERS - result.length + 1))
            if (count == -1) break
            require(result.length + count <= MAX_SEOUL_BUS_XML_CHARACTERS) { "Bus XML response is too large." }
            result.append(buffer, 0, count)
        }
        result.toString()
    }
}

internal fun parseSeoulBusXml(xml: String): Document {
    require(xml.length <= MAX_SEOUL_BUS_XML_CHARACTERS) { "Bus XML response is too large." }
    // These APIs return data-only XML; reject declarations even on parsers without the feature flags.
    require(!xml.contains("<!DOCTYPE", ignoreCase = true) && !xml.contains("<!ENTITY", ignoreCase = true)) {
        "Bus XML declarations are not allowed."
    }
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        isExpandEntityReferences = false
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
    }
    return factory.newDocumentBuilder().apply {
        setEntityResolver { _, _ -> throw SAXException("External XML resources are not allowed.") }
    }.parse(InputSource(StringReader(xml)))
}

private const val MAX_SEOUL_BUS_XML_CHARACTERS = 2 * 1024 * 1024
