package io.github.barszczmm.dzienniczek.util

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink

/**
 * Converts HTML from the journals (announcements, messages) to readable plain text:
 * paragraphs and line breaks are kept, lists get "1." / "•" markers, entities are decoded.
 * Plain text without tags is returned unchanged (apart from trimming).
 */
fun htmlToPlainText(input: String): String =
    htmlToMarkedText(input).replace(Regex("$LINK_START[^$LINK_TEXT]*$LINK_TEXT"), "").replace(LINK_END, "")

// Markers around links in the intermediate text: START url TEXT label END.
private const val LINK_START = "\u0001"
private const val LINK_TEXT = "\u0002"
private const val LINK_END = "\u0003"

private fun htmlToMarkedText(input: String): String {
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
                "a" -> {
                    val href = node.attr("href").trim()
                    if (href.startsWith("http", ignoreCase = true) || href.startsWith("mailto:", ignoreCase = true)) {
                        out.append(LINK_START).append(href).append(LINK_TEXT)
                        val before = out.length
                        node.childNodes().forEach { walk(it, listCounter) }
                        if (out.substring(before).isBlank()) out.append(href)
                        out.append(LINK_END)
                    } else {
                        node.childNodes().forEach { walk(it, listCounter) }
                    }
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

private val BARE_URL = Regex("""(https?://|www\.)[^\s<>"]+[^\s<>".,;:!?)\]]""", RegexOption.IGNORE_CASE)

/**
 * Like [htmlToPlainText], but links (<a href> and bare http(s)/www addresses) are clickable
 * – they open in the browser via the platform URI handler.
 */
fun htmlToAnnotatedString(input: String, linkStyle: SpanStyle): AnnotatedString {
    val marked = htmlToMarkedText(input)
    val styles = TextLinkStyles(style = linkStyle)
    return buildAnnotatedString {
        fun appendWithBareLinks(text: String) {
            var last = 0
            BARE_URL.findAll(text).forEach { m ->
                append(text.substring(last, m.range.first))
                val url = if (m.value.startsWith("www.", ignoreCase = true)) "https://${m.value}" else m.value
                withLink(LinkAnnotation.Url(url, styles)) { append(m.value) }
                last = m.range.last + 1
            }
            append(text.substring(last))
        }

        var i = 0
        while (i < marked.length) {
            val start = marked.indexOf(LINK_START, i)
            if (start < 0) {
                appendWithBareLinks(marked.substring(i)); break
            }
            appendWithBareLinks(marked.substring(i, start))
            val textSep = marked.indexOf(LINK_TEXT, start)
            val end = marked.indexOf(LINK_END, textSep.coerceAtLeast(start))
            if (textSep < 0 || end < 0) {
                appendWithBareLinks(marked.substring(start + 1)); break
            }
            val url = marked.substring(start + 1, textSep)
            val label = marked.substring(textSep + 1, end)
            withLink(LinkAnnotation.Url(url, styles)) { append(label) }
            i = end + 1
        }
    }
}

/** [htmlToAnnotatedString] with the app's link colour, remembered for the given HTML. */
@Composable
fun rememberHtmlText(html: String): AnnotatedString {
    val color = MaterialTheme.colorScheme.primary
    return remember(html, color) {
        htmlToAnnotatedString(html, SpanStyle(color = color, textDecoration = TextDecoration.Underline))
    }
}
