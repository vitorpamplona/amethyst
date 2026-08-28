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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import com.vitorpamplona.amethyst.R
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JvmDrawablesTest {
    /**
     * The Compose compiler plugin adds a synthetic `$stable: Int` to every
     * object it touches, so a raw `declaredFields` scan of `R` over-counts by
     * one per nested object. Resource fields are the non-synthetic ints.
     */
    private fun resourceFields(type: Class<*>) =
        type.declaredFields.filter {
            it.type == Int::class.javaPrimitiveType && !it.isSynthetic && !it.name.startsWith("$")
        }

    private fun paths(node: VectorNode): List<VectorPath> =
        when (node) {
            is VectorPath -> listOf(node)
            is VectorGroup -> node.flatMap { paths(it) }
            else -> emptyList()
        }

    @Test
    fun `every vector drawable in the res tree parses into a non-empty ImageVector`() {
        val fields = resourceFields(R.drawable::class.java)
        assertEquals(39, fields.size)

        val vectors =
            fields.mapNotNull { field ->
                field.isAccessible = true
                val id = field.getInt(null)
                JvmDrawables.imageVectorFor(id)?.let { field.name to it }
            }

        // 30 of the 39 drawables are VectorDrawable XML; the other 9 are rasters.
        assertEquals(30, vectors.size, "parsed: ${vectors.map { it.first }}")

        val empty = vectors.filter { (_, image) -> image.root.none { node -> paths(node).isNotEmpty() } }
        assertTrue(empty.isEmpty(), "produced no paths: ${empty.map { it.first }}")
    }

    @Test
    fun `carries the declared viewport and default size`() {
        // alby.xml: <vector android:width="489dp" android:viewportWidth="489" .../>
        val image = assertNotNull(JvmDrawables.imageVectorFor(R.drawable.alby))
        assertEquals(489f, image.viewportWidth)
        assertEquals(489f, image.viewportHeight)
        assertEquals(489f, image.defaultWidth.value)
    }

    @Test
    fun `reads solid fills and the evenOdd fill type`() {
        val image = assertNotNull(JvmDrawables.imageVectorFor(R.drawable.alby))
        val all = image.root.flatMap { paths(it) }
        val fills = all.mapNotNull { (it.fill as? SolidColor)?.value }
        assertTrue(Color(0xFFFFDF6F) in fills, "expected the #FFDF6F fill, got ${fills.distinct()}")
        assertTrue(all.any { it.pathFillType == PathFillType.EvenOdd })
        assertTrue(all.any { it.pathFillType == PathFillType.NonZero })
    }

    @Test
    fun `reads a gradient fill nested under an aapt attr element`() {
        // amethyst.xml expresses fillColor as <aapt:attr><gradient><item .../>
        val image = assertNotNull(JvmDrawables.imageVectorFor(R.drawable.amethyst))
        val brushes = image.root.flatMap { paths(it) }.mapNotNull { it.fill }
        assertTrue(
            brushes.any { it !is SolidColor },
            "expected at least one gradient brush among ${brushes.size} fills",
        )
    }

    @Test
    fun `applies group transforms`() {
        // ic_notif_zap.xml wraps its paths in a <group> with translate/scale.
        val image = assertNotNull(JvmDrawables.imageVectorFor(R.drawable.ic_notif_zap))
        val groups = image.root.filterIsInstance<VectorGroup>()
        assertTrue(groups.isNotEmpty(), "expected a nested <group>")
        assertTrue(
            groups.any { it.scaleX != 1f || it.scaleY != 1f || it.translationX != 0f || it.translationY != 0f },
            "expected a group carrying a transform",
        )
    }

    @Test
    fun `parses the aapt color formats`() {
        assertEquals(Color(0xFFFFFFFF), VectorDrawableParser.parseColor("#ffffff"))
        assertEquals(Color(0xFF000000), VectorDrawableParser.parseColor("#000"))
        assertEquals(Color(0x80FF0000), VectorDrawableParser.parseColor("#80FF0000"))
        assertEquals(Color(0xFF652D80), VectorDrawableParser.parseColor("#FF652D80"))
        assertEquals(null, VectorDrawableParser.parseColor("@color/primary"))
    }

    @Test
    fun `night variants override the default when dark theme is on`() {
        // nip_05 is the one drawable with a drawable-night variant.
        try {
            JvmDrawables.useNightVariants = false
            val day = assertNotNull(JvmDrawables.imageVectorFor(R.drawable.nip_05))
            JvmDrawables.useNightVariants = true
            val night = assertNotNull(JvmDrawables.imageVectorFor(R.drawable.nip_05))
            val dayFills = day.root.flatMap { paths(it) }.mapNotNull { (it.fill as? SolidColor)?.value }
            val nightFills = night.root.flatMap { paths(it) }.mapNotNull { (it.fill as? SolidColor)?.value }
            assertTrue(dayFills != nightFills, "night variant should differ; both were $dayFills")
        } finally {
            JvmDrawables.useNightVariants = false
        }
    }
}
