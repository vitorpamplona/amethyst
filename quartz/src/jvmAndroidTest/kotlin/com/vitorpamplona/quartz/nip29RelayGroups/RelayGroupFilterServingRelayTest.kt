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
package com.vitorpamplona.quartz.nip29RelayGroups

import com.vitorpamplona.geode.fixtures.SyntheticEvents
import com.vitorpamplona.geode.testing.RelayClientTest
import com.vitorpamplona.geode.testing.collectUntilEose
import com.vitorpamplona.geode.testing.preload
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupAdminsEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMembersEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupPinnedEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.SupportedRolesEvent
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupIdTag
import com.vitorpamplona.quartz.nip7DThreads.ThreadEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tier C of the NIP-29 group-chat test plan: each screen's REQ shape run against the **real in-process
 * relay** (geode), proving the relay actually serves what the [RelayGroupFilterBuilders] ask for. The
 * builders themselves are Android-module, so these mirror their exact shapes with inline [Filter]s (the
 * shape↔builder equivalence is pinned separately by the amethyst-side `RelayGroupFilterBuildersTest`).
 *
 * Covers: state (`#d`), batched preview tail (`#h`, time-floored), threads (11/1111), pinned-body backfill
 * by id below the window, notification `#p`+`#h` scoping, and the relay directory (39000-39003).
 */
class RelayGroupFilterServingRelayTest : RelayClientTest() {
    private val relayKey = "b".repeat(64)
    private val me = "a".repeat(64)
    private val sig = "0".repeat(128)

    /**
     * A relay-signed **addressable** state event (`#d`-scoped) for a group. These must be the real
     * `AddressableEvent` subclasses (not a bare [SyntheticEvents.fakeEvent]) so the store populates the
     * addressable-coordinate columns the `#d` query path reads.
     */
    private fun state(
        idSeed: Int,
        kind: Int,
        groupId: String = "g1",
    ): Event {
        val id = SyntheticEvents.hexId(idSeed)
        val tags = arrayOf(arrayOf("d", groupId))
        return when (kind) {
            GroupMetadataEvent.KIND -> GroupMetadataEvent(id, relayKey, 100L, tags, "", sig)
            GroupAdminsEvent.KIND -> GroupAdminsEvent(id, relayKey, 100L, tags, "", sig)
            GroupMembersEvent.KIND -> GroupMembersEvent(id, relayKey, 100L, tags, "", sig)
            SupportedRolesEvent.KIND -> SupportedRolesEvent(id, relayKey, 100L, tags, "", sig)
            GroupPinnedEvent.KIND -> GroupPinnedEvent(id, relayKey, 100L, tags, "", sig)
            else -> error("unexpected state kind $kind")
        }
    }

    /** A group content event (`#h`-scoped), optionally `#p`-tagging a pubkey (a mention). */
    private fun content(
        idSeed: Int,
        kind: Int,
        groupId: String,
        at: Long,
        pTag: String? = null,
    ): Event {
        val tags =
            if (pTag == null) {
                arrayOf(arrayOf(GroupIdTag.TAG_NAME, groupId))
            } else {
                arrayOf(arrayOf(GroupIdTag.TAG_NAME, groupId), arrayOf("p", pTag))
            }
        return SyntheticEvents.fakeEvent(idSeed = idSeed, kind = kind, createdAt = at, tags = tags)
    }

    // --- C1: state load (#d, 39000-39005) ---

    @Test
    fun stateFilterReturnsEveryRelaySignedStateKind() =
        runBlocking {
            defaultRelay.preload(
                listOf(
                    state(1, GroupMetadataEvent.KIND),
                    state(2, GroupAdminsEvent.KIND),
                    state(3, GroupMembersEvent.KIND),
                    state(4, SupportedRolesEvent.KIND),
                    state(5, GroupPinnedEvent.KIND),
                ),
            )

            val (events, eose) =
                client.collectUntilEose(
                    defaultRelayUrl,
                    Filter(
                        kinds =
                            listOf(
                                GroupMetadataEvent.KIND,
                                GroupAdminsEvent.KIND,
                                GroupMembersEvent.KIND,
                                SupportedRolesEvent.KIND,
                                GroupPinnedEvent.KIND,
                            ),
                        tags = mapOf("d" to listOf("g1")),
                    ),
                )

            assertTrue(eose)
            assertEquals(
                setOf(39000, 39001, 39002, 39003, 39005),
                events.map { it.kind }.toSet(),
            )
        }

    // --- C2: batched preview tail (#h, time-floored, both groups in one REQ) ---

    @Test
    fun batchedTailReturnsInWindowMessagesForEveryGroupOnTheRelay() =
        runBlocking {
            val floor = 1_000L
            defaultRelay.preload(
                listOf(
                    content(10, ChatEvent.KIND, "g1", at = 500L), // below floor → excluded
                    content(11, ChatEvent.KIND, "g1", at = 1_500L),
                    content(12, ChatEvent.KIND, "g1", at = 2_000L), // g1 newest
                    content(13, ChatEvent.KIND, "g2", at = 1_800L),
                ),
            )

            val (events, eose) =
                client.collectUntilEose(
                    defaultRelayUrl,
                    Filter(
                        kinds = listOf(ChatEvent.KIND, PollEvent.KIND),
                        tags = mapOf(GroupIdTag.TAG_NAME to listOf("g1", "g2")),
                        since = floor,
                    ),
                )

            assertTrue(eose)
            assertTrue(events.all { it.createdAt >= floor }, "nothing below the floor may leak in")
            // Batched: one #h REQ carries both groups' recent chat.
            assertEquals(setOf("g1", "g2"), events.mapNotNull { it.groupTag() }.toSet())
            assertEquals(3, events.size)
            // The Messages-list preview must reflect the true newest for g1.
            assertEquals(2_000L, events.filter { it.groupTag() == "g1" }.maxOf { it.createdAt })
        }

    // --- C5: threads tab (kind 11 + 1111) ---

    @Test
    fun threadsFilterReturnsThreadRootsAndComments() =
        runBlocking {
            defaultRelay.preload(
                listOf(
                    content(20, ThreadEvent.KIND, "g1", at = 100L),
                    content(21, CommentEvent.KIND, "g1", at = 110L),
                    content(22, ChatEvent.KIND, "g1", at = 120L), // chat, must NOT match the threads filter
                ),
            )

            val (events, eose) =
                client.collectUntilEose(
                    defaultRelayUrl,
                    Filter(
                        kinds = listOf(ThreadEvent.KIND, CommentEvent.KIND),
                        tags = mapOf(GroupIdTag.TAG_NAME to listOf("g1")),
                    ),
                )

            assertTrue(eose)
            assertEquals(setOf(ThreadEvent.KIND, CommentEvent.KIND), events.map { it.kind }.toSet())
            assertTrue(events.none { it.kind == ChatEvent.KIND }, "the kind-9 chat must not surface in the threads feed")
        }

    // --- C6: a pinned message older than the window is reachable by id even when the tail excludes it ---

    @Test
    fun pinnedMessageBelowTheWindowIsUnreachableByTailButFetchableById() =
        runBlocking {
            val floor = 1_000L
            val pinned = content(30, ChatEvent.KIND, "g1", at = 200L) // older than the tail floor
            defaultRelay.preload(listOf(pinned, content(31, ChatEvent.KIND, "g1", at = 1_500L)))

            val (tail, _) =
                client.collectUntilEose(
                    defaultRelayUrl,
                    Filter(
                        kinds = listOf(ChatEvent.KIND, PollEvent.KIND),
                        tags = mapOf(GroupIdTag.TAG_NAME to listOf("g1")),
                        since = floor,
                    ),
                )
            assertFalse(tail.any { it.id == pinned.id }, "the pinned old message is below the tail window")

            val (byId, eose) = client.collectUntilEose(defaultRelayUrl, Filter(ids = listOf(pinned.id)))
            assertTrue(eose)
            assertEquals(listOf(pinned.id), byId.map { it.id })
        }

    // --- C7: notifications — only content that #p-tags me, scoped to the group ---

    @Test
    fun notificationFilterReturnsOnlyContentThatTagsMe() =
        runBlocking {
            defaultRelay.preload(
                listOf(
                    content(40, ChatEvent.KIND, "g1", at = 100L, pTag = me), // mentions me
                    content(41, CommentEvent.KIND, "g1", at = 110L, pTag = me), // mentions me
                    content(42, ChatEvent.KIND, "g1", at = 120L), // no p-tag → not a notification
                ),
            )

            val (events, eose) =
                client.collectUntilEose(
                    defaultRelayUrl,
                    Filter(
                        kinds = listOf(ChatEvent.KIND, CommentEvent.KIND),
                        tags = mapOf("p" to listOf(me), GroupIdTag.TAG_NAME to listOf("g1")),
                    ),
                )

            assertTrue(eose)
            assertEquals(setOf(40, 41).map { SyntheticEvents.hexId(it) }.toSet(), events.map { it.id }.toSet())
            assertTrue(events.none { it.id == SyntheticEvents.hexId(42) }, "a message that doesn't tag me is not a notification")
        }

    // --- C8: relay directory (kinds 39000-39003, unscoped) ---

    @Test
    fun directoryFilterListsEveryGroupTheRelayHosts() =
        runBlocking {
            defaultRelay.preload(
                listOf(
                    state(50, GroupMetadataEvent.KIND, groupId = "g1"),
                    state(51, GroupMetadataEvent.KIND, groupId = "g2"),
                    state(52, GroupAdminsEvent.KIND, groupId = "g1"),
                ),
            )

            val (events, eose) =
                client.collectUntilEose(
                    defaultRelayUrl,
                    Filter(
                        kinds =
                            listOf(
                                GroupMetadataEvent.KIND,
                                GroupAdminsEvent.KIND,
                                GroupMembersEvent.KIND,
                                SupportedRolesEvent.KIND,
                            ),
                        limit = 500,
                    ),
                )

            assertTrue(eose)
            val metadataGroups =
                events.filter { it.kind == GroupMetadataEvent.KIND }.mapNotNull { it.dTagValue() }.toSet()
            assertEquals(setOf("g1", "g2"), metadataGroups, "the directory lists both groups the relay hosts")
        }

    private fun Event.groupTag(): String? = tags.firstOrNull { it.size >= 2 && it[0] == GroupIdTag.TAG_NAME }?.get(1)

    private fun Event.dTagValue(): String? = tags.firstOrNull { it.size >= 2 && it[0] == "d" }?.get(1)
}
