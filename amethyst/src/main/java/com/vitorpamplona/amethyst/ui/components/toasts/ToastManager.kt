/**
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
package com.vitorpamplona.amethyst.ui.components.toasts

import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.components.toasts.multiline.MultiErrorToastMsg
import com.vitorpamplona.amethyst.ui.components.toasts.multiline.UserBasedErrorMessage
import kotlinx.coroutines.flow.MutableStateFlow

class ToastManager {
    val toasts = MutableStateFlow<ToastMsg?>(null)

    fun clearToasts() {
        toasts.tryEmit(null)
    }

    fun toast(
        title: String,
        message: String,
    ) {
        toasts.tryEmit(StringToastMsg(title, message))
    }

    fun toast(
        title: String,
        message: String,
        action: () -> Unit,
    ) {
        toasts.tryEmit(ActionableStringToastMsg(title, message, action))
    }

    fun toast(
        titleResId: Int,
        resourceId: Int,
    ) {
        toasts.tryEmit(ResourceToastMsg(titleResId, resourceId))
    }

    fun toast(
        titleResId: Int,
        message: String?,
        throwable: Throwable,
    ) {
        toasts.tryEmit(ThrowableToastMsg(titleResId, message, throwable))
    }

    fun toast(
        titleResId: Int,
        resourceId: Int,
        vararg params: String,
    ) {
        toasts.tryEmit(ResourceToastMsg(titleResId, resourceId, params))
    }

    fun toast(
        titleResId: Int,
        message: String,
        user: User?,
    ) {
        val current = toasts.value
        if (current is MultiErrorToastMsg && current.titleResId == titleResId) {
            current.add(message, user)
        } else {
            toasts.tryEmit(MultiErrorToastMsg(titleResId).also { it.add(message, user) })
        }
    }

    fun toast(
        titleResId: Int,
        data: UserBasedErrorMessage,
    ) {
        val current = toasts.value
        if (current is MultiErrorToastMsg && current.titleResId == titleResId) {
            current.add(data)
        } else {
            toasts.tryEmit(MultiErrorToastMsg(titleResId).also { it.add(data) })
        }
    }
}
