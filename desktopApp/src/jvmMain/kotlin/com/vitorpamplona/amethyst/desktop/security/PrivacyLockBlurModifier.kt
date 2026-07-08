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
package com.vitorpamplona.amethyst.desktop.security

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

/**
 * Blur the modified node when the privacy lock is enabled AND the desktop
 * window is currently unfocused.
 *
 * Per plan Q4: applies only to sensitive text nodes (balance amount, invoice
 * strings, addresses, NWC URIs, transaction memos) — NOT to card
 * containers, icons, or layout structure. This preserves the visual
 * skeleton for a passer-by while hiding the meaningful values.
 *
 * Uses Compose Desktop's built-in [LocalWindowInfo.isWindowFocused] — no
 * Swing WindowListener plumbing required.
 */
@Composable
fun Modifier.privacyLockBlurWhenUnfocused(): Modifier {
    val settings = LocalPrivacyLockSettings.current
    val enabled by settings.lockEnabled.collectAsState()
    val focused = LocalWindowInfo.current.isWindowFocused
    return if (enabled && !focused) this.then(Modifier.blur(16.dp)) else this
}
