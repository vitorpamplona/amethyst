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
package com.vitorpamplona.quartz.nip51Lists.simpleGroupList

import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.utils.nsecToKeyPair
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SimpleGroupListEventTest {
    private val signer = NostrSignerInternal("nsec10g0wheggqn9dawlc0yuv6adnat6n09anr7eyykevw2dm8xa5fffs0wsdsr".nsecToKeyPair())

    private val group = GroupTag("abc123", "wss://relay.example.com/", "My Group")

    @Test
    fun removeFromListCreatedWithNoPrivateGroups() =
        runTest {
            // Exactly what RelayGroupListState.follow() does for the very first group.
            val created =
                SimpleGroupListEvent.create(
                    publicGroups = listOf(group),
                    signer = signer,
                    createdAt = 1740669816,
                )

            assertEquals(1, created.publicGroups().size)

            val removed =
                SimpleGroupListEvent.remove(
                    earlierVersion = created,
                    group = group,
                    signer = signer,
                    createdAt = 1740669817,
                )

            assertEquals(0, removed.publicGroups().size)
        }

    @Test
    fun removeIgnoresTheCosmeticNameOnTheStoredTag() =
        runTest {
            val created =
                SimpleGroupListEvent.create(
                    publicGroups = listOf(group),
                    signer = signer,
                    createdAt = 1740669816,
                )

            // The channel metadata may have changed names since the tag was written.
            val removed =
                SimpleGroupListEvent.remove(
                    earlierVersion = created,
                    group = GroupTag(group.groupId, group.relayUrl, "Renamed"),
                    signer = signer,
                    createdAt = 1740669817,
                )

            assertEquals(0, removed.publicGroups().size)
        }
}
