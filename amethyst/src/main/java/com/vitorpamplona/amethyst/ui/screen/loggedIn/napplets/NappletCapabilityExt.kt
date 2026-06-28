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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.napplets

import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.napplet.NappletCapability

internal fun NappletCapability.symbol(): MaterialSymbol =
    when (this) {
        NappletCapability.SHELL -> MaterialSymbols.Tune
        NappletCapability.IDENTITY -> MaterialSymbols.AccountCircle
        NappletCapability.KEYS -> MaterialSymbols.Key
        NappletCapability.RELAY -> MaterialSymbols.Public
        NappletCapability.STORAGE -> MaterialSymbols.Storage
        NappletCapability.VALUE -> MaterialSymbols.Bolt
        NappletCapability.RESOURCE -> MaterialSymbols.Language
        NappletCapability.UPLOAD -> MaterialSymbols.Upload
        NappletCapability.THEME -> MaterialSymbols.Image
        NappletCapability.NOTIFY -> MaterialSymbols.Notifications
        NappletCapability.INC -> MaterialSymbols.SwapHoriz
    }
