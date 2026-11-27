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
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.biometric_authentication_failed
import com.vitorpamplona.amethyst.commons.resources.biometric_authentication_failed_explainer_with_error
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.withArgs
import kotlinx.cinterop.ExperimentalForeignApi
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAErrorAppCancel
import platform.LocalAuthentication.LAErrorSystemCancel
import platform.LocalAuthentication.LAErrorUserCancel
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Face ID, Touch ID or the device passcode, as Android's BiometricPrompt with its device-credential
 * fallback: [LAPolicyDeviceOwnerAuthentication] offers the biometric first and the passcode after it
 * fails or locks out, so no separate keyguard step is needed.
 */
internal object IosDeviceOwner {
    /** True when the device has a passcode (and so a biometric or passcode prompt to show). */
    @OptIn(ExperimentalForeignApi::class)
    fun isSecure(): Boolean = LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication, error = null)

    /**
     * Shows the system prompt with [reason] under the app's name. [onResult] runs on the main queue:
     * true when the owner passed, false with the error's code and message otherwise.
     */
    fun authenticate(
        reason: String,
        onResult: (approved: Boolean, errorCode: Long, message: String?) -> Unit,
    ) {
        // A fresh context per prompt: a reused one would pass at once while its last success holds.
        LAContext().evaluatePolicy(LAPolicyDeviceOwnerAuthentication, localizedReason = reason) { success, error ->
            dispatch_async(dispatch_get_main_queue()) {
                onResult(success, error?.code ?: 0L, error?.localizedDescription)
            }
        }
    }

    /** The user, the system or the app dismissed the prompt: no error to show. */
    fun isCancel(code: Long): Boolean = code == LAErrorUserCancel || code == LAErrorSystemCancel || code == LAErrorAppCancel
}

/**
 * Asks for Face ID, Touch ID or the passcode before a sensitive action. A device without a passcode
 * approves at once, as Android does without a screen lock: there is nothing to prove.
 */
@Composable
actual fun rememberDeviceAuthenticator(): DeviceAuthenticator {
    val failedTitle = stringRes(Res.string.biometric_authentication_failed)
    val failedWithError = stringRes(Res.string.biometric_authentication_failed_explainer_with_error)
    return remember(failedTitle, failedWithError) {
        DeviceAuthenticator { title, onApproved, onError ->
            if (!IosDeviceOwner.isSecure()) {
                onApproved()
            } else {
                IosDeviceOwner.authenticate(title) { approved, code, message ->
                    when {
                        approved -> onApproved()
                        IosDeviceOwner.isCancel(code) -> Unit
                        else -> onError(failedTitle, failedWithError.withArgs(listOf(message ?: code.toString())))
                    }
                }
            }
        }
    }
}
