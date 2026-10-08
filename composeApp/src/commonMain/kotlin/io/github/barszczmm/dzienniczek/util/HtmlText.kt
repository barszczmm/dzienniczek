package io.github.barszczmm.dzienniczek.util

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode

/**
 * Converts HTML from the journals (announcements, messages) to readable plain text:
 * paragraphs and line breaks are kept, lists get "1." / "•" markers, entities are decoded.
 * Plain text without tags is returned unchanged (apart from trimming).
 */
fun htmlToPlainText(input: String): String {
    if (input.isBlank()) return ""
    if (!Regex("<[a-zA-Z/!][^>]*>|&[a-zA-Z#0-9]+;").containsMatchIn(input)) return input.trim()

    val out = StringBuilder()
    fun newLines(count: Int) {
        val trailing = out.length - out.trimEnd('\n').length
        if (out.isNotEmpty() && trailing < count) repeat(count - trailing) { out.append('\n') }
    }

    fun walk(node: Node, listCounter: IntArray?) {
        when (node) {
            is TextNode -> {
                // Collapse HTML whitespace like a browser, keep non-breaking spaces as spaces.
                val text = node.text().replace(' ', ' ').replace(Regex("\\s+"), " ")
                if (text.isNotBlank() || (out.isNotEmpty() && !out.endsWith(" ") && !out.endsWith("\n"))) {
                    out.append(if (out.isEmpty() || out.endsWith("\n")) text.trimStart() else text)
                }
            }
            is Element -> when (node.tagName().lowercase()) {
                "br" -> out.append('\n')
                "p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "table" -> {
                    newLines(if (node.tagName().lowercase() == "div") 1 else 2)
                    node.childNodes().forEach { walk(it, listCounter) }
                    newLines(if (node.tagName().lowercase() == "div") 1 else 2)
                }
                "tr" -> {
                    newLines(1)
                    node.childNodes().forEach { walk(it, listCounter) }
                }
                "td", "th" -> {
                    if (!out.endsWith("\n")) out.append("  ")
                    node.childNodes().forEach { walk(it, listCounter) }
                }
                "ol" -> {
                    newLines(1)
                    val counter = intArrayOf(node.attr("start").toIntOrNull()?.minus(1) ?: 0)
                    node.childNodes().forEach { walk(it, counter) }
                    newLines(2)
                }
                "ul" -> {
                    newLines(1)
                    node.childNodes().forEach { walk(it, null) }
                    newLines(2)
                }
                "li" -> {
                    newLines(1)
                    if (listCounter != null) {
                        listCounter[0]++
                        out.append("${listCounter[0]}. ")
                    } else {
                        out.append("• ")
                    }
                    node.childNodes().forEach { walk(it, null) }
                    newLines(1)
                }
                "script", "style", "head" -> Unit
                else -> node.childNodes().forEach { walk(it, listCounter) }
            }
            else -> Unit
        }
    }

    Ksoup.parse(input).body().childNodes().forEach { walk(it, null) }
    return out.toString()
        .lines().joinToString("\n") { it.trimEnd() }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}
