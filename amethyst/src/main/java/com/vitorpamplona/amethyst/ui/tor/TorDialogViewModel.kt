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

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

@Stable
class TorDialogViewModel : ViewModel() {
    val torType = mutableStateOf(TorType.INTERNAL)
    val socksPortStr = mutableStateOf("9050")

    // usage types
    val onionRelaysViaTor = mutableStateOf(true)
    val dmRelaysViaTor = mutableStateOf(true)
    val newRelaysViaTor = mutableStateOf(true)

    val trustedRelaysViaTor = mutableStateOf(false)
    val urlPreviewsViaTor = mutableStateOf(false)
    val profilePicsViaTor = mutableStateOf(false)
    val imagesViaTor = mutableStateOf(false)
    val videosViaTor = mutableStateOf(false)
    val moneyOperationsViaTor = mutableStateOf(false)
    val nip05VerificationsViaTor = mutableStateOf(false)
    val mediaUploadsViaTor = mutableStateOf(false)

    val preset =
        derivedStateOf {
            whichPreset(
                TorSettings(
                    torType = TorType.INTERNAL,
                    externalSocksPort = -1,
                    onionRelaysViaTor = onionRelaysViaTor.value,
                    dmRelaysViaTor = dmRelaysViaTor.value,
                    newRelaysViaTor = newRelaysViaTor.value,
                    trustedRelaysViaTor = trustedRelaysViaTor.value,
                    urlPreviewsViaTor = urlPreviewsViaTor.value,
                    profilePicsViaTor = profilePicsViaTor.value,
                    imagesViaTor = imagesViaTor.value,
                    videosViaTor = videosViaTor.value,
                    moneyOperationsViaTor = moneyOperationsViaTor.value,
                    nip05VerificationsViaTor = nip05VerificationsViaTor.value,
                    mediaUploadsViaTor = mediaUploadsViaTor.value,
                ),
            )
        }

    fun reset(torSettings: TorSettings) {
        torType.value = torSettings.torType
        socksPortStr.value = torSettings.externalSocksPort.toString()
        onionRelaysViaTor.value = torSettings.onionRelaysViaTor
        dmRelaysViaTor.value = torSettings.dmRelaysViaTor
        newRelaysViaTor.value = torSettings.newRelaysViaTor
        trustedRelaysViaTor.value = torSettings.trustedRelaysViaTor
        urlPreviewsViaTor.value = torSettings.urlPreviewsViaTor
        profilePicsViaTor.value = torSettings.profilePicsViaTor
        imagesViaTor.value = torSettings.imagesViaTor
        videosViaTor.value = torSettings.videosViaTor
        moneyOperationsViaTor.value = torSettings.moneyOperationsViaTor
        nip05VerificationsViaTor.value = torSettings.nip05VerificationsViaTor
        mediaUploadsViaTor.value = torSettings.mediaUploadsViaTor
    }

    fun save(): TorSettings =
        TorSettings(
            torType = torType.value,
            externalSocksPort = Integer.parseInt(socksPortStr.value),
            onionRelaysViaTor = onionRelaysViaTor.value,
            dmRelaysViaTor = dmRelaysViaTor.value,
            newRelaysViaTor = newRelaysViaTor.value,
            trustedRelaysViaTor = trustedRelaysViaTor.value,
            urlPreviewsViaTor = urlPreviewsViaTor.value,
            profilePicsViaTor = profilePicsViaTor.value,
            imagesViaTor = imagesViaTor.value,
            videosViaTor = videosViaTor.value,
            moneyOperationsViaTor = moneyOperationsViaTor.value,
            nip05VerificationsViaTor = nip05VerificationsViaTor.value,
            mediaUploadsViaTor = mediaUploadsViaTor.value,
        )

    fun setPreset(preset: TorPresetType) {
        when (preset) {
            TorPresetType.DEFAULT -> resetOnlyFlags(torDefaultPreset)
            TorPresetType.ONLY_WHEN_NEEDED -> resetOnlyFlags(torOnlyWhenNeededPreset)
            TorPresetType.SMALL_PAYLOADS -> resetOnlyFlags(torSmallPayloadsPreset)
            TorPresetType.FULL_PRIVACY -> resetOnlyFlags(torFullyPrivate)
            TorPresetType.CUSTOM -> Unit
        }
    }

    fun resetOnlyFlags(torSettings: TorSettings) {
        onionRelaysViaTor.value = torSettings.onionRelaysViaTor
        dmRelaysViaTor.value = torSettings.dmRelaysViaTor
        newRelaysViaTor.value = torSettings.newRelaysViaTor
        trustedRelaysViaTor.value = torSettings.trustedRelaysViaTor
        urlPreviewsViaTor.value = torSettings.urlPreviewsViaTor
        profilePicsViaTor.value = torSettings.profilePicsViaTor
        imagesViaTor.value = torSettings.imagesViaTor
        videosViaTor.value = torSettings.videosViaTor
        moneyOperationsViaTor.value = torSettings.moneyOperationsViaTor
        nip05VerificationsViaTor.value = torSettings.nip05VerificationsViaTor
        mediaUploadsViaTor.value = torSettings.mediaUploadsViaTor
    }
}
