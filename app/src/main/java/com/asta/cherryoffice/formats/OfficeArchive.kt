package com.asta.cherryoffice.formats

import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element

/** Limits apply to decompressed data, including entries we do not retain. */
object OfficeArchive {
    fun read(input: InputStream, keep: (String) -> Boolean): Map<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        var total = 0L
        var count = 0
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(++count <= 10000) { "This document contains too many archive entries." }
                val name = entry.name.replace('\\', '/')
                val retain = keep(name) && !entry.isDirectory
                val out = if (retain) java.io.ByteArrayOutputStream() else null
                val buffer = ByteArray(8192)
                var entrySize = 0
                while (true) {
                    val n = zip.read(buffer)
                    if (n < 0) break
                    total += n
                    entrySize += n
                    require(total <= 128L * 1024 * 1024 && entrySize <= 24 * 1024 * 1024) {
                        "This document is too large to preview safely on this device."
                    }
                    out?.write(buffer, 0, n)
                }
                if (out != null) result[name] = out.toByteArray()
                zip.closeEntry()
            }
        }
        return result
    }

    fun xml(bytes: ByteArray): Document {
        // Android XML factories do not all implement Xerces security features.
        // Reject declarations independently, including UTF-16/32 encodings.
        val declaration = String(bytes, Charsets.ISO_8859_1).replace("\u0000", "")
        if (declaration.contains("<!DOCTYPE", ignoreCase = true) || declaration.contains("<!ENTITY", ignoreCase = true)) {
            throw org.xml.sax.SAXParseException("Document type declarations are not allowed in Office XML.", null)
        }
        val builder = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            try { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            catch (_: javax.xml.parsers.ParserConfigurationException) { /* Rejected above on every runtime. */ }
            isExpandEntityReferences = false
        }.newDocumentBuilder()
        builder.setEntityResolver { _, _ -> throw org.xml.sax.SAXException("External XML references are not allowed.") }
        return builder.parse(bytes.inputStream())
    }

    fun elements(parent: org.w3c.dom.Node, name: String): List<Element> {
        val nodes = when (parent) {
            is Document -> parent.getElementsByTagNameNS("*", name)
            is Element -> parent.getElementsByTagNameNS("*", name)
            else -> return emptyList()
        }
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }

    fun resolve(base: String, target: String): String {
        val parts = mutableListOf<String>()
        val path = if (target.startsWith('/')) target else "$base/$target"
        path.split('/').forEach {
            when (it) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts.add(it)
            }
        }
        return parts.joinToString("/")
    }
}
