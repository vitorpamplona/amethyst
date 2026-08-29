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
package com.vitorpamplona.amethyst.shared.platform

import com.vitorpamplona.amethyst.stubs.PlatformGaps

/**
 * What this platform can and cannot do, declared up front.
 *
 * Two different statements live here, and keeping them apart is the point.
 * Most of what Android offers has a desktop counterpart and simply has not been
 * written yet — that is a backlog item, and it is NOT declared here; it shows
 * up through `PlatformGaps.report` when the code path is first hit.
 *
 * What IS declared here is the other kind: features with no desktop counterpart
 * at all. Those are not defects and not TODOs. A UI should ask
 * [PlatformGaps.isUnavailable] and hide the control rather than offer a button
 * that cannot work, and the entry stands as the documentation of why. If a
 * desktop equivalent ever appears, the entry is deleted and the feature becomes
 * ordinary work.
 */
object DesktopCapabilities {
    /** Feature keys, so the UI and the declaration cannot drift apart. */
    object Feature {
        const val HEALTH_CONNECT = "HealthConnect"
        const val PICTURE_IN_PICTURE = "PictureInPicture"
        const val FOREGROUND_SERVICE = "ForegroundService"
        const val EXTERNAL_SIGNER = "ExternalSigner"
        const val GOOGLE_CAST = "GoogleCast"
        const val UNIFIED_PUSH = "UnifiedPush"
        const val APP_LOCALE_SETTINGS = "AppLocaleSettings"
        const val SHARE_SHEET = "ShareSheet"
        const val BATTERY_OPTIMIZATION = "BatteryOptimization"
    }

    fun declare() {
        PlatformGaps.declareUnavailable(
            Feature.HEALTH_CONNECT,
            "Health Connect is an Android system service for on-device health records. " +
                "Desktop has no equivalent store, so workout import has nothing to read from.",
        )
        PlatformGaps.declareUnavailable(
            Feature.PICTURE_IN_PICTURE,
            "Android PiP docks an Activity into a system overlay. A desktop equivalent would be " +
                "an always-on-top window, which is a different feature with different UX, not a port of this one.",
        )
        PlatformGaps.declareUnavailable(
            Feature.FOREGROUND_SERVICE,
            "Desktop processes are not killed for being backgrounded, so there is nothing to " +
                "keep alive and no notification to justify it. The work these services do belongs " +
                "to ordinary long-lived objects the app owns.",
        )
        PlatformGaps.declareUnavailable(
            Feature.EXTERNAL_SIGNER,
            "NIP-55 signing hands an Intent to a separate signer app. Desktop has no app-to-app " +
                "intent bus; remote signing there goes over NIP-46 instead, which already works.",
        )
        PlatformGaps.declareUnavailable(
            Feature.GOOGLE_CAST,
            "Cast discovery and session management ship in Google Play services.",
        )
        PlatformGaps.declareUnavailable(
            Feature.UNIFIED_PUSH,
            "UnifiedPush distributors are Android apps. A desktop build holds its own relay " +
                "connections while running, so it needs no push distributor.",
        )
        PlatformGaps.declareUnavailable(
            Feature.APP_LOCALE_SETTINGS,
            "Android exposes a per-app language screen in system settings. Desktop has no such " +
                "screen; the in-app language picker is the whole story.",
        )
        PlatformGaps.declareUnavailable(
            Feature.SHARE_SHEET,
            "No desktop OS has a system share sheet the way Android does. Sharing falls back to " +
                "the clipboard, which is the closest honest equivalent.",
        )
        PlatformGaps.declareUnavailable(
            Feature.BATTERY_OPTIMIZATION,
            "Doze and app standby are Android power-management policies with no desktop analogue, " +
                "so there is no exemption to request.",
        )
    }
}
