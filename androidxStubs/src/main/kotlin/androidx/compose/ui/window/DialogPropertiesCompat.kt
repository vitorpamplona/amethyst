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
@file:Suppress("ktlint:standard:function-naming")

package androidx.compose.ui.window

/**
 * Accepts Android's `decorFitsSystemWindows` on the multiplatform
 * `DialogProperties`, which has no such parameter.
 *
 * The flag controls whether a dialog's *window* is inset from the status and
 * navigation bars — an Android windowing concern with no desktop counterpart, so
 * the value is genuinely nothing to act on here. What matters is not forcing the
 * seven call sites that set it to drop it, which would change how those dialogs
 * lay out on Android, where the flag is load-bearing for edge-to-edge and IME
 * insets.
 *
 * `decorFitsSystemWindows` deliberately has no default: a call that passes it
 * matches only this function, and a call that omits it matches only the real
 * constructor, so neither is ever ambiguous — including the call below.
 */
fun DialogProperties(
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    usePlatformDefaultWidth: Boolean = true,
    decorFitsSystemWindows: Boolean,
): DialogProperties =
    DialogProperties(
        dismissOnBackPress = dismissOnBackPress,
        dismissOnClickOutside = dismissOnClickOutside,
        usePlatformDefaultWidth = usePlatformDefaultWidth,
    )
