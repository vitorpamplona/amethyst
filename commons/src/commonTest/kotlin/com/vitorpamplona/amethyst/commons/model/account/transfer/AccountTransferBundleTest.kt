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
package com.vitorpamplona.amethyst.commons.model.account.transfer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountTransferBundleTest {
    @Test
    fun keepsTheHigherCounterPerKeyset() {
        val merged =
            mergeCounters(
                current = mapOf("keysetA" to 10L, "keysetB" to 99L),
                imported = mapOf("keysetA" to 25L, "keysetB" to 3L),
            )

        assertEquals(25L, merged["keysetA"], "the imported phone had spent further ahead")
        assertEquals(99L, merged["keysetB"], "this phone had spent further ahead")
    }

    @Test
    fun neverMovesACounterBackwards() {
        // The whole point: re-deriving an index the mint already signed strands
        // the keyset, so a stale bundle must not lower what this device knows.
        val merged = mergeCounters(mapOf("keyset" to 500L), mapOf("keyset" to 1L))

        assertEquals(500L, merged["keyset"])
    }

    @Test
    fun carriesOverKeysetsThisDeviceHasNeverSeen() {
        val merged = mergeCounters(mapOf("known" to 1L), mapOf("fresh" to 7L))

        assertEquals(mapOf("known" to 1L, "fresh" to 7L), merged)
    }

    // ---
    // npub validation: the bundle is untrusted input and its npub becomes both a
    // preference file name and a saved-account identity.
    // ---

    @Test
    fun acceptsACanonicalNpub() {
        assertTrue(isWellFormedNpub("npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6"))
    }

    @Test
    fun rejectsTheEmptyNpub() {
        // The dangerous one: account deletion matches preference files by
        // name.contains(npub), so "" matches every file on disk and removing
        // that account would take every other account's data with it.
        assertFalse(isWellFormedNpub(""))
    }

    @Test
    fun rejectsNonCanonicalAndHostileStrings() {
        assertFalse(isWellFormedNpub("1"))
        assertFalse(isWellFormedNpub("../../databases/x"))
        assertFalse(isWellFormedNpub("npub1"))
        assertFalse(isWellFormedNpub("not an npub at all"))
        // A bare hex pubkey decodes as a key but is not the canonical npub form
        // the account files are named with.
        assertFalse(isWellFormedNpub("3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"))
    }

    @Test
    fun rejectsAnNsecOfferedWhereAnNpubBelongs() {
        // uriToRoute happily decodes an nsec to a pubkey; the round-trip check is
        // what keeps a secret key out of a filename.
        assertFalse(isWellFormedNpub("nsec1vl029mgpspedva04g90vltkh6fvh240zqtv9k0t9af8935ke9laqsnlfe5"))
    }

    @Test
    fun anEmptyImportChangesNothing() {
        val current = mapOf("keyset" to 4L)

        assertEquals(current, mergeCounters(current, emptyMap()))
    }
}
