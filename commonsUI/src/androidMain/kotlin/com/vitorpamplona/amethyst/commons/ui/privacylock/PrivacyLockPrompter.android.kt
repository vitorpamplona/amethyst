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
package com.vitorpamplona.amethyst.commons.ui.privacylock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.privacylock.PrivacyLockSettings
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_prompt_title
import com.vitorpamplona.amethyst.commons.ui.stringRes
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Unlocks with BiometricPrompt (strong biometrics or the device credential), through [rememberDeviceAuthenticator]. */
@Composable
actual fun rememberPrivacyLockPrompter(settings: PrivacyLockSettings): CredentialPrompter {
    val authenticator = rememberDeviceAuthenticator()
    val title = stringRes(Res.string.privacy_lock_prompt_title)
    return remember(authenticator, title) {
        object : CredentialPrompter {
            override val available: Boolean = true

            override suspend fun prompt(): PromptResult =
                suspendCancellableCoroutine { continuation ->
                    authenticator.authenticate(
                        title = title,
                        onApproved = { if (continuation.isActive) continuation.resume(PromptResult.Success) },
                        onError = { _, _ -> if (continuation.isActive) continuation.resume(PromptResult.Failed) },
                    )
                }
        }
    }
}
