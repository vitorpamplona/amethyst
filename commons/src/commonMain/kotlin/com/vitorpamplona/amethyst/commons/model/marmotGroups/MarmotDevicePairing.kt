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
package com.vitorpamplona.amethyst.commons.model.marmotGroups

import com.vitorpamplona.quartz.nip01Core.core.Event

/**
 * Marmot device pairing (`features/device-pairing.md`, draft): rumor kinds
 * 452 Request, 453 Answer, 454 Reveal, 455 Confirm and 456 Cancel, each a
 * NIP-59 gift wrap from the account to itself. White Noise (MDK 0.18 device
 * groups) sends them to link a new device to the account's others.
 *
 * Amethyst does not link devices, so these are protocol traffic for another
 * client of the same account, not messages for the user: they are kept out of
 * the cache, every chat feed and notifications. A user signed in to White
 * Noise and Amethyst with one key would otherwise collect them.
 */
object MarmotDevicePairing {
    val KINDS: IntRange = 452..456

    /** Whether [rumor] is a device-pairing message, which this client ignores. */
    fun isPairingRumor(rumor: Event): Boolean = rumor.kind in KINDS
}
