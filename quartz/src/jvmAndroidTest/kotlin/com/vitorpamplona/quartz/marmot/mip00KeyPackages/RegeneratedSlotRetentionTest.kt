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
package com.vitorpamplona.quartz.marmot.mip00KeyPackages

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Regenerating a slot must not strand the KeyPackage already on relays.
 *
 * `generateKeyPackage` replaces `activeBundles[slot]`, but it does nothing to
 * the kind:30443 a previous generation was published as — that event keeps
 * sitting on relays, and a peer can still invite through it. Dropping the
 * displaced private keys therefore left the account advertising a KeyPackage
 * whose Welcomes it could not open.
 */
class RegeneratedSlotRetentionTest {
    private val identity = ByteArray(32) { 0x22 }

    @Test
    fun aRegeneratedSlotKeepsTheKeysOfTheKeyPackageStillOnRelays() =
        runBlocking {
            val manager = KeyPackageRotationManager()
            val slot = manager.getOrCreateSlotDTag("primary")

            val first = manager.generateKeyPackage(identity, slot)
            manager.recordPublishedEventId(slot, FIRST_EVENT_ID)

            // The user republishes — or a consumed slot rotates — and the slot
            // is minted afresh while the first KeyPackage is still published.
            val second = manager.generateKeyPackage(identity, slot)
            manager.recordPublishedEventId(slot, SECOND_EVENT_ID)

            val recoveredFirst = manager.findBundleByEventId(FIRST_EVENT_ID)
            assertNotNull("the displaced bundle must survive regeneration", recoveredFirst)
            assertSame("a Welcome against the older event must get the OLDER bundle", first, recoveredFirst)

            val recoveredSecond = manager.findBundleByEventId(SECOND_EVENT_ID)
            assertSame("the newest event must still resolve to the active bundle", second, recoveredSecond)
        }

    @Test
    fun aRegeneratedSlotIsAlsoRecoverableByKeyPackageReference() =
        runBlocking {
            val manager = KeyPackageRotationManager()
            val slot = manager.getOrCreateSlotDTag("primary")

            val first = manager.generateKeyPackage(identity, slot)
            manager.recordPublishedEventId(slot, FIRST_EVENT_ID)
            manager.generateKeyPackage(identity, slot)

            // The Welcome path matches on the KeyPackage reference before it
            // falls back to the Nostr event id, so retention has to hold up
            // under that lookup too.
            val byRef = manager.findBundleByRef(first.keyPackage.reference())
            assertSame("the displaced bundle must be reachable by its own ref", first, byRef)
        }

    @Test
    fun regeneratingDoesNotLeaveTheOldEventIdPointingAtTheNewBundle() =
        runBlocking {
            val manager = KeyPackageRotationManager()
            val slot = manager.getOrCreateSlotDTag("primary")

            val first = manager.generateKeyPackage(identity, slot)
            manager.recordPublishedEventId(slot, FIRST_EVENT_ID)
            val second = manager.generateKeyPackage(identity, slot)

            // The regression this guards: resolving the id through the slot
            // index returned whatever now occupied the slot, so the older event
            // handed out keys that cannot open the Welcome addressed to it.
            val recovered = manager.findBundleByEventId(FIRST_EVENT_ID)
            assertEquals(
                "the old event id must not resolve to the replacement bundle",
                false,
                recovered === second,
            )
            assertSame(first, recovered)
        }

    companion object {
        private val FIRST_EVENT_ID = "aa".repeat(32)
        private val SECOND_EVENT_ID = "bb".repeat(32)
    }
}
