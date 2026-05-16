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
package com.vitorpamplona.amethyst.commons.i2p

// Shape mirrors TorSettings. Both daemons can be enabled side-by-side (needed so
// .onion and .i2p hidden services stay reachable independently), but only one
// transport carries clearnet at a time — see PrivacySettings.preferredClearnetTransport.
// The per-feature booleans here only take effect when I2P is the preferred clearnet
// transport; otherwise the matching TorSettings flag wins.
data class I2pSettings(
    val i2pType: I2pType = I2pType.OFF,
    val externalSocksPort: Int = 4447,
    val i2pRelaysViaI2p: Boolean = true,
    val dmRelaysViaI2p: Boolean = false,
    val newRelaysViaI2p: Boolean = false,
    val trustedRelaysViaI2p: Boolean = false,
    val urlPreviewsViaI2p: Boolean = false,
    val profilePicsViaI2p: Boolean = false,
    val imagesViaI2p: Boolean = false,
    val videosViaI2p: Boolean = false,
    val moneyOperationsViaI2p: Boolean = false,
    val nip05VerificationsViaI2p: Boolean = false,
    val mediaUploadsViaI2p: Boolean = false,
)

enum class I2pType(
    val screenCode: Int,
) {
    OFF(0),
    INTERNAL(1),
    EXTERNAL(2),
}

fun parseI2pType(code: Int?): I2pType =
    when (code) {
        I2pType.INTERNAL.screenCode -> I2pType.INTERNAL
        I2pType.EXTERNAL.screenCode -> I2pType.EXTERNAL
        else -> I2pType.OFF
    }
