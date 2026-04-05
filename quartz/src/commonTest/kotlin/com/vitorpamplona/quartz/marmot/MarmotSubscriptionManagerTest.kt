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
package com.vitorpamplona.quartz.marmot

import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for MarmotSubscriptionManager.
 */
class MarmotSubscriptionManagerTest {
    private val userPubKey = "a".repeat(64)
    private val groupId1 = "b".repeat(64)
    private val groupId2 = "c".repeat(64)

    @Test
    fun testSubscribeGroup() {
        val manager = MarmotSubscriptionManager(userPubKey)

        manager.subscribeGroup(groupId1)

        assertTrue(manager.isSubscribed(groupId1))
        assertEquals(setOf(groupId1), manager.activeGroupIds())
    }

    @Test
    fun testSubscribeGroupWithSince() {
        val manager = MarmotSubscriptionManager(userPubKey)
        val since = 1700000000L

        manager.subscribeGroup(groupId1, since)

        assertTrue(manager.isSubscribed(groupId1))

        val filters = manager.activeGroupFilters()
        assertEquals(1, filters.size)
        assertEquals(since, filters[0].since)
    }

    @Test
    fun testUnsubscribeGroup() {
        val manager = MarmotSubscriptionManager(userPubKey)

        manager.subscribeGroup(groupId1)
        manager.unsubscribeGroup(groupId1)

        assertFalse(manager.isSubscribed(groupId1))
        assertTrue(manager.activeGroupIds().isEmpty())
    }

    @Test
    fun testMultipleGroups() {
        val manager = MarmotSubscriptionManager(userPubKey)

        manager.subscribeGroup(groupId1)
        manager.subscribeGroup(groupId2)

        assertEquals(setOf(groupId1, groupId2), manager.activeGroupIds())

        val filters = manager.activeGroupFilters()
        assertEquals(2, filters.size)
    }

    @Test
    fun testUpdateGroupSince() {
        val manager = MarmotSubscriptionManager(userPubKey)
        val newSince = 1700000000L

        manager.subscribeGroup(groupId1)
        manager.updateGroupSince(groupId1, newSince)

        val filters = manager.activeGroupFilters()
        assertEquals(1, filters.size)
        assertEquals(newSince, filters[0].since)
    }

    @Test
    fun testGiftWrapFilter() {
        val manager = MarmotSubscriptionManager(userPubKey)
        val filter = manager.giftWrapFilter()

        assertEquals(listOf(GiftWrapEvent.KIND), filter.kinds)
        assertNotNull(filter.tags)
        assertEquals(listOf(userPubKey), filter.tags!!["p"])
        assertNull(filter.since)
    }

    @Test
    fun testGiftWrapFilterWithSince() {
        val manager = MarmotSubscriptionManager(userPubKey)
        val since = 1700000000L

        manager.updateGiftWrapSince(since)
        val filter = manager.giftWrapFilter()

        assertEquals(since, filter.since)
    }

    @Test
    fun testActiveGroupFiltersContainCorrectKind() {
        val manager = MarmotSubscriptionManager(userPubKey)

        manager.subscribeGroup(groupId1)
        val filters = manager.activeGroupFilters()

        assertEquals(1, filters.size)
        assertEquals(listOf(GroupEvent.KIND), filters[0].kinds)
        assertNotNull(filters[0].tags)
        assertEquals(listOf(groupId1), filters[0].tags!!["h"])
    }

    @Test
    fun testBuildFiltersIncludesBothTypes() {
        val manager = MarmotSubscriptionManager(userPubKey)

        manager.subscribeGroup(groupId1)
        val allFilters = manager.buildFilters()

        // Should have 1 group filter + 1 gift wrap filter
        assertEquals(2, allFilters.size)
    }

    @Test
    fun testBuildFiltersWithNoGroupsHasGiftWrapOnly() {
        val manager = MarmotSubscriptionManager(userPubKey)
        val allFilters = manager.buildFilters()

        // Only the gift wrap filter
        assertEquals(1, allFilters.size)
        assertEquals(listOf(GiftWrapEvent.KIND), allFilters[0].kinds)
    }

    @Test
    fun testKeyPackageFilter() {
        val manager = MarmotSubscriptionManager(userPubKey)
        val targetPubKey = "d".repeat(64)

        val filter = manager.keyPackageFilter(targetPubKey)
        assertEquals(listOf(targetPubKey), filter.authors)
    }

    @Test
    fun testSyncWithGroupManager() {
        val manager = MarmotSubscriptionManager(userPubKey)

        // Start with one group
        manager.subscribeGroup(groupId1)

        // Sync with group manager that has different groups
        manager.syncWithGroupManager(setOf(groupId2))

        // groupId1 should be removed, groupId2 added
        assertFalse(manager.isSubscribed(groupId1))
        assertTrue(manager.isSubscribed(groupId2))
    }

    @Test
    fun testClear() {
        val manager = MarmotSubscriptionManager(userPubKey)

        manager.subscribeGroup(groupId1)
        manager.subscribeGroup(groupId2)
        manager.updateGiftWrapSince(1700000000L)

        manager.clear()

        assertTrue(manager.activeGroupIds().isEmpty())
        assertNull(manager.giftWrapFilter().since)
    }
}
