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
package com.vitorpamplona.quartz.nip51Lists.muteList

import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.EventTag
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.UserTag
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.WordTag
import com.vitorpamplona.quartz.utils.nsecToKeyPair
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MuteListEventTest {
    private val signer = NostrSignerInternal("nsec10g0wheggqn9dawlc0yuv6adnat6n09anr7eyykevw2dm8xa5fffs0wsdsr".nsecToKeyPair())

    // 64-char hex IDs
    private val rootA = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1"
    private val rootB = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb2b"
    private val pubA = "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc3c"

    @Test
    fun create_withEventTag_privateContent() =
        runTest {
            val event =
                MuteListEvent.create(
                    mute = EventTag(rootA),
                    isPrivate = true,
                    signer = signer,
                    createdAt = 1_700_000_000,
                )

            // No public "e" tag should be present
            val publicETags = event.tags.filter { it.size >= 2 && it[0] == "e" && it[1] == rootA }
            assertTrue(publicETags.isEmpty(), "Private mute must not appear as a public e-tag")

            // Content must be non-empty (it is encrypted)
            assertTrue(event.content.isNotEmpty(), "Content must be non-empty when mute is private")

            // Decrypting must yield exactly one EventTag with eventId == rootA
            val privateMutes = assertNotNull(event.privateMutes(signer), "privateMutes must not return null")
            assertEquals(1, privateMutes.size, "Expected exactly one private mute")
            val tag = assertNotNull(privateMutes.firstOrNull() as? EventTag, "Mute must be an EventTag")
            assertEquals(rootA, tag.eventId)
        }

    @Test
    fun add_eventTagToExistingMute_combinesPrivateSet() =
        runTest {
            val firstEvent =
                MuteListEvent.create(
                    mute = EventTag(rootA),
                    isPrivate = true,
                    signer = signer,
                    createdAt = 1_700_000_001,
                )

            val secondEvent =
                MuteListEvent.add(
                    earlierVersion = firstEvent,
                    mute = EventTag(rootB),
                    isPrivate = true,
                    signer = signer,
                    createdAt = 1_700_000_002,
                )

            val privateMutes = assertNotNull(secondEvent.privateMutes(signer), "privateMutes must not return null")
            val ids = privateMutes.filterIsInstance<EventTag>().map { it.eventId }.toSet()
            assertTrue(ids.contains(rootA), "rootA must be present after add")
            assertTrue(ids.contains(rootB), "rootB must be present after add")
        }

    @Test
    fun add_eventTagPreservesPriorUserAndWordTags() =
        runTest {
            // Build a kind-10000 with one UserTag and one WordTag (both private)
            val base =
                MuteListEvent.create(
                    publicMutes = emptyList(),
                    privateMutes = listOf(UserTag(pubA), WordTag("spam")),
                    signer = signer,
                    createdAt = 1_700_000_003,
                )

            val updated =
                MuteListEvent.add(
                    earlierVersion = base,
                    mute = EventTag(rootA),
                    isPrivate = true,
                    signer = signer,
                    createdAt = 1_700_000_004,
                )

            val privateMutes = assertNotNull(updated.privateMutes(signer), "privateMutes must not return null")
            assertEquals(3, privateMutes.size, "Expected three private mutes (UserTag + WordTag + EventTag)")

            val userTags = privateMutes.filterIsInstance<UserTag>()
            val wordTags = privateMutes.filterIsInstance<WordTag>()
            val eventTags = privateMutes.filterIsInstance<EventTag>()

            assertEquals(1, userTags.size, "Must have exactly one UserTag")
            assertEquals(pubA, userTags.first().pubKey)

            assertEquals(1, wordTags.size, "Must have exactly one WordTag")
            assertEquals("spam", wordTags.first().word)

            assertEquals(1, eventTags.size, "Must have exactly one EventTag")
            assertEquals(rootA, eventTags.first().eventId)
        }

    @Test
    fun remove_eventTagLeavesOthers() =
        runTest {
            // Build event with both rootA and rootB muted privately
            val base =
                MuteListEvent.create(
                    publicMutes = emptyList(),
                    privateMutes = listOf(EventTag(rootA), EventTag(rootB)),
                    signer = signer,
                    createdAt = 1_700_000_005,
                )

            val updated =
                MuteListEvent.remove(
                    earlierVersion = base,
                    mute = EventTag(rootA),
                    signer = signer,
                    createdAt = 1_700_000_006,
                )

            val privateMutes = assertNotNull(updated.privateMutes(signer), "privateMutes must not return null")
            val ids = privateMutes.filterIsInstance<EventTag>().map { it.eventId }.toSet()
            assertTrue(!ids.contains(rootA), "rootA must have been removed")
            assertTrue(ids.contains(rootB), "rootB must still be present")
        }

    @Test
    fun removeAll_mixedTagsRemovesUserAndEvent_keepsWord() =
        runTest {
            val base =
                MuteListEvent.create(
                    publicMutes = emptyList(),
                    privateMutes = listOf(UserTag(pubA), WordTag("spam"), EventTag(rootA)),
                    signer = signer,
                    createdAt = 1_700_000_007,
                )

            val updated =
                MuteListEvent.removeAll(
                    earlierVersion = base,
                    mutes = listOf(UserTag(pubA), EventTag(rootA)),
                    signer = signer,
                    createdAt = 1_700_000_008,
                )

            val privateMutes = assertNotNull(updated.privateMutes(signer), "privateMutes must not return null")

            val userTags = privateMutes.filterIsInstance<UserTag>()
            val wordTags = privateMutes.filterIsInstance<WordTag>()
            val eventTags = privateMutes.filterIsInstance<EventTag>()

            assertTrue(userTags.none { it.pubKey == pubA }, "UserTag(pubA) must have been removed")
            assertTrue(eventTags.none { it.eventId == rootA }, "EventTag(rootA) must have been removed")
            assertEquals(1, wordTags.size, "WordTag must still be present")
            assertEquals("spam", wordTags.first().word)
        }

    @Test
    fun legacyMuteListWithoutEventTags_decodesToEmptyThreadSet() =
        runTest {
            // Build a kind-10000 with only p + word tags (public), no e tags
            val legacyEvent =
                MuteListEvent.create(
                    publicMutes = listOf(UserTag(pubA), WordTag("spam")),
                    privateMutes = emptyList(),
                    signer = signer,
                    createdAt = 1_700_000_009,
                )

            // Calling mutedThreadIdSet() on a tag array with no e-tags must not crash and return empty
            val publicThreadIds = legacyEvent.tags.mutedThreadIdSet()
            assertTrue(publicThreadIds.isEmpty(), "Legacy event with only p+word tags must have empty thread id set")

            // privateMutes returns empty list (content is blank/empty for no private mutes)
            val privateMutes = legacyEvent.privateMutes(signer)
            val privateEventTags = (privateMutes ?: emptyList()).filterIsInstance<EventTag>()
            assertTrue(privateEventTags.isEmpty(), "No private EventTags in a legacy event")
        }

    @Test
    fun roundTrip_eventTagsViaEncryption_preservesIds() =
        runTest {
            val event =
                MuteListEvent.create(
                    publicMutes = emptyList(),
                    privateMutes = listOf(EventTag(rootA), EventTag(rootB)),
                    signer = signer,
                    createdAt = 1_700_000_010,
                )

            val decrypted = assertNotNull(event.privateMutes(signer), "privateMutes must not return null")
            val ids = decrypted.filterIsInstance<EventTag>().map { it.eventId }.toSet()

            assertEquals(setOf(rootA, rootB), ids, "Round-trip must preserve all muted thread IDs")
        }
}
