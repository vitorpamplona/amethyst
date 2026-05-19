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
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.commons.i2p.I2pSettings
import com.vitorpamplona.amethyst.commons.i2p.I2pType

@Stable
class I2pDialogViewModel : ViewModel() {
    val i2pType = mutableStateOf(I2pType.OFF)
    val socksPortStr = mutableStateOf("4447")

    val i2pRelaysViaI2p = mutableStateOf(true)
    val dmRelaysViaI2p = mutableStateOf(false)
    val newRelaysViaI2p = mutableStateOf(false)
    val trustedRelaysViaI2p = mutableStateOf(false)
    val urlPreviewsViaI2p = mutableStateOf(false)
    val profilePicsViaI2p = mutableStateOf(false)
    val imagesViaI2p = mutableStateOf(false)
    val videosViaI2p = mutableStateOf(false)
    val moneyOperationsViaI2p = mutableStateOf(false)
    val nip05VerificationsViaI2p = mutableStateOf(false)
    val mediaUploadsViaI2p = mutableStateOf(false)

    fun reset(settings: I2pSettings) {
        i2pType.value = settings.i2pType
        socksPortStr.value = settings.externalSocksPort.toString()
        i2pRelaysViaI2p.value = settings.i2pRelaysViaI2p
        dmRelaysViaI2p.value = settings.dmRelaysViaI2p
        newRelaysViaI2p.value = settings.newRelaysViaI2p
        trustedRelaysViaI2p.value = settings.trustedRelaysViaI2p
        urlPreviewsViaI2p.value = settings.urlPreviewsViaI2p
        profilePicsViaI2p.value = settings.profilePicsViaI2p
        imagesViaI2p.value = settings.imagesViaI2p
        videosViaI2p.value = settings.videosViaI2p
        moneyOperationsViaI2p.value = settings.moneyOperationsViaI2p
        nip05VerificationsViaI2p.value = settings.nip05VerificationsViaI2p
        mediaUploadsViaI2p.value = settings.mediaUploadsViaI2p
    }

    fun save(): I2pSettings =
        I2pSettings(
            i2pType = i2pType.value,
            externalSocksPort = Integer.parseInt(socksPortStr.value),
            i2pRelaysViaI2p = i2pRelaysViaI2p.value,
            dmRelaysViaI2p = dmRelaysViaI2p.value,
            newRelaysViaI2p = newRelaysViaI2p.value,
            trustedRelaysViaI2p = trustedRelaysViaI2p.value,
            urlPreviewsViaI2p = urlPreviewsViaI2p.value,
            profilePicsViaI2p = profilePicsViaI2p.value,
            imagesViaI2p = imagesViaI2p.value,
            videosViaI2p = videosViaI2p.value,
            moneyOperationsViaI2p = moneyOperationsViaI2p.value,
            nip05VerificationsViaI2p = nip05VerificationsViaI2p.value,
            mediaUploadsViaI2p = mediaUploadsViaI2p.value,
        )
}
