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
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarmotDevicePairingTest {
    private fun rumor(kind: Int) = Event("0".repeat(64), "1".repeat(64), 1_700_000_000, kind, emptyArray(), "{}", "")

    @Test
    fun theFivePairingKindsAreIgnored() {
        (452..456).forEach { assertTrue(MarmotDevicePairing.isPairingRumor(rumor(it)), "kind $it") }
    }

    @Test
    fun neighbouringKindsAreNot() {
        // 444 is the Marmot Welcome, 14 a NIP-17 DM, 451 and 457 border the range.
        listOf(14, 444, 451, 457).forEach { assertFalse(MarmotDevicePairing.isPairingRumor(rumor(it)), "kind $it") }
    }
}
