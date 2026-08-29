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
package com.google.accompanist.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * JVM stand-ins for Accompanist's permission composables.
 *
 * Desktop has no runtime permission model — a program either can reach the
 * camera or microphone or it cannot, and the OS asks the user directly at the
 * moment of use rather than through the app. So permission state reports
 * granted and requesting is a no-op; the screens that gate on it show their
 * content, and any actual denial surfaces where the device is opened.
 */
@RequiresOptIn(message = "Accompanist's permissions API is experimental.")
annotation class ExperimentalPermissionsApi

sealed interface PermissionStatus {
    object Granted : PermissionStatus

    data class Denied(
        val shouldShowRationale: Boolean,
    ) : PermissionStatus
}

val PermissionStatus.isGranted: Boolean
    get() = this is PermissionStatus.Granted

val PermissionStatus.shouldShowRationale: Boolean
    get() = (this as? PermissionStatus.Denied)?.shouldShowRationale == true

interface PermissionState {
    val permission: String
    val status: PermissionStatus

    fun launchPermissionRequest()
}

@Composable
fun rememberPermissionState(
    permission: String,
    onPermissionResult: (Boolean) -> Unit = {},
): PermissionState =
    remember(permission) {
        object : PermissionState {
            override val permission = permission
            override val status: PermissionStatus = PermissionStatus.Granted

            override fun launchPermissionRequest() = onPermissionResult(true)
        }
    }
