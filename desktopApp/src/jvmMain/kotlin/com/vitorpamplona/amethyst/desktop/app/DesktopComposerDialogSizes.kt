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
import com.vitorpamplona.amethyst.commons.ui.navigation.host.ComposerDialogSizes
import com.vitorpamplona.amethyst.commons.ui.navigation.host.ComposerSize
import com.vitorpamplona.quartz.utils.Log
import java.io.File
import java.util.Properties

/** The composer dialog sizes the user dragged to, one per [ComposerSize], kept in [file] between runs. */
class DesktopComposerDialogSizes(
    private val file: File,
) : ComposerDialogSizes {
    private val sizes: Properties by lazy {
        Properties().apply {
            if (file.exists()) {
                runCatching { file.inputStream().use { load(it) } }.onFailure { Log.w(TAG, "Could not read $file", it) }
            }
        }
    }

    @Synchronized
    override fun load(size: ComposerSize): DpSize? =
        sizes
            .getProperty(size.name)
            ?.split(',')
            ?.mapNotNull { it.toFloatOrNull() }
            ?.takeIf { it.size == 2 && it.all { side -> side.isFinite() && side > 0f } }
            ?.let { DpSize(it[0].dp, it[1].dp) }

    @Synchronized
    override fun save(
        size: ComposerSize,
        picked: DpSize?,
    ) {
        if (picked == null) {
            if (sizes.remove(size.name) == null) return
        } else {
            if (!picked.width.value.isFinite() || !picked.height.value.isFinite()) return
            sizes.setProperty(size.name, "${picked.width.value.toInt()},${picked.height.value.toInt()}")
        }
        runCatching { file.outputStream().use { sizes.store(it, null) } }.onFailure { Log.w(TAG, "Could not write $file", it) }
    }

    private companion object {
        const val TAG = "ComposerDialogSizes"
    }
}
