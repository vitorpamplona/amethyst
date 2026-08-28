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
package com.vitorpamplona.amethyst.shared.resources

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parses an Android VectorDrawable XML into a Compose [ImageVector].
 *
 * Android's `pathData` is SVG path syntax, which Compose already parses, so
 * this only has to translate the surrounding element and attribute vocabulary:
 * `<vector>` sizing and tint, `<group>` transforms, `<path>` fills and strokes,
 * and the `<aapt:attr>` + `<gradient>` form used for gradient fills.
 *
 * Scope is deliberately the vocabulary Amethyst's own drawables use. An
 * unrecognised attribute is ignored rather than guessed at; an unparseable
 * document raises, because a silently blank icon is worse than a loud failure
 * at the point the resource is registered.
 */
object VectorDrawableParser {
    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

    fun parse(
        input: InputStream,
        name: String,
    ): ImageVector {
        val factory = DocumentBuilderFactory.newInstance()
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        factory.isNamespaceAware = true
        factory.isXIncludeAware = false
        factory.isExpandEntityReferences = false
        val root = factory.newDocumentBuilder().parse(input).documentElement

        require(root.localName == "vector" || root.tagName == "vector") {
            "not a VectorDrawable: root element is <${root.tagName}>"
        }

        val viewportWidth = root.androidFloat("viewportWidth") ?: 24f
        val viewportHeight = root.androidFloat("viewportHeight") ?: 24f
        val builder =
            ImageVector.Builder(
                name = name,
                defaultWidth = (root.androidDimension("width") ?: viewportWidth).dp,
                defaultHeight = (root.androidDimension("height") ?: viewportHeight).dp,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                tintColor = root.androidColor("tint") ?: Color.Unspecified,
            )

        root.childElements().forEach { builder.addNode(it) }
        return builder.build()
    }

    private fun ImageVector.Builder.addNode(element: Element) {
        when (element.localName ?: element.tagName) {
            "path" -> addPathElement(element)
            "group" -> {
                addGroup(
                    name = element.android("name").orEmpty(),
                    rotate = element.androidFloat("rotation") ?: 0f,
                    pivotX = element.androidFloat("pivotX") ?: 0f,
                    pivotY = element.androidFloat("pivotY") ?: 0f,
                    scaleX = element.androidFloat("scaleX") ?: 1f,
                    scaleY = element.androidFloat("scaleY") ?: 1f,
                    translationX = element.androidFloat("translateX") ?: 0f,
                    translationY = element.androidFloat("translateY") ?: 0f,
                )
                element.childElements().forEach { addNode(it) }
                clearGroup()
            }
            // <clip-path> and animation elements are intentionally unsupported.
            else -> Unit
        }
    }

    private fun ImageVector.Builder.addPathElement(element: Element) {
        val pathData = element.android("pathData") ?: return
        val nodes = PathParser().parsePathString(pathData).toNodes()

        addPath(
            pathData = nodes,
            pathFillType = if (element.android("fillType").equals("evenOdd", ignoreCase = true)) PathFillType.EvenOdd else PathFillType.NonZero,
            name = element.android("name").orEmpty(),
            fill = element.brushFor("fillColor"),
            fillAlpha = element.androidFloat("fillAlpha") ?: 1f,
            stroke = element.brushFor("strokeColor"),
            strokeAlpha = element.androidFloat("strokeAlpha") ?: 1f,
            strokeLineWidth = element.androidFloat("strokeWidth") ?: 0f,
            strokeLineJoin =
                when (element.android("strokeLineJoin")?.lowercase()) {
                    "bevel" -> StrokeJoin.Bevel
                    "round" -> StrokeJoin.Round
                    else -> StrokeJoin.Miter
                },
        )
    }

    /**
     * A colour attribute is either inline (`android:fillColor="#RRGGBB"`) or
     * nested as `<aapt:attr name="android:fillColor"><gradient>`, which is how
     * aapt inlines a gradient that has no place in an attribute value.
     */
    private fun Element.brushFor(attribute: String): Brush? {
        androidColor(attribute)?.let { return SolidColor(it) }

        val nested =
            childElements().firstOrNull { child ->
                (child.localName ?: child.tagName) == "attr" &&
                    child.getAttribute("name") == "android:$attribute"
            } ?: return null

        val gradient = nested.childElements().firstOrNull { (it.localName ?: it.tagName) == "gradient" } ?: return null
        val stops =
            gradient
                .childElements()
                .filter { (it.localName ?: it.tagName) == "item" }
                .mapNotNull { item ->
                    val color = item.androidColor("color") ?: return@mapNotNull null
                    (item.androidFloat("offset") ?: 0f) to color
                }
        if (stops.isEmpty()) return null

        return when (gradient.android("type")?.lowercase()) {
            "radial" ->
                Brush.radialGradient(
                    colorStops = stops.toTypedArray(),
                    center = Offset(gradient.androidFloat("centerX") ?: 0f, gradient.androidFloat("centerY") ?: 0f),
                    radius = gradient.androidFloat("gradientRadius") ?: 0f,
                )
            // "sweep" has no direct Compose equivalent that honours the start
            // angle, so it degrades to the linear reading of its stops.
            else ->
                Brush.linearGradient(
                    colorStops = stops.toTypedArray(),
                    start = Offset(gradient.androidFloat("startX") ?: 0f, gradient.androidFloat("startY") ?: 0f),
                    end = Offset(gradient.androidFloat("endX") ?: 0f, gradient.androidFloat("endY") ?: 0f),
                )
        }
    }

    private fun Element.android(name: String): String? =
        getAttributeNS(ANDROID_NS, name).takeIf { it.isNotEmpty() }
            ?: getAttribute("android:$name").takeIf { it.isNotEmpty() }

    private fun Element.androidFloat(name: String): Float? = android(name)?.toFloatOrNull()

    /** `489dp`, `24dip`, `12px` — the unit is dropped; vectors scale anyway. */
    private fun Element.androidDimension(name: String): Float? = android(name)?.trimEnd { it.isLetter() }?.toFloatOrNull()

    private fun Element.androidColor(name: String): Color? = android(name)?.let(::parseColor)

    /** `#RGB`, `#ARGB`, `#RRGGBB` and `#AARRGGBB`, as aapt accepts them. */
    internal fun parseColor(value: String): Color? {
        if (!value.startsWith("#")) return null
        val hex = value.substring(1)
        val argb =
            when (hex.length) {
                3 -> "FF" + hex.map { "$it$it" }.joinToString("")
                4 -> hex.map { "$it$it" }.joinToString("")
                6 -> "FF$hex"
                8 -> hex
                else -> return null
            }
        return argb.toLongOrNull(16)?.let { Color(it.toULong().toLong().toInt()) }
    }

    private fun Element.childElements(): List<Element> {
        val out = ArrayList<Element>()
        val children = childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) out.add(node as Element)
        }
        return out
    }
}
