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
package com.vitorpamplona.amethyst.model

import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Muted public chats ride inside the NIP-78 AppSpecificData blob so they reach the
 * user's other devices.
 *
 * The nullable default is the load-bearing part. `updateFrom` overwrites local state
 * whenever remote differs, and AppSpecificState replays the cached backup event on
 * every app start — so if an older client rewrote the blob and dropped the key, a
 * non-null `emptyList()` default would clear the local mute set on every single
 * launch. `null` keeps "key absent" distinguishable from "explicitly empty".
 */
class MutedPublicChatsSyncTest {
    private val channelA = "a".repeat(64)
    private val channelB = "b".repeat(64)

    @Test
    fun blobWrittenWithoutTheFieldDecodesToNull() {
        val json = """{"pinnedRooms":[]}"""
        val decoded = JsonMapper.fromJson<AccountChatPreferencesInternal>(json)
        assertNull(decoded.mutedPublicChats)
    }

    @Test
    fun explicitlyEmptyListDecodesToEmptyNotNull() {
        val json = """{"pinnedRooms":[],"mutedPublicChats":[]}"""
        val decoded = JsonMapper.fromJson<AccountChatPreferencesInternal>(json)
        assertEquals(emptyList<String>(), decoded.mutedPublicChats)
    }

    @Test
    fun jsonRoundTripPreservesMutedChats() {
        val internal = AccountChatPreferencesInternal(emptyList(), listOf(channelA, channelB))
        val decoded = JsonMapper.fromJson<AccountChatPreferencesInternal>(JsonMapper.toJson(internal))
        assertEquals(listOf(channelA, channelB), decoded.mutedPublicChats)
    }

    @Test
    fun wireShapeIsSortedSoTheBlobIsStable() {
        // Mirrors AccountSyncedSettings.toInternal(): mutedPublicChats.sorted().
        // Two sets with the same members must serialize identically regardless of
        // iteration order, or every settings save republishes a no-op event.
        val oneOrder = setOf(channelB, channelA).sorted()
        val otherOrder = setOf(channelA, channelB).sorted()
        assertEquals(
            JsonMapper.toJson(AccountChatPreferencesInternal(emptyList(), oneOrder)),
            JsonMapper.toJson(AccountChatPreferencesInternal(emptyList(), otherOrder)),
        )
    }
}
