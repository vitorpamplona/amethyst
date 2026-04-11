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
package com.vitorpamplona.amethyst.ui.tor

import com.vitorpamplona.amethyst.R

// Re-export commons types for backward compatibility
typealias TorSettings = com.vitorpamplona.amethyst.commons.tor.TorSettings
typealias TorType = com.vitorpamplona.amethyst.commons.tor.TorType
typealias TorPresetType = com.vitorpamplona.amethyst.commons.tor.TorPresetType
typealias TorDialogViewModel = com.vitorpamplona.amethyst.commons.tor.TorDialogViewModel

// Re-export top-level functions and vals
val torOnlyWhenNeededPreset get() = com.vitorpamplona.amethyst.commons.tor.torOnlyWhenNeededPreset
val torDefaultPreset get() = com.vitorpamplona.amethyst.commons.tor.torDefaultPreset
val torSmallPayloadsPreset get() = com.vitorpamplona.amethyst.commons.tor.torSmallPayloadsPreset
val torFullyPrivate get() = com.vitorpamplona.amethyst.commons.tor.torFullyPrivate

fun parseTorType(code: Int?): com.vitorpamplona.amethyst.commons.tor.TorType =
    com.vitorpamplona.amethyst.commons.tor
        .parseTorType(code)

fun parseTorPresetType(code: Int?): com.vitorpamplona.amethyst.commons.tor.TorPresetType =
    com.vitorpamplona.amethyst.commons.tor
        .parseTorPresetType(code)

fun whichPreset(torSettings: TorSettings): com.vitorpamplona.amethyst.commons.tor.TorPresetType =
    com.vitorpamplona.amethyst.commons.tor
        .whichPreset(torSettings)

fun isPreset(
    torSettings: TorSettings,
    preset: TorSettings,
): Boolean =
    com.vitorpamplona.amethyst.commons.tor
        .isPreset(torSettings, preset)

// Android resource mappings — kept in the app module since R is Android-only
val TorType.resourceId: Int
    get() =
        when (this) {
            TorType.OFF -> R.string.tor_off
            TorType.INTERNAL -> R.string.tor_internal
            TorType.EXTERNAL -> R.string.tor_external
        }

val TorPresetType.resourceId: Int
    get() =
        when (this) {
            TorPresetType.ONLY_WHEN_NEEDED -> R.string.tor_when_needed
            TorPresetType.DEFAULT -> R.string.tor_default
            TorPresetType.SMALL_PAYLOADS -> R.string.tor_small_payloads
            TorPresetType.FULL_PRIVACY -> R.string.tor_full_privacy
            TorPresetType.CUSTOM -> R.string.tor_custom
        }

val TorPresetType.explainerId: Int
    get() =
        when (this) {
            TorPresetType.ONLY_WHEN_NEEDED -> R.string.tor_when_needed_explainer
            TorPresetType.DEFAULT -> R.string.tor_default_explainer
            TorPresetType.SMALL_PAYLOADS -> R.string.tor_small_payloads_explainer
            TorPresetType.FULL_PRIVACY -> R.string.tor_full_privacy_explainer
            TorPresetType.CUSTOM -> R.string.tor_custom_explainer
        }
