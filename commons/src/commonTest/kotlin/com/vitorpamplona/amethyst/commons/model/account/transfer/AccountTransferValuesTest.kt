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
import kotlin.test.assertTrue

class AccountTransferValuesTest {
    @Test
    fun keepsEachValueAtItsOriginalType() {
        // A Long that comes back as an Int throws a ClassCastException inside
        // whichever feature reads that key, long after the import looked fine.
        val converted =
            AccountTransferValues.fromPreferenceMap(
                mapOf(
                    "aString" to "hello",
                    "aBool" to true,
                    "anInt" to 7,
                    "aLong" to 9_000_000_000L,
                    "aFloat" to 0.25f,
                    "aSet" to setOf("b", "a"),
                ),
            )

        assertEquals(TransferValue.Str("hello"), converted["aString"])
        assertEquals(TransferValue.Bool(true), converted["aBool"])
        assertEquals(TransferValue.Int32(7), converted["anInt"])
        assertEquals(TransferValue.Int64(9_000_000_000L), converted["aLong"])
        assertEquals(TransferValue.Flt(0.25f), converted["aFloat"])
        assertEquals(TransferValue.StrSet(listOf("a", "b")), converted["aSet"])
    }

    @Test
    fun sortsSetsSoTheSameSettingsProduceTheSameBytes() {
        val first = AccountTransferValues.fromPreferenceMap(mapOf("k" to linkedSetOf("z", "a", "m")))
        val second = AccountTransferValues.fromPreferenceMap(mapOf("k" to linkedSetOf("m", "z", "a")))

        assertEquals(first, second)
        assertEquals(TransferValue.StrSet(listOf("a", "m", "z")), first["k"])
    }

    @Test
    fun dropsReadingHistoryAndPendingLocalWork() {
        // Per-device history, not settings: unbounded, changes on nearly every
        // interaction, and describes what happened on the OLD phone.
        val converted =
            AccountTransferValues.fromPreferenceMap(
                mapOf(
                    AccountTransferKeys.LAST_READ_PER_ROUTE to "{}",
                    AccountTransferKeys.DISMISSED_POLL_NOTE_IDS to setOf("id"),
                    AccountTransferKeys.VIEWED_POLL_RESULT_NOTE_IDS to "{}",
                    AccountTransferKeys.PENDING_ATTESTATIONS to "{}",
                    // Kept: a display choice the user made, not history.
                    "dismissed_channel_invites" to setOf("channel"),
                ),
            )

        assertEquals(setOf("dismissed_channel_invites"), converted.keys)
    }

    @Test
    fun dropsTheKeysThatMustNotLeaveTheDevice() {
        val converted =
            AccountTransferValues.fromPreferenceMap(
                mapOf(
                    AccountTransferKeys.NOSTR_PRIVKEY to "deadbeef",
                    AccountTransferKeys.NIP46_BUNKER_SECRET to "secret",
                    AccountTransferKeys.NIP46_TRANSPORT_KEY to "transport",
                    AccountTransferKeys.NIP46_SEEN_IDS to setOf("id"),
                    "nwcWallets" to "[]",
                ),
            )

        assertEquals(setOf("nwcWallets"), converted.keys)
    }

    @Test
    fun dropsValuesPreferencesCannotHold() {
        // `all` is typed Map<String, *>; anything not a preference type could not
        // be written back, so carrying it would only produce a failing import.
        val converted = AccountTransferValues.fromPreferenceMap(mapOf("weird" to Any(), "missing" to null, "ok" to "yes"))

        assertEquals(setOf("ok"), converted.keys)
    }

    @Test
    fun dropsNonStringsOutOfASet() {
        val converted = AccountTransferValues.fromPreferenceMap(mapOf("mixed" to setOf("a", 1, null)))

        assertEquals(TransferValue.StrSet(listOf("a")), converted["mixed"])
    }

    @Test
    fun anEmptyPreferenceFileConvertsToAnEmptyMap() {
        assertTrue(AccountTransferValues.fromPreferenceMap(emptyMap()).isEmpty())
    }
}
