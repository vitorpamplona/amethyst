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
package com.vitorpamplona.amethyst.ui.i2p

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.i2p.I2pSettings
import com.vitorpamplona.amethyst.commons.i2p.I2pType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

@Stable
class I2pSettingsFlow(
    val i2pType: MutableStateFlow<I2pType> = MutableStateFlow(I2pType.OFF),
    val externalSocksPort: MutableStateFlow<Int> = MutableStateFlow(4447),
    val i2pRelaysViaI2p: MutableStateFlow<Boolean> = MutableStateFlow(true),
    val dmRelaysViaI2p: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val newRelaysViaI2p: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val trustedRelaysViaI2p: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val urlPreviewsViaI2p: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val profilePicsViaI2p: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val imagesViaI2p: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val videosViaI2p: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val moneyOperationsViaI2p: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val nip05VerificationsViaI2p: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val mediaUploadsViaI2p: MutableStateFlow<Boolean> = MutableStateFlow(false),
) {
    val propertyWatchFlow =
        combine<Any, I2pSettings>(
            listOf(
                i2pType,
                externalSocksPort,
                i2pRelaysViaI2p,
                dmRelaysViaI2p,
                newRelaysViaI2p,
                trustedRelaysViaI2p,
                urlPreviewsViaI2p,
                profilePicsViaI2p,
                imagesViaI2p,
                videosViaI2p,
                moneyOperationsViaI2p,
                nip05VerificationsViaI2p,
                mediaUploadsViaI2p,
            ),
        ) { flows ->
            I2pSettings(
                flows[0] as I2pType,
                flows[1] as Int,
                flows[2] as Boolean,
                flows[3] as Boolean,
                flows[4] as Boolean,
                flows[5] as Boolean,
                flows[6] as Boolean,
                flows[7] as Boolean,
                flows[8] as Boolean,
                flows[9] as Boolean,
                flows[10] as Boolean,
                flows[11] as Boolean,
                flows[12] as Boolean,
            )
        }

    fun toSettings(): I2pSettings =
        I2pSettings(
            i2pType.value,
            externalSocksPort.value,
            i2pRelaysViaI2p.value,
            dmRelaysViaI2p.value,
            newRelaysViaI2p.value,
            trustedRelaysViaI2p.value,
            urlPreviewsViaI2p.value,
            profilePicsViaI2p.value,
            imagesViaI2p.value,
            videosViaI2p.value,
            moneyOperationsViaI2p.value,
            nip05VerificationsViaI2p.value,
            mediaUploadsViaI2p.value,
        )

    fun update(settings: I2pSettings): Boolean {
        var any = false

        if (i2pType.value != settings.i2pType) {
            i2pType.tryEmit(settings.i2pType)
            any = true
        }
        if (externalSocksPort.value != settings.externalSocksPort) {
            externalSocksPort.tryEmit(settings.externalSocksPort)
            any = true
        }
        if (i2pRelaysViaI2p.value != settings.i2pRelaysViaI2p) {
            i2pRelaysViaI2p.tryEmit(settings.i2pRelaysViaI2p)
            any = true
        }
        if (dmRelaysViaI2p.value != settings.dmRelaysViaI2p) {
            dmRelaysViaI2p.tryEmit(settings.dmRelaysViaI2p)
            any = true
        }
        if (newRelaysViaI2p.value != settings.newRelaysViaI2p) {
            newRelaysViaI2p.tryEmit(settings.newRelaysViaI2p)
            any = true
        }
        if (trustedRelaysViaI2p.value != settings.trustedRelaysViaI2p) {
            trustedRelaysViaI2p.tryEmit(settings.trustedRelaysViaI2p)
            any = true
        }
        if (urlPreviewsViaI2p.value != settings.urlPreviewsViaI2p) {
            urlPreviewsViaI2p.tryEmit(settings.urlPreviewsViaI2p)
            any = true
        }
        if (profilePicsViaI2p.value != settings.profilePicsViaI2p) {
            profilePicsViaI2p.tryEmit(settings.profilePicsViaI2p)
            any = true
        }
        if (imagesViaI2p.value != settings.imagesViaI2p) {
            imagesViaI2p.tryEmit(settings.imagesViaI2p)
            any = true
        }
        if (videosViaI2p.value != settings.videosViaI2p) {
            videosViaI2p.tryEmit(settings.videosViaI2p)
            any = true
        }
        if (moneyOperationsViaI2p.value != settings.moneyOperationsViaI2p) {
            moneyOperationsViaI2p.tryEmit(settings.moneyOperationsViaI2p)
            any = true
        }
        if (nip05VerificationsViaI2p.value != settings.nip05VerificationsViaI2p) {
            nip05VerificationsViaI2p.tryEmit(settings.nip05VerificationsViaI2p)
            any = true
        }
        if (mediaUploadsViaI2p.value != settings.mediaUploadsViaI2p) {
            mediaUploadsViaI2p.tryEmit(settings.mediaUploadsViaI2p)
            any = true
        }

        return any
    }

    companion object {
        fun build(settings: I2pSettings): I2pSettingsFlow =
            I2pSettingsFlow(
                MutableStateFlow(settings.i2pType),
                MutableStateFlow(settings.externalSocksPort),
                MutableStateFlow(settings.i2pRelaysViaI2p),
                MutableStateFlow(settings.dmRelaysViaI2p),
                MutableStateFlow(settings.newRelaysViaI2p),
                MutableStateFlow(settings.trustedRelaysViaI2p),
                MutableStateFlow(settings.urlPreviewsViaI2p),
                MutableStateFlow(settings.profilePicsViaI2p),
                MutableStateFlow(settings.imagesViaI2p),
                MutableStateFlow(settings.videosViaI2p),
                MutableStateFlow(settings.moneyOperationsViaI2p),
                MutableStateFlow(settings.nip05VerificationsViaI2p),
                MutableStateFlow(settings.mediaUploadsViaI2p),
            )
    }
}
