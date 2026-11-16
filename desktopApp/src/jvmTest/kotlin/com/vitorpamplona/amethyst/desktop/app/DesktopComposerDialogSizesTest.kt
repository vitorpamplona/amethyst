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
import com.vitorpamplona.amethyst.commons.ui.navigation.host.ComposerSize
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopComposerDialogSizesTest {
    private fun tempFile(): File = Files.createTempDirectory("composer-sizes").toFile().resolve("sizes.properties")

    @Test
    fun aPickedSizeSurvivesARestartPerSize() {
        val file = tempFile()
        DesktopComposerDialogSizes(ScreenWindowSizes(file)).save(ComposerSize.QUICK, DpSize(700.dp, 500.dp))

        val reopened = DesktopComposerDialogSizes(ScreenWindowSizes(file))
        assertEquals(DpSize(700.dp, 500.dp), reopened.load(ComposerSize.QUICK))
        assertNull(reopened.load(ComposerSize.FORM))
    }

    @Test
    fun resettingForgetsThePickedSize() {
        val file = tempFile()
        val sizes = DesktopComposerDialogSizes(ScreenWindowSizes(file))
        sizes.save(ComposerSize.FORM, DpSize(900.dp, 700.dp))
        sizes.save(ComposerSize.FORM, null)

        assertNull(DesktopComposerDialogSizes(ScreenWindowSizes(file)).load(ComposerSize.FORM))
    }

    @Test
    fun aDamagedEntryReadsAsTheDefault() {
        val file = tempFile()
        file.writeText("QUICK=abc,12\nFORM=-5,300\n")

        val sizes = DesktopComposerDialogSizes(ScreenWindowSizes(file))
        assertNull(sizes.load(ComposerSize.QUICK))
        assertNull(sizes.load(ComposerSize.FORM))
    }
}
