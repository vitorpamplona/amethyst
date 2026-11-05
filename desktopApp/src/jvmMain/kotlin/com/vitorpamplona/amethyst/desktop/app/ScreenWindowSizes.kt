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
package com.vitorpamplona.amethyst.desktop.app

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.vitorpamplona.quartz.utils.Log
import java.io.File
import java.util.Properties

/**
 * The size each kind of screen window (a route's name: "Note", "Profile", "NewShortNote") was last
 * left at, so the next one opens the same. Before the user has sized one, composers open small,
 * the browser and video feeds large, and everything else as a reading column.
 */
class ScreenWindowSizes(
    private val file: File,
) {
    private val sizes: Properties by lazy {
        Properties().apply {
            if (file.exists()) {
                runCatching { file.inputStream().use { load(it) } }.onFailure { Log.w("ScreenWindowSizes", "Could not read $file", it) }
            }
        }
    }

    @Synchronized
    fun sizeFor(kind: String): DpSize {
        val saved =
            sizes
                .getProperty(kind)
                ?.split(',')
                ?.mapNotNull { it.toFloatOrNull() }
                ?.takeIf { it.size == 2 && it.all { side -> side >= MIN_SIDE } }
        return if (saved != null) DpSize(saved[0].dp, saved[1].dp) else defaultFor(kind)
    }

    @Synchronized
    fun save(
        kind: String,
        size: DpSize,
    ) {
        if (!size.width.value.isFinite() || !size.height.value.isFinite()) return
        sizes.setProperty(kind, "${size.width.value.toInt()},${size.height.value.toInt()}")
        runCatching { file.outputStream().use { sizes.store(it, null) } }.onFailure { Log.w("ScreenWindowSizes", "Could not write $file", it) }
    }

    private fun defaultFor(kind: String): DpSize =
        when {
            kind in WIDE_KINDS -> DpSize(1080.dp, 800.dp)
            kind.startsWith("New") || kind.endsWith("Post") || kind.contains("Edit") -> DpSize(520.dp, 680.dp)
            else -> DpSize(560.dp, 860.dp)
        }

    private companion object {
        const val MIN_SIDE = 200f
        val WIDE_KINDS = setOf("Browser", "WebApp", "NostrApp", "Video", "Shorts", "Pictures", "LiveStreams")
    }
}
