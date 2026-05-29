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
package com.vitorpamplona.quartz.nipF4Podcasts

import com.vitorpamplona.quartz.nip51Lists.muteList.tags.UserTag
import com.vitorpamplona.quartz.nipF4Podcasts.authored.AuthoredPodcastsEvent
import com.vitorpamplona.quartz.utils.DeterministicSigner
import com.vitorpamplona.quartz.utils.nsecToKeyPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthoredPodcastsEventTest {
    private val signer =
        DeterministicSigner(
            "nsec10g0wheggqn9dawlc0yuv6adnat6n09anr7eyykevw2dm8xa5fffs0wsdsr".nsecToKeyPair(),
        )

    private val podcast1 = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
    private val podcast2 = "32e1827635450ebb3c5a7d12c1f8e7b2b514439ac10a67eef3d9fd9c5c68e245"
    private val unrelated = "0000000000000000000000000000000000000000000000000000000000000001"

    @Test
    fun `kind is 10064`() {
        assertEquals(10064, AuthoredPodcastsEvent.KIND)
    }

    @Test
    fun `build round-trips podcast pubkeys`() {
        val template =
            AuthoredPodcastsEvent.build(
                podcasts = listOf(UserTag(podcast1), UserTag(podcast2)),
            )
        val event = signer.sign<AuthoredPodcastsEvent>(template)

        assertEquals(listOf(podcast1, podcast2), event.authoredKeys())
        assertTrue(event.authors(podcast1))
        assertTrue(event.authors(podcast2))
        assertFalse(event.authors(unrelated))
    }

    @Test
    fun `is replaceable with empty d-tag`() {
        val template = AuthoredPodcastsEvent.build(emptyList())
        val event = signer.sign<AuthoredPodcastsEvent>(template)

        assertEquals("", event.dTag())
    }

    @Test
    fun `linkedPubKeys returns all authored podcast keys`() {
        val template =
            AuthoredPodcastsEvent.build(
                podcasts = listOf(UserTag(podcast1), UserTag(podcast2)),
            )
        val event = signer.sign<AuthoredPodcastsEvent>(template)

        assertEquals(setOf(podcast1, podcast2), event.linkedPubKeys().toSet())
    }
}
