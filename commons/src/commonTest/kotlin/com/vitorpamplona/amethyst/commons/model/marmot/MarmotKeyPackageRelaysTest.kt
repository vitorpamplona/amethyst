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
package com.vitorpamplona.amethyst.commons.model.marmot

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Where this account's KeyPackages go. On a first login the account's relay lists have not loaded
 * yet, so the outbox is the app's defaults: a KeyPackage published there alone is invisible to a
 * White Noise inviter that looks on the relays the user chose (`missing_key_package`).
 */
class MarmotKeyPackageRelaysTest {
    private val defaultA = RelayUrlNormalizer.normalize("wss://default-a.example")
    private val defaultB = RelayUrlNormalizer.normalize("wss://default-b.example")
    private val mineWrite = RelayUrlNormalizer.normalize("wss://my-write.example")
    private val mineKp = RelayUrlNormalizer.normalize("wss://my-keypackages.example")
    private val privateStorage = RelayUrlNormalizer.normalize("wss://my-private.example")

    private val defaults = setOf(defaultA, defaultB)

    @Test
    fun beforeAnyListLoadsTheDefaultsAreAProvisionalChoice() {
        val choice = MarmotKeyPackageRelays.choose(outbox = defaults, assumedDefaults = defaults, keyPackageList = emptySet())
        assertEquals(defaults, choice.relays)
        assertTrue(choice.provisional, "a later list must replace it")
    }

    @Test
    fun aKeyPackageListKeepsTheDefaultsOut() {
        // The 10051 arrived but the 10002 has not: the outbox still holds the app's guesses.
        val choice = MarmotKeyPackageRelays.choose(outbox = defaults + privateStorage, assumedDefaults = defaults, keyPackageList = setOf(mineKp))
        assertEquals(setOf(mineKp, privateStorage), choice.relays)
        assertFalse(choice.provisional)
    }

    @Test
    fun theNip65WriteRelaysAndTheKeyPackageListAreUnioned() {
        val choice = MarmotKeyPackageRelays.choose(outbox = setOf(mineWrite), assumedDefaults = emptySet(), keyPackageList = setOf(mineKp))
        assertEquals(setOf(mineWrite, mineKp), choice.relays)
        assertFalse(choice.provisional)
    }

    @Test
    fun aKnownNip65ListWithoutAKeyPackageListIsTheWriteSet() {
        val choice = MarmotKeyPackageRelays.choose(outbox = setOf(mineWrite), assumedDefaults = emptySet(), keyPackageList = emptySet())
        assertEquals(setOf(mineWrite), choice.relays)
        assertFalse(choice.provisional)
    }

    @Test
    fun aKnownListWithNoWriteRelaysFallsBackToTheOutbox() {
        val choice = MarmotKeyPackageRelays.choose(outbox = emptySet(), assumedDefaults = emptySet(), keyPackageList = emptySet())
        assertTrue(choice.relays.isEmpty())
        assertFalse(choice.provisional)
    }

    @Test
    fun relaysToAddAreTheOnesTheEventHasNotReached() {
        assertEquals(setOf(mineKp), MarmotKeyPackageRelays.missing(target = setOf(mineWrite, mineKp), reached = setOf(mineWrite, defaultA)))
        assertTrue(MarmotKeyPackageRelays.missing(target = setOf(mineWrite), reached = setOf(mineWrite)).isEmpty())
    }
}
