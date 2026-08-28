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

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.jetbrains.skia.Image
import java.util.concurrent.ConcurrentHashMap

/**
 * The JVM counterpart to `painterResource(R.drawable.x)`.
 *
 * Drawables are copied next to the string tables by
 * `generateAndroidResourceTable`, which resolves Android's qualifier rules at
 * build time: vectors win over rasters, and among rasters the densest bucket
 * wins because the JVM ships one artifact and scales at draw time.
 */
object JvmDrawables {
    private const val TABLE_DIR = "amethyst-res"

    private class Entry(
        val default: String?,
        val night: String?,
    )

    private val index: Map<Int, Entry> by lazy { loadIndex() }
    private val vectorCache = ConcurrentHashMap<String, ImageVector>()
    private val bitmapCache = ConcurrentHashMap<String, Painter>()

    /** Set from the theme so `-night` drawable variants resolve like Android's. */
    @Volatile
    var useNightVariants: Boolean = false

    @Composable
    fun painterFor(id: Int): Painter {
        val fileName = fileNameFor(id) ?: return MissingPainter
        return if (fileName.endsWith(".xml")) {
            rememberVectorPainter(vectorCache.getOrPut(fileName) { loadVector(fileName) })
        } else {
            bitmapCache.getOrPut(fileName) { loadBitmap(fileName) }
        }
    }

    fun imageVectorFor(id: Int): ImageVector? =
        fileNameFor(id)
            ?.takeIf { it.endsWith(".xml") }
            ?.let { vectorCache.getOrPut(it) { loadVector(it) } }

    private fun fileNameFor(id: Int): String? {
        val entry = index[id] ?: return null
        return if (useNightVariants) entry.night ?: entry.default else entry.default
    }

    private fun loadVector(fileName: String): ImageVector =
        openResource("$TABLE_DIR/drawable/$fileName").use {
            VectorDrawableParser.parse(it, fileName.substringBeforeLast('.'))
        }

    private fun loadBitmap(fileName: String): Painter {
        val bytes = openResource("$TABLE_DIR/drawable/$fileName").use { it.readBytes() }
        return BitmapPainter(Image.makeFromEncoded(bytes).toComposeImageBitmap())
    }

    private fun openResource(path: String) =
        JvmDrawables::class.java.classLoader
            ?.getResourceAsStream(path)
            ?: error("drawable resource not on the classpath: $path")

    private fun loadIndex(): Map<Int, Entry> {
        val text =
            JvmDrawables::class.java.classLoader
                ?.getResourceAsStream("$TABLE_DIR/drawables.tsv")
                ?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: return emptyMap()

        val defaults = HashMap<Int, String>()
        val nights = HashMap<Int, String>()
        text.lineSequence().forEach { line ->
            if (line.isEmpty()) return@forEach
            val parts = line.split('\t')
            if (parts.size < 3) return@forEach
            val id = parts[0].toIntOrNull() ?: return@forEach
            if (parts[1] == "night") nights[id] = parts[2] else defaults[id] = parts[2]
        }
        return (defaults.keys + nights.keys).associateWith { Entry(defaults[it], nights[it]) }
    }

    /**
     * A drawable id with no file behind it draws nothing rather than throwing:
     * an icon missing from one platform must not take a whole screen down.
     */
    private val MissingPainter = ColorPainter(Color.Transparent)
}
