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

data class TorSettings(
    val torType: TorType = TorType.INTERNAL,
    val externalSocksPort: Int = 9050,
    val onionRelaysViaTor: Boolean = true,
    val dmRelaysViaTor: Boolean = true,
    val newRelaysViaTor: Boolean = true,
    val trustedRelaysViaTor: Boolean = false,
    val urlPreviewsViaTor: Boolean = false,
    val profilePicsViaTor: Boolean = false,
    val imagesViaTor: Boolean = false,
    val videosViaTor: Boolean = false,
    val moneyOperationsViaTor: Boolean = false,
    val nip05VerificationsViaTor: Boolean = false,
    val mediaUploadsViaTor: Boolean = false,
)

enum class TorType(
    val screenCode: Int,
    val resourceId: Int,
) {
    OFF(0, R.string.tor_off),
    INTERNAL(1, R.string.tor_internal),
    EXTERNAL(2, R.string.tor_external),
}

fun parseTorType(code: Int?): TorType =
    when (code) {
        TorType.OFF.screenCode -> TorType.OFF
        TorType.INTERNAL.screenCode -> TorType.INTERNAL
        TorType.EXTERNAL.screenCode -> TorType.EXTERNAL
        else -> TorType.INTERNAL
    }

enum class TorPresetType(
    val screenCode: Int,
    val resourceId: Int,
    val explainerId: Int,
) {
    ONLY_WHEN_NEEDED(0, R.string.tor_when_needed, R.string.tor_when_needed_explainer),
    DEFAULT(1, R.string.tor_default, R.string.tor_default_explainer),
    SMALL_PAYLOADS(2, R.string.tor_small_payloads, R.string.tor_small_payloads_explainer),
    FULL_PRIVACY(3, R.string.tor_full_privacy, R.string.tor_full_privacy_explainer),
    CUSTOM(4, R.string.tor_custom, R.string.tor_custom_explainer),
}

fun parseTorPresetType(code: Int?): TorPresetType =
    when (code) {
        TorPresetType.ONLY_WHEN_NEEDED.screenCode -> TorPresetType.ONLY_WHEN_NEEDED
        TorPresetType.DEFAULT.screenCode -> TorPresetType.DEFAULT
        TorPresetType.SMALL_PAYLOADS.screenCode -> TorPresetType.SMALL_PAYLOADS
        TorPresetType.FULL_PRIVACY.screenCode -> TorPresetType.FULL_PRIVACY
        else -> TorPresetType.CUSTOM
    }

fun isPreset(
    torSettings: TorSettings,
    preset: TorSettings,
): Boolean =
    torSettings.onionRelaysViaTor == preset.onionRelaysViaTor &&
        torSettings.dmRelaysViaTor == preset.dmRelaysViaTor &&
        torSettings.newRelaysViaTor == preset.newRelaysViaTor &&
        torSettings.trustedRelaysViaTor == preset.trustedRelaysViaTor &&
        torSettings.urlPreviewsViaTor == preset.urlPreviewsViaTor &&
        // torSettings.profilePicsViaTor == preset.profilePicsViaTor &&
        torSettings.imagesViaTor == preset.imagesViaTor &&
        torSettings.videosViaTor == preset.videosViaTor &&
        torSettings.moneyOperationsViaTor == preset.moneyOperationsViaTor &&
        torSettings.nip05VerificationsViaTor == preset.nip05VerificationsViaTor &&
        torSettings.mediaUploadsViaTor == preset.mediaUploadsViaTor

fun whichPreset(torSettings: TorSettings): TorPresetType {
    if (isPreset(torSettings, torOnlyWhenNeededPreset)) return TorPresetType.ONLY_WHEN_NEEDED
    if (isPreset(torSettings, torDefaultPreset)) return TorPresetType.DEFAULT
    if (isPreset(torSettings, torSmallPayloadsPreset)) return TorPresetType.SMALL_PAYLOADS
    if (isPreset(torSettings, torFullyPrivate)) return TorPresetType.FULL_PRIVACY
    return TorPresetType.CUSTOM
}

val torOnlyWhenNeededPreset =
    TorSettings(
        onionRelaysViaTor = true,
        dmRelaysViaTor = false,
        newRelaysViaTor = false,
        trustedRelaysViaTor = false,
        urlPreviewsViaTor = false,
        profilePicsViaTor = false,
        imagesViaTor = false,
        videosViaTor = false,
        moneyOperationsViaTor = false,
        nip05VerificationsViaTor = false,
        mediaUploadsViaTor = false,
    )
val torDefaultPreset =
    TorSettings(
        onionRelaysViaTor = true,
        dmRelaysViaTor = true,
        newRelaysViaTor = true,
        trustedRelaysViaTor = false,
        urlPreviewsViaTor = false,
        profilePicsViaTor = false,
        imagesViaTor = false,
        videosViaTor = false,
        moneyOperationsViaTor = false,
        nip05VerificationsViaTor = false,
        mediaUploadsViaTor = false,
    )
val torSmallPayloadsPreset =
    TorSettings(
        onionRelaysViaTor = true,
        dmRelaysViaTor = true,
        newRelaysViaTor = true,
        trustedRelaysViaTor = true,
        urlPreviewsViaTor = true,
        profilePicsViaTor = true,
        imagesViaTor = false,
        videosViaTor = false,
        moneyOperationsViaTor = true,
        nip05VerificationsViaTor = true,
        mediaUploadsViaTor = false,
    )
val torFullyPrivate =
    TorSettings(
        onionRelaysViaTor = true,
        dmRelaysViaTor = true,
        newRelaysViaTor = true,
        trustedRelaysViaTor = true,
        urlPreviewsViaTor = true,
        profilePicsViaTor = true,
        imagesViaTor = true,
        videosViaTor = true,
        moneyOperationsViaTor = true,
        nip05VerificationsViaTor = true,
        mediaUploadsViaTor = true,
    )
