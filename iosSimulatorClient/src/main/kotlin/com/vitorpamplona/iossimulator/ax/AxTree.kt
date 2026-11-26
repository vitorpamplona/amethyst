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
package com.vitorpamplona.iossimulator.ax

import com.vitorpamplona.androiddevice.ui.Bounds
import com.vitorpamplona.androiddevice.ui.UiNode
import com.vitorpamplona.androiddevice.ui.UiTree
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.io.IOException
import kotlin.math.roundToInt

/**
 * idb's accessibility tree (`accessibility_info`, nested format) as the same [UiTree] an Android
 * screen gives, so the agent reads and taps an iPhone's screen the way it does a phone's: each
 * element numbered in document order, with its bounds in the screen's pixels.
 *
 * An element's keys, as idb writes them: `type` ("Button", "StaticText", "TextField"…),
 * `AXLabel`, `AXValue`, `AXUniqueId` (the accessibility identifier), `title`, `placeholder`,
 * `frame` ({x, y, width, height} in points; older companions only give `AXFrame` as
 * "{{x, y}, {w, h}}"), `enabled`, `focused`, `hidden`, `traits` (names), `children`.
 */
object AxTree {
    private val json = Json { ignoreUnknownKeys = true }

    private val CLICKABLE = setOf("Button", "Link", "Cell", "Switch", "Toggle", "Key", "MenuItem", "Icon", "Tab", "TabBarButton", "SegmentedControl", "RadioButton", "CheckBox", "PopUpButton")
    private val SCROLLABLE = setOf("ScrollView", "Table", "CollectionView", "WebView", "TextView", "Picker", "PickerWheel")
    private val EDITABLE = setOf("TextField", "SecureTextField", "SearchField", "TextView")
    private val CHECKABLE = setOf("Switch", "Toggle", "CheckBox")
    private val FRAME_TEXT = Regex("""\{\{\s*(-?[\d.]+)\s*,\s*(-?[\d.]+)\s*}\s*,\s*\{\s*(-?[\d.]+)\s*,\s*(-?[\d.]+)\s*}}""")

    /** Parses [text]; [density] (pixels per point) turns its point frames into pixels. */
    fun parse(
        text: String,
        density: Double,
    ): UiTree {
        val root =
            try {
                json.parseToJsonElement(text)
            } catch (e: IllegalArgumentException) {
                throw IOException("The accessibility tree isn't JSON", e)
            }
        val nodes = ArrayList<UiNode>()

        fun walk(
            element: JsonElement,
            depth: Int,
        ) {
            when (element) {
                is JsonArray -> element.forEach { walk(it, depth) }
                is JsonObject -> {
                    if (element.bool("hidden") != true) nodes += node(nodes.size, depth, element, density)
                    (element["children"] as? JsonArray)?.forEach { walk(it, depth + 1) }
                }
                else -> Unit
            }
        }
        walk(root, 0)
        return UiTree(nodes)
    }

    private fun node(
        id: Int,
        depth: Int,
        e: JsonObject,
        density: Double,
    ): UiNode {
        val type = e.str("type") ?: e.str("role")?.removePrefix("AX") ?: "Element"
        val traits = (e["traits"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.lowercase() }.orEmpty().toSet()
        val label = e.str("AXLabel") ?: e.str("title")
        val value = e.str("AXValue")
        val placeholder = e.str("placeholder")
        val editable = type in EDITABLE && (type != "TextView" || "notenabled" !in traits)
        val checkable = type in CHECKABLE
        return UiNode(
            id = id,
            depth = depth,
            className = type,
            text = label ?: value ?: placeholder ?: "",
            // The value (a field's text, a slider's percent) beside its label; a field still empty shows its placeholder.
            contentDescription = (if (label != null) value?.takeIf { it != label } ?: placeholder.takeIf { value.isNullOrEmpty() && editable } else null).orEmpty(),
            resourceId = e.str("AXUniqueId").orEmpty(),
            packageName = "",
            bounds = bounds(e, density),
            clickable = type in CLICKABLE || "button" in traits || "link" in traits || editable,
            longClickable = false,
            scrollable = type in SCROLLABLE || "adjustable" in traits,
            checkable = checkable,
            checked = checkable && (value == "1" || "selected" in traits),
            enabled = e.bool("enabled") ?: ("notenabled" !in traits),
            focused = e.bool("focused") == true,
            selected = "selected" in traits,
            password = type == "SecureTextField",
            editable = editable,
        )
    }

    private fun bounds(
        e: JsonObject,
        density: Double,
    ): Bounds {
        val frame = e["frame"] as? JsonObject
        val (x, y, w, h) =
            if (frame != null) {
                listOf(frame.num("x"), frame.num("y"), frame.num("width"), frame.num("height"))
            } else {
                FRAME_TEXT
                    .find(e.str("AXFrame").orEmpty())
                    ?.groupValues
                    ?.drop(1)
                    ?.map { it.toDouble() } ?: listOf(0.0, 0.0, 0.0, 0.0)
            }
        return Bounds((x * density).roundToInt(), (y * density).roundToInt(), ((x + w) * density).roundToInt(), ((y + h) * density).roundToInt())
    }

    private fun JsonObject.str(name: String): String? =
        this[name]
            ?.takeIf { it !is JsonNull }
            ?.let { v ->
                (v as? JsonPrimitive)?.let { if (it.isString) it.content else it.contentOrNull }
            }?.takeIf { it.isNotBlank() }

    private fun JsonObject.bool(name: String): Boolean? = (this[name] as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.num(name: String): Double = (this[name] as? JsonPrimitive)?.doubleOrNull ?: 0.0
}
