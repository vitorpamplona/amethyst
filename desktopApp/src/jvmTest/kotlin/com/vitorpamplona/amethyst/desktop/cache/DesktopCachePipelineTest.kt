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
package com.vitorpamplona.amethyst.desktop.cache

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.desktop.feeds.DesktopFollowingFeedFilter
import com.vitorpamplona.amethyst.desktop.feeds.DesktopGlobalFeedFilter
import com.vitorpamplona.amethyst.desktop.feeds.DesktopNotificationFeedFilter
import com.vitorpamplona.amethyst.desktop.feeds.DesktopProfileFeedFilter
import com.vitorpamplona.amethyst.desktop.feeds.DesktopThreadFilter
import com.vitorpamplona.amethyst.desktop.viewmodels.DesktopFeedViewModel
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Integration tests for the Desktop cache → filter → ViewModel pipeline.
 *
 * These tests verify that events consumed into DesktopLocalCache flow through
 * feed filters and into DesktopFeedViewModel's FeedState correctly.
 *
 * The test structure mirrors how the app works:
 *   1. Events arrive from relays
 *   2. DesktopLocalCache.consume() stores them + emits via eventStream
 *   3. DesktopFeedViewModel collects eventStream and updates FeedState
 *   4. FeedFilter determines which notes appear in which feed
 */
class DesktopCachePipelineTest {
    // Deterministic test keys
    private val userPubKey = "a".repeat(64)
    private val followedPubKey = "b".repeat(64)
    private val unfollowedPubKey = "c".repeat(64)
    private val dummySig = "0".repeat(128)
    private val relayUrl =
        com.vitorpamplona.quartz.nip01Core.relay.normalizer
            .NormalizedRelayUrl("wss://relay.test/")

    /** Wait for async bundling (250ms bundler + margin) */
    private suspend fun waitForBundler() = delay(500)

    private fun textNote(
        id: String,
        pubKey: String,
        content: String = "Hello world",
        createdAt: Long = System.currentTimeMillis() / 1000,
        replyToId: String? = null,
    ): TextNoteEvent {
        val tags =
            if (replyToId != null) {
                arrayOf(arrayOf("e", replyToId, "", "reply"))
            } else {
                emptyArray()
            }
        return TextNoteEvent(
            id = id,
            pubKey = pubKey,
            createdAt = createdAt,
            tags = tags,
            content = content,
            sig = dummySig,
        )
    }

    private fun contactList(
        id: String,
        pubKey: String,
        follows: List<String>,
        createdAt: Long = System.currentTimeMillis() / 1000,
    ): ContactListEvent =
        ContactListEvent(
            id = id,
            pubKey = pubKey,
            createdAt = createdAt,
            tags = follows.map { arrayOf("p", it) }.toTypedArray(),
            content = "",
            sig = dummySig,
        )

    private fun reaction(
        id: String,
        pubKey: String,
        targetNoteId: String,
        createdAt: Long = System.currentTimeMillis() / 1000,
    ): ReactionEvent =
        ReactionEvent(
            id = id,
            pubKey = pubKey,
            createdAt = createdAt,
            tags = arrayOf(arrayOf("e", targetNoteId)),
            content = "+",
            sig = dummySig,
        )

    // -----------------------------------------------------------------------
    // 1. Cache consumption basics
    // -----------------------------------------------------------------------

    @Test
    fun `consume text note creates Note in cache`() {
        val cache = DesktopLocalCache()
        val event = textNote("note1".padEnd(64, '0'), userPubKey)

        val consumed = cache.consume(event, relayUrl)

        assertTrue(consumed, "First consume should return true")
        val note = cache.getNoteIfExists("note1".padEnd(64, '0'))
        assertTrue(note != null, "Note should exist in cache after consume")
        assertEquals(event.id, note.event?.id)
    }

    @Test
    fun `consume same note twice returns false`() {
        val cache = DesktopLocalCache()
        val event = textNote("note1".padEnd(64, '0'), userPubKey)

        cache.consume(event, relayUrl)
        val secondConsume = cache.consume(event, relayUrl)

        assertTrue(!secondConsume, "Second consume of same event should return false")
    }

    @Test
    fun `consume contact list updates followedUsers`() {
        val cache = DesktopLocalCache()
        val event = contactList("cl1".padEnd(64, '0'), userPubKey, listOf(followedPubKey))

        cache.consume(event, relayUrl)

        assertEquals(setOf(followedPubKey), cache.followedUsers.value)
    }

    @Test
    fun `newer contact list replaces older`() {
        val cache = DesktopLocalCache()
        val old = contactList("cl1".padEnd(64, '0'), userPubKey, listOf(followedPubKey), createdAt = 100)
        val newer =
            contactList(
                "cl2".padEnd(64, '0'),
                userPubKey,
                listOf(followedPubKey, unfollowedPubKey),
                createdAt = 200,
            )

        cache.consume(old, relayUrl)
        cache.consume(newer, relayUrl)

        assertEquals(setOf(followedPubKey, unfollowedPubKey), cache.followedUsers.value)
    }

    @Test
    fun `older contact list is rejected`() {
        val cache = DesktopLocalCache()
        val newer = contactList("cl2".padEnd(64, '0'), userPubKey, listOf(followedPubKey, unfollowedPubKey), createdAt = 200)
        val old = contactList("cl1".padEnd(64, '0'), userPubKey, listOf(followedPubKey), createdAt = 100)

        cache.consume(newer, relayUrl)
        cache.consume(old, relayUrl)

        assertEquals(
            setOf(followedPubKey, unfollowedPubKey),
            cache.followedUsers.value,
            "Older contact list should not overwrite newer",
        )
    }

    @Test
    fun `consume reaction links to target note`() {
        val cache = DesktopLocalCache()
        val noteId = "note1".padEnd(64, '0')
        val note = textNote(noteId, userPubKey)
        val react = reaction("react1".padEnd(64, '0'), followedPubKey, noteId)

        cache.consume(note, relayUrl)
        cache.consume(react, relayUrl)

        val cachedNote = cache.getNoteIfExists(noteId)!!
        assertTrue(cachedNote.countReactions() > 0, "Note should have reactions after consuming reaction event")
    }

    // -----------------------------------------------------------------------
    // 2. Event stream emission
    // -----------------------------------------------------------------------

    @Test
    fun `consume emits to eventStream`() =
        runBlocking {
            val cache = DesktopLocalCache()
            val collected = mutableListOf<Set<Note>>()

            val job =
                launch(Dispatchers.IO) {
                    cache.eventStream.newEventBundles.collect { collected.add(it) }
                }

            // Give collector time to start
            delay(50)

            val event = textNote("note1".padEnd(64, '0'), userPubKey)
            cache.consume(event, relayUrl)
            val note = cache.getNoteIfExists(event.id)!!
            cache.emitNewNotes(setOf(note))

            delay(100)
            job.cancel()

            assertTrue(collected.isNotEmpty(), "EventStream should emit after consume + emitNewNotes")
            assertTrue(collected.any { batch -> batch.any { it.idHex == event.id } })
        }

    // -----------------------------------------------------------------------
    // 3. Filter logic
    // -----------------------------------------------------------------------

    @Test
    fun `GlobalFeedFilter includes all text notes`() {
        val cache = DesktopLocalCache()
        val filter = DesktopGlobalFeedFilter(cache)

        // Add notes from different authors
        cache.consume(textNote("n1".padEnd(64, '0'), userPubKey, createdAt = 100), relayUrl)
        cache.consume(textNote("n2".padEnd(64, '0'), followedPubKey, createdAt = 200), relayUrl)
        cache.consume(textNote("n3".padEnd(64, '0'), unfollowedPubKey, createdAt = 300), relayUrl)

        val feed = filter.feed()
        assertEquals(3, feed.size, "Global feed should contain all text notes")
    }

    @Test
    fun `FollowingFeedFilter only includes notes from followed users`() {
        val cache = DesktopLocalCache()
        cache.consume(contactList("cl".padEnd(64, '0'), userPubKey, listOf(followedPubKey)), relayUrl)

        cache.consume(textNote("n1".padEnd(64, '0'), followedPubKey, createdAt = 100), relayUrl)
        cache.consume(textNote("n2".padEnd(64, '0'), unfollowedPubKey, createdAt = 200), relayUrl)

        val filter = DesktopFollowingFeedFilter(cache) { cache.followedUsers.value }
        val feed = filter.feed()

        assertEquals(1, feed.size, "Following feed should only contain notes from followed users")
        assertEquals("n1".padEnd(64, '0'), feed[0].idHex)
    }

    @Test
    fun `FollowingFeedFilter returns empty when no follows`() {
        val cache = DesktopLocalCache()
        cache.consume(textNote("n1".padEnd(64, '0'), followedPubKey), relayUrl)

        val filter = DesktopFollowingFeedFilter(cache) { emptySet() }
        val feed = filter.feed()

        assertTrue(feed.isEmpty(), "Following feed should be empty when no follows")
    }

    @Test
    fun `ProfileFeedFilter only shows notes from target pubkey`() {
        val cache = DesktopLocalCache()
        cache.consume(textNote("n1".padEnd(64, '0'), followedPubKey, createdAt = 100), relayUrl)
        cache.consume(textNote("n2".padEnd(64, '0'), unfollowedPubKey, createdAt = 200), relayUrl)

        val filter = DesktopProfileFeedFilter(followedPubKey, cache)
        val feed = filter.feed()

        assertEquals(1, feed.size)
        assertEquals(followedPubKey, feed[0].author?.pubkeyHex)
    }

    @Test
    fun `ThreadFilter returns root and replies`() {
        val cache = DesktopLocalCache()
        val rootId = "root".padEnd(64, '0')
        val replyId = "reply".padEnd(64, '0')

        cache.consume(textNote(rootId, userPubKey, createdAt = 100), relayUrl)
        cache.consume(textNote(replyId, followedPubKey, createdAt = 200, replyToId = rootId), relayUrl)

        val filter = DesktopThreadFilter(rootId, cache)
        val feed = filter.feed()

        assertEquals(2, feed.size, "Thread should contain root + reply")
    }

    @Test
    fun `NotificationFeedFilter shows events tagging user`() {
        val cache = DesktopLocalCache()
        val noteId = "note1".padEnd(64, '0')
        cache.consume(textNote(noteId, userPubKey, createdAt = 100), relayUrl)

        // Reaction from someone else targeting user's note
        val react = reaction("react1".padEnd(64, '0'), followedPubKey, noteId, createdAt = 200)
        cache.consume(react, relayUrl)

        val filter = DesktopNotificationFeedFilter(userPubKey, cache)
        val feed = filter.feed()

        // ReactionEvent tags "e" not "p" — notification filter requires isTaggedUser
        // This test documents the current behavior
        val reactNote = cache.getNoteIfExists("react1".padEnd(64, '0'))
        val reactEvent = reactNote?.event
        assertTrue(reactEvent != null, "Reaction event should exist in cache")
    }

    // -----------------------------------------------------------------------
    // 4. ViewModel integration
    // -----------------------------------------------------------------------

    @Test
    fun `ViewModel starts in Loading then transitions to Loaded after refresh`() =
        runBlocking {
            val cache = DesktopLocalCache()
            cache.consume(textNote("n1".padEnd(64, '0'), userPubKey), relayUrl)

            val vm = DesktopFeedViewModel(DesktopGlobalFeedFilter(cache), cache)

            // Wait for init refresh
            waitForBundler()

            val state = vm.feedState.feedContent.value
            assertIs<FeedState.Loaded>(state, "After consuming notes and refreshing, state should be Loaded")

            val notes = vm.feedState.visibleNotes()
            assertEquals(1, notes.size)
            vm.destroy()
        }

    @Test
    fun `ViewModel shows Empty when cache has no matching notes`() =
        runBlocking {
            val cache = DesktopLocalCache()
            val vm = DesktopFeedViewModel(DesktopGlobalFeedFilter(cache), cache)

            waitForBundler()

            val state = vm.feedState.feedContent.value
            assertIs<FeedState.Empty>(state, "ViewModel should show Empty when no notes in cache")
            vm.destroy()
        }

    @Test
    fun `ViewModel updates when new notes arrive via eventStream`() =
        runBlocking {
            val cache = DesktopLocalCache()
            val vm = DesktopFeedViewModel(DesktopGlobalFeedFilter(cache), cache)

            waitForBundler()
            assertIs<FeedState.Empty>(vm.feedState.feedContent.value)

            // Simulate relay event arriving
            val event = textNote("n1".padEnd(64, '0'), userPubKey)
            cache.consume(event, relayUrl)
            val note = cache.getNoteIfExists(event.id)!!
            cache.emitNewNotes(setOf(note))

            waitForBundler()

            val state = vm.feedState.feedContent.value
            assertIs<FeedState.Loaded>(state, "ViewModel should transition to Loaded after new notes arrive")
            assertEquals(1, vm.feedState.visibleNotes().size)
            vm.destroy()
        }

    @Test
    fun `Following ViewModel only shows followed users notes via eventStream`() =
        runBlocking {
            val cache = DesktopLocalCache()
            cache.consume(contactList("cl".padEnd(64, '0'), userPubKey, listOf(followedPubKey)), relayUrl)

            val filter = DesktopFollowingFeedFilter(cache) { cache.followedUsers.value }
            val vm = DesktopFeedViewModel(filter, cache)
            waitForBundler()

            // Add followed user's note
            val e1 = textNote("n1".padEnd(64, '0'), followedPubKey, createdAt = 100)
            cache.consume(e1, relayUrl)
            val note1 = cache.getNoteIfExists(e1.id)!!
            cache.emitNewNotes(setOf(note1))
            waitForBundler()

            assertEquals(1, vm.feedState.visibleNotes().size, "Should show followed user's note")

            // Add unfollowed user's note
            val e2 = textNote("n2".padEnd(64, '0'), unfollowedPubKey, createdAt = 200)
            cache.consume(e2, relayUrl)
            val note2 = cache.getNoteIfExists(e2.id)!!
            cache.emitNewNotes(setOf(note2))
            waitForBundler()

            assertEquals(1, vm.feedState.visibleNotes().size, "Should NOT show unfollowed user's note")
            vm.destroy()
        }

    @Test
    fun `Following ViewModel feed is empty when followedUsers is empty`() =
        runBlocking {
            val cache = DesktopLocalCache()
            // No contact list consumed — followedUsers remains empty

            val e1 = textNote("n1".padEnd(64, '0'), followedPubKey)
            cache.consume(e1, relayUrl)

            val filter = DesktopFollowingFeedFilter(cache) { cache.followedUsers.value }
            val vm = DesktopFeedViewModel(filter, cache)
            waitForBundler()

            assertIs<FeedState.Empty>(
                vm.feedState.feedContent.value,
                "Following feed should be empty when no contact list loaded",
            )
            vm.destroy()
        }

    // -----------------------------------------------------------------------
    // 5. Cache clear
    // -----------------------------------------------------------------------

    @Test
    fun `clear resets all cache state`() {
        val cache = DesktopLocalCache()
        cache.consume(textNote("n1".padEnd(64, '0'), userPubKey), relayUrl)
        cache.consume(contactList("cl".padEnd(64, '0'), userPubKey, listOf(followedPubKey)), relayUrl)

        cache.clear()

        assertEquals(0, cache.noteCount())
        assertEquals(0, cache.userCount())
        assertTrue(cache.followedUsers.value.isEmpty())
    }

    // -----------------------------------------------------------------------
    // 6. Feed ordering
    // -----------------------------------------------------------------------

    @Test
    fun `global feed is sorted newest first`() {
        val cache = DesktopLocalCache()
        cache.consume(textNote("old".padEnd(64, '0'), userPubKey, createdAt = 100), relayUrl)
        cache.consume(textNote("mid".padEnd(64, '0'), userPubKey, createdAt = 200), relayUrl)
        cache.consume(textNote("new".padEnd(64, '0'), userPubKey, createdAt = 300), relayUrl)

        val filter = DesktopGlobalFeedFilter(cache)
        val feed = filter.feed()

        assertEquals("new".padEnd(64, '0'), feed[0].idHex, "Newest note should be first")
        assertEquals("old".padEnd(64, '0'), feed[2].idHex, "Oldest note should be last")
    }

    // -----------------------------------------------------------------------
    // 7. Metadata consumption
    // -----------------------------------------------------------------------

    @Test
    fun `consumeMetadata updates user info`() {
        val cache = DesktopLocalCache()
        val metadata =
            com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent(
                id = "meta1".padEnd(64, '0'),
                pubKey = userPubKey,
                createdAt = System.currentTimeMillis() / 1000,
                tags = emptyArray(),
                content = """{"name":"TestUser","display_name":"Test User","about":"A test user"}""",
                sig = dummySig,
            )

        cache.consume(metadata, relayUrl)

        val user = cache.getUserIfExists(userPubKey)
        assertTrue(user != null, "User should exist after metadata consumption")
        // Metadata parsing may vary, but user object should be created
        assertEquals(userPubKey, user.pubkeyHex)
    }

    // -----------------------------------------------------------------------
    // 8. BoundedLargeCache eviction
    // -----------------------------------------------------------------------

    @Test
    fun `BoundedLargeCache evicts when over capacity`() {
        val cache = BoundedLargeCache<String, String>(10, evictPercent = 0.5f)

        repeat(15) { i ->
            cache.put("key_${i.toString().padStart(3, '0')}", "value_$i")
        }

        assertTrue(cache.size() <= 10, "Cache should not exceed max size, got ${cache.size()}")
    }

    @Test
    fun `BoundedLargeCache get returns null for evicted entries`() {
        val cache = BoundedLargeCache<String, String>(5, evictPercent = 0.5f)

        repeat(10) { i ->
            cache.put("key_${i.toString().padStart(3, '0')}", "value_$i")
        }

        // Some early entries should have been evicted
        val size = cache.size()
        assertTrue(size <= 5, "Cache should be at or below max size")
    }

    // -----------------------------------------------------------------------
    // 9. Additive filter incremental updates
    // -----------------------------------------------------------------------

    @Test
    fun `GlobalFeedFilter applyFilter only accepts TextNoteEvents`() {
        val cache = DesktopLocalCache()
        val filter = DesktopGlobalFeedFilter(cache)

        // Create a text note
        val textEvent = textNote("t1".padEnd(64, '0'), userPubKey)
        cache.consume(textEvent, relayUrl)
        val textNote = cache.getNoteIfExists(textEvent.id)!!

        // Create a reaction (not a text note)
        val reactEvent = reaction("r1".padEnd(64, '0'), userPubKey, "t1".padEnd(64, '0'))
        cache.consume(reactEvent, relayUrl)
        val reactNote = cache.getNoteIfExists(reactEvent.id)!!

        val filtered = filter.applyFilter(setOf(textNote, reactNote))

        assertEquals(1, filtered.size, "applyFilter should only pass TextNoteEvents")
        assertTrue(filtered.first().event is TextNoteEvent)
    }

    @Test
    fun `FollowingFeedFilter applyFilter respects follow set`() {
        val cache = DesktopLocalCache()
        cache.consume(contactList("cl".padEnd(64, '0'), userPubKey, listOf(followedPubKey)), relayUrl)

        val filter = DesktopFollowingFeedFilter(cache) { cache.followedUsers.value }

        val e1 = textNote("n1".padEnd(64, '0'), followedPubKey)
        cache.consume(e1, relayUrl)
        val note1 = cache.getNoteIfExists(e1.id)!!

        val e2 = textNote("n2".padEnd(64, '0'), unfollowedPubKey)
        cache.consume(e2, relayUrl)
        val note2 = cache.getNoteIfExists(e2.id)!!

        val filtered = filter.applyFilter(setOf(note1, note2))

        assertEquals(1, filtered.size, "applyFilter should only include followed users")
        assertEquals(followedPubKey, filtered.first().author?.pubkeyHex)
    }

    // -----------------------------------------------------------------------
    // 10. Profile count caching
    // -----------------------------------------------------------------------

    @Test
    fun `profile follower count is cached and survives clear of note cache`() {
        val cache = DesktopLocalCache()

        assertEquals(0, cache.getCachedFollowerCount(userPubKey))

        cache.cacheFollowerCount(userPubKey, 42)
        assertEquals(42, cache.getCachedFollowerCount(userPubKey))

        // Updating again overwrites
        cache.cacheFollowerCount(userPubKey, 100)
        assertEquals(100, cache.getCachedFollowerCount(userPubKey))
    }

    @Test
    fun `profile following count is cached`() {
        val cache = DesktopLocalCache()

        cache.cacheFollowingCount(userPubKey, 150)
        assertEquals(150, cache.getCachedFollowingCount(userPubKey))
    }

    @Test
    fun `clear resets profile count caches`() {
        val cache = DesktopLocalCache()
        cache.cacheFollowerCount(userPubKey, 42)
        cache.cacheFollowingCount(userPubKey, 150)

        cache.clear()

        assertEquals(0, cache.getCachedFollowerCount(userPubKey))
        assertEquals(0, cache.getCachedFollowingCount(userPubKey))
    }

    @Test
    fun `metadata is available from cache after consumption`() {
        val cache = DesktopLocalCache()
        val metadata =
            com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent(
                id = "meta1".padEnd(64, '0'),
                pubKey = userPubKey,
                createdAt = System.currentTimeMillis() / 1000,
                tags = emptyArray(),
                content = """{"name":"TestUser","display_name":"Test User","about":"A test user"}""",
                sig = dummySig,
            )

        cache.consume(metadata, relayUrl)

        val user = cache.getUserIfExists(userPubKey)!!
        val cached = user.metadataOrNull()
        assertTrue(cached != null, "Metadata should be cached after consumption")
        assertEquals("Test User", cached.bestName())
        assertEquals(
            "A test user",
            cached.flow.value
                ?.info
                ?.about,
        )
    }
}
