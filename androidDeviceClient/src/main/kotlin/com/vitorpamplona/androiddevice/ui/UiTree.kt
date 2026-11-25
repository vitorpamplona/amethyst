/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.androiddevice.ui

import org.w3c.dom.Element
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.IOException
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import kotlin.math.roundToInt

/** A rectangle in device pixels, as uiautomator's `bounds="[l,t][r,b]"` gives it. */
data class Bounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
    val isEmpty: Boolean get() = right <= left || bottom <= top

    fun scaled(scale: Double) = Bounds((left * scale).roundToInt(), (top * scale).roundToInt(), (right * scale).roundToInt(), (bottom * scale).roundToInt())
}

/** One view (or Compose node exposed to accessibility) in the window hierarchy. */
data class UiNode(
    /** Its number in [UiTree.nodes]: what the agent names it by. */
    val id: Int,
    val depth: Int,
    val className: String,
    val text: String,
    val contentDescription: String,
    val resourceId: String,
    val packageName: String,
    val bounds: Bounds,
    val clickable: Boolean,
    val longClickable: Boolean,
    val scrollable: Boolean,
    val checkable: Boolean,
    val checked: Boolean,
    val enabled: Boolean,
    val focused: Boolean,
    val selected: Boolean,
    val password: Boolean,
) {
    val isEditable: Boolean get() = className.endsWith("EditText") || className.endsWith("TextField")

    /** Whether it says or does anything: the rest are layout. */
    val isInteresting: Boolean
        get() = text.isNotBlank() || contentDescription.isNotBlank() || clickable || longClickable || scrollable || checkable || isEditable

    val shortClass: String get() = className.substringAfterLast('.')
}

/**
 * The window hierarchy uiautomator dumps, parsed: every node in document order, so the agent can
 * read the screen as text and act on a node by number or by what it says.
 */
class UiTree(
    val nodes: List<UiNode>,
) {
    /**
     * The screen as lines the agent reads: each node that says or does something, indented by
     * depth, with its number, kind, text, id and bounds ([scale] maps device pixels to the space
     * the caller's coordinates use).
     */
    fun describe(
        scale: Double = 1.0,
        maxLines: Int = 400,
    ): String {
        val shown = nodes.filter { it.isInteresting && !it.bounds.isEmpty }
        if (shown.isEmpty()) return "Nothing on screen says or does anything (it may still be loading, or show only images)."
        val minDepth = shown.minOf { it.depth }
        val lines =
            shown.take(maxLines).map { n ->
                buildString {
                    repeat((n.depth - minDepth).coerceAtMost(12)) { append("  ") }
                    append('#').append(n.id).append(' ').append(n.shortClass)
                    if (n.text.isNotBlank()) append(" \"").append(n.text.oneLine()).append('"')
                    if (n.contentDescription.isNotBlank() && n.contentDescription != n.text) append(" desc=\"").append(n.contentDescription.oneLine()).append('"')
                    if (n.resourceId.isNotBlank()) append(" id=").append(n.resourceId.substringAfter(":id/"))
                    val b = n.bounds.scaled(scale)
                    append(" [")
                        .append(b.left)
                        .append(',')
                        .append(b.top)
                        .append("][")
                        .append(b.right)
                        .append(',')
                        .append(b.bottom)
                        .append(']')
                    val flags =
                        listOfNotNull(
                            "clickable".takeIf { n.clickable },
                            "long-clickable".takeIf { n.longClickable },
                            "scrollable".takeIf { n.scrollable },
                            "editable".takeIf { n.isEditable },
                            (if (n.checked) "checked" else "unchecked").takeIf { n.checkable },
                            "focused".takeIf { n.focused },
                            "selected".takeIf { n.selected },
                            "disabled".takeIf { !n.enabled },
                            "password".takeIf { n.password },
                        )
                    if (flags.isNotEmpty()) append(' ').append(flags.joinToString(" "))
                }
            }
        val more = if (shown.size > maxLines) "\n… and ${shown.size - maxLines} more" else ""
        return lines.joinToString("\n") + more
    }

    fun node(id: Int): UiNode? = nodes.getOrNull(id)

    /**
     * The node whose text, description or resource id is [query]: an exact match (ignoring case)
     * first, then one that contains it; among equals, one that can be tapped, then the smallest.
     */
    fun find(query: String): UiNode? {
        val q = query.trim()
        if (q.isEmpty()) return null
        val candidates = nodes.filter { !it.bounds.isEmpty }

        fun matches(
            n: UiNode,
            exact: Boolean,
        ): Boolean {
            val fields = listOf(n.text, n.contentDescription, n.resourceId.substringAfter(":id/"))
            return fields.any { f -> if (exact) f.equals(q, ignoreCase = true) else f.contains(q, ignoreCase = true) }
        }
        val order = compareByDescending<UiNode> { it.clickable || it.longClickable || it.isEditable }.thenBy { (it.bounds.right - it.bounds.left).toLong() * (it.bounds.bottom - it.bounds.top) }
        return candidates.filter { matches(it, exact = true) }.minWithOrNull(order)
            ?: candidates.filter { matches(it, exact = false) }.minWithOrNull(order)
    }

    /** Whether any node shows or is described by [query] (contains, ignoring case). */
    fun contains(query: String): Boolean = nodes.any { it.text.contains(query, ignoreCase = true) || it.contentDescription.contains(query, ignoreCase = true) }

    companion object {
        /** Parses uiautomator's XML. Throws [IOException] when it isn't a hierarchy. */
        fun parse(xml: String): UiTree {
            val document =
                try {
                    val factory = DocumentBuilderFactory.newInstance()
                    // The dump comes from a device: no DTDs, no external entities.
                    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                    factory.isExpandEntityReferences = false
                    factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
                } catch (e: ParserConfigurationException) {
                    throw IOException("Can't read the UI hierarchy", e)
                } catch (e: SAXException) {
                    throw IOException("The UI hierarchy isn't valid XML", e)
                }
            val nodes = ArrayList<UiNode>()

            fun walk(
                element: Element,
                depth: Int,
            ) {
                val children = element.childNodes
                for (i in 0 until children.length) {
                    val child = children.item(i) as? Element ?: continue
                    if (child.tagName != "node") continue
                    nodes += node(nodes.size, depth, child)
                    walk(child, depth + 1)
                }
            }
            walk(document.documentElement, 0)
            return UiTree(nodes)
        }

        private fun node(
            id: Int,
            depth: Int,
            e: Element,
        ): UiNode {
            fun flag(name: String) = e.getAttribute(name) == "true"
            return UiNode(
                id = id,
                depth = depth,
                className = e.getAttribute("class"),
                text = e.getAttribute("text"),
                contentDescription = e.getAttribute("content-desc"),
                resourceId = e.getAttribute("resource-id"),
                packageName = e.getAttribute("package"),
                bounds = parseBounds(e.getAttribute("bounds")),
                clickable = flag("clickable"),
                longClickable = flag("long-clickable"),
                scrollable = flag("scrollable"),
                checkable = flag("checkable"),
                checked = flag("checked"),
                enabled = flag("enabled"),
                focused = flag("focused"),
                selected = flag("selected"),
                password = flag("password"),
            )
        }

        private val BOUNDS = Regex("\\[(-?\\d+),(-?\\d+)]\\[(-?\\d+),(-?\\d+)]")

        fun parseBounds(text: String): Bounds = BOUNDS.find(text)?.groupValues?.let { Bounds(it[1].toInt(), it[2].toInt(), it[3].toInt(), it[4].toInt()) } ?: Bounds(0, 0, 0, 0)

        private fun String.oneLine(): String = replace('\n', ' ').replace("\"", "\\\"").let { if (it.length > 120) it.take(117) + "…" else it }
    }
}
