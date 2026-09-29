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
package com.vitorpamplona.quartz.nipXXPushNotifications

import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkTarget
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nipXXPushNotifications.deregistration.PushDeregistrationEvent
import com.vitorpamplona.quartz.nipXXPushNotifications.preferences.PushPreferencesEvent
import com.vitorpamplona.quartz.nipXXPushNotifications.registration.PushRegistrationEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class PushNotificationLinksTest {
    private val service = "5".repeat(64)
    private val tags = arrayOf(arrayOf("p", service), arrayOf("app", "divine"))

    @Test
    fun everyPushControlEventLinksItsServiceOnly() {
        val expected = listOf(Link(Relation.NOTIFICATION_SERVER, LinkTarget.User(service), "p"))
        val id = "0".repeat(64)
        val author = "1".repeat(64)
        val sig = "0".repeat(128)
        assertEquals(expected, PushRegistrationEvent(id, author, 1, tags, "ciphertext", sig).links())
        assertEquals(expected, PushDeregistrationEvent(id, author, 1, tags, "ciphertext", sig).links())
        assertEquals(expected, PushPreferencesEvent(id, author, 1, tags, "ciphertext", sig).links())
    }
}
