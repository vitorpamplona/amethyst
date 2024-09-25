/**
 * Copyright (c) 2024 Vitor Pamplona
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

import kotlinx.coroutines.flow.MutableStateFlow

class TorSettingsFlow(
    val torType: MutableStateFlow<TorType> = MutableStateFlow(TorType.INTERNAL),
    val externalSocksPort: MutableStateFlow<Int> = MutableStateFlow(9050),
    val onionRelaysViaTor: MutableStateFlow<Boolean> = MutableStateFlow(true),
    val dmRelaysViaTor: MutableStateFlow<Boolean> = MutableStateFlow(true),
    val newRelaysViaTor: MutableStateFlow<Boolean> = MutableStateFlow(true),
    val trustedRelaysViaTor: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val urlPreviewsViaTor: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val profilePicsViaTor: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val imagesViaTor: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val videosViaTor: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val moneyOperationsViaTor: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val nip05VerificationsViaTor: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val nip96UploadsViaTor: MutableStateFlow<Boolean> = MutableStateFlow(false),
) {
    fun toSettings(): TorSettings =
        TorSettings(
            torType.value,
            externalSocksPort.value,
            onionRelaysViaTor.value,
            dmRelaysViaTor.value,
            newRelaysViaTor.value,
            trustedRelaysViaTor.value,
            urlPreviewsViaTor.value,
            profilePicsViaTor.value,
            imagesViaTor.value,
            videosViaTor.value,
            moneyOperationsViaTor.value,
            nip05VerificationsViaTor.value,
            nip96UploadsViaTor.value,
        )

    fun update(torSettings: TorSettings): Boolean {
        var any = false

        if (torType.value != torSettings.torType) {
            torType.tryEmit(torSettings.torType)
            any = true
        }
        if (externalSocksPort.value != torSettings.externalSocksPort) {
            externalSocksPort.tryEmit(torSettings.externalSocksPort)
            any = true
        }
        if (onionRelaysViaTor.value != torSettings.onionRelaysViaTor) {
            onionRelaysViaTor.tryEmit(torSettings.onionRelaysViaTor)
            any = true
        }
        if (dmRelaysViaTor.value != torSettings.dmRelaysViaTor) {
            dmRelaysViaTor.tryEmit(torSettings.dmRelaysViaTor)
            any = true
        }
        if (newRelaysViaTor.value != torSettings.newRelaysViaTor) {
            newRelaysViaTor.tryEmit(torSettings.newRelaysViaTor)
            any = true
        }
        if (trustedRelaysViaTor.value != torSettings.trustedRelaysViaTor) {
            trustedRelaysViaTor.tryEmit(torSettings.trustedRelaysViaTor)
            any = true
        }
        if (urlPreviewsViaTor.value != torSettings.urlPreviewsViaTor) {
            urlPreviewsViaTor.tryEmit(torSettings.urlPreviewsViaTor)
            any = true
        }
        if (profilePicsViaTor.value != torSettings.profilePicsViaTor) {
            profilePicsViaTor.tryEmit(torSettings.profilePicsViaTor)
            any = true
        }
        if (imagesViaTor.value != torSettings.imagesViaTor) {
            imagesViaTor.tryEmit(torSettings.imagesViaTor)
            any = true
        }
        if (videosViaTor.value != torSettings.videosViaTor) {
            videosViaTor.tryEmit(torSettings.videosViaTor)
            any = true
        }
        if (moneyOperationsViaTor.value != torSettings.moneyOperationsViaTor) {
            moneyOperationsViaTor.tryEmit(torSettings.moneyOperationsViaTor)
            any = true
        }

        if (nip05VerificationsViaTor.value != torSettings.nip05VerificationsViaTor) {
            nip05VerificationsViaTor.tryEmit(torSettings.nip05VerificationsViaTor)
            any = true
        }
        if (nip96UploadsViaTor.value != torSettings.nip96UploadsViaTor) {
            nip96UploadsViaTor.tryEmit(torSettings.nip96UploadsViaTor)
            any = true
        }

        return any
    }

    companion object {
        fun build(torSettings: TorSettings): TorSettingsFlow =
            TorSettingsFlow(
                MutableStateFlow(torSettings.torType),
                MutableStateFlow(torSettings.externalSocksPort),
                MutableStateFlow(torSettings.onionRelaysViaTor),
                MutableStateFlow(torSettings.dmRelaysViaTor),
                MutableStateFlow(torSettings.newRelaysViaTor),
                MutableStateFlow(torSettings.trustedRelaysViaTor),
                MutableStateFlow(torSettings.urlPreviewsViaTor),
                MutableStateFlow(torSettings.profilePicsViaTor),
                MutableStateFlow(torSettings.imagesViaTor),
                MutableStateFlow(torSettings.videosViaTor),
                MutableStateFlow(torSettings.moneyOperationsViaTor),
                MutableStateFlow(torSettings.nip05VerificationsViaTor),
                MutableStateFlow(torSettings.nip96UploadsViaTor),
            )
    }
}
