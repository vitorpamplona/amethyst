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

import com.vitorpamplona.amethyst.R

class TorSettings(
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
    val nip96UploadsViaTor: Boolean = false,
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
        else -> {
            TorType.INTERNAL
        }
    }
