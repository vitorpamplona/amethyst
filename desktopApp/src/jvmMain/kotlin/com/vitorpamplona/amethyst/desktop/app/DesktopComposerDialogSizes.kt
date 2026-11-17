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
import com.vitorpamplona.amethyst.commons.ui.navigation.host.ComposerDialogSizes
import com.vitorpamplona.amethyst.commons.ui.navigation.host.ComposerSize

/**
 * The composer dialog sizes the user dragged to, one per [ComposerSize], kept between runs in a
 * file of their own by the same store as the screen windows' sizes.
 */
class DesktopComposerDialogSizes(
    private val sizes: ScreenWindowSizes,
) : ComposerDialogSizes {
    override fun load(size: ComposerSize): DpSize? = sizes.saved(size.name)

    override fun save(
        size: ComposerSize,
        picked: DpSize?,
    ) {
        if (picked == null) sizes.forget(size.name) else sizes.save(size.name, picked)
    }
}
