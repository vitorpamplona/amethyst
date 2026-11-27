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
package com.vitorpamplona.amethyst.commons.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.darwin.NSObject

/**
 * "While using the app" location, which iOS asks for once (then only Settings can change it). The
 * Info.plist defaults it to approximate (NSLocationDefaultAccuracyReduced), as Android asks only
 * for coarse location: a 5 km geohash is all the app reads.
 */
private class IosLocationPermission : PlatformPermission {
    private val manager = CLLocationManager()

    override var isGranted: Boolean by mutableStateOf(isAuthorized())
        private set

    // CLLocationManager holds its delegate weakly; this class keeps it.
    private val delegate =
        object : NSObject(), CLLocationManagerDelegateProtocol {
            override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
                isGranted = isAuthorized()
            }
        }

    init {
        manager.delegate = delegate
    }

    private fun isAuthorized(): Boolean {
        val status = manager.authorizationStatus
        return status == kCLAuthorizationStatusAuthorizedWhenInUse || status == kCLAuthorizationStatusAuthorizedAlways
    }

    override fun launchRequest() = manager.requestWhenInUseAuthorization()

    fun release() {
        manager.delegate = null
    }
}

@Composable
actual fun rememberCoarseLocationPermission(): PlatformPermission {
    val permission = remember { IosLocationPermission() }
    DisposableEffect(permission) { onDispose { permission.release() } }
    return permission
}
