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
package com.vitorpamplona.amethyst.service.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which tray notifications the Notifications tab's last-read marker takes down. The sweep finds
 * them by the group they were posted under, so a group that two categories both claim, or a
 * chat group a tab category claims, would take down a notification the user never read.
 */
class NotificationCategoryTabTest {
    @Test
    fun chatsAndPaymentsAreNotReadOnTheTab() {
        assertFalse(NotificationCategory.DIRECT_MESSAGE.readOnNotificationsTab)
        assertFalse(NotificationCategory.PAYMENT_RECEIVED.readOnNotificationsTab)
    }

    @Test
    fun everyOtherCategoryIsReadOnTheTab() {
        val onTab = NotificationCategory.entries.filter { it.readOnNotificationsTab }.toSet()
        assertEquals(NotificationCategory.entries.toSet() - NotificationCategory.DIRECT_MESSAGE - NotificationCategory.PAYMENT_RECEIVED, onTab)
    }

    @Test
    fun eachGroupBelongsToExactlyOneCategory() {
        NotificationCategory.entries.forEach { category ->
            assertEquals(listOf(category), NotificationCategory.entries.filter { it.owns(category.group) })
        }
    }

    @Test
    fun aReplyThreadGroupBelongsToReplies() {
        val threadGroup = NotificationUtils.replyGroupKeyFor("a".repeat(64))

        assertEquals(listOf(NotificationCategory.REPLY), NotificationCategory.entries.filter { it.owns(threadGroup) })
    }

    @Test
    fun aGroupThatOnlySharesAPrefixIsNotOwned() {
        assertFalse(NotificationCategory.REPLY.owns(NotificationCategory.REPLY.group + "S"))
        assertTrue(NotificationCategory.REPLY.owns(NotificationCategory.REPLY.group + ":root"))
    }
}
