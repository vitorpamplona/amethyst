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
package com.vitorpamplona.amethyst.commons.util

import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.utils.Log

/**
 * Runs [action] and returns its result. When the signer refuses (the user rejected the request in
 * their signer app, it timed out, the key is read-only, or the signer is gone), it logs and returns
 * null instead of throwing: a rejection is the user's answer, and uncaught in a `viewModelScope`
 * launch it would crash the app.
 *
 * For code that holds only an `Account`. A ViewModel that has a `BaseAccountViewModel` uses its
 * `launchSigner` / `reportSignerErrors` instead, which also tell the user about read-only keys
 * and missing signer apps.
 */
suspend inline fun <T> logSignerErrors(
    tag: String,
    action: () -> T,
): T? =
    try {
        action()
    } catch (e: SignerExceptions) {
        Log.w(tag, "Signer refused", e)
        null
    }
