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
package com.vitorpamplona.amethyst.commons.ui.components.toasts

/**
 * Platform-agnostic toast manager interface for KMP.
 *
 * Android implementation uses resource IDs and Android-specific toast types.
 * Other platforms can provide their own notification mechanisms.
 */
interface IToastManager {
    fun clearToasts()

    fun toast(
        title: String,
        message: String,
    )

    fun toast(
        title: String,
        message: String,
        action: () -> Unit,
    )

    fun toast(
        titleResId: Int,
        resourceId: Int,
    )

    fun toast(
        titleResId: Int,
        message: String?,
        throwable: Throwable,
    )

    fun toast(
        titleResId: Int,
        description: Int,
        throwable: Throwable,
    )

    fun toast(
        titleResId: Int,
        resourceId: Int,
        vararg params: String,
    )
}
