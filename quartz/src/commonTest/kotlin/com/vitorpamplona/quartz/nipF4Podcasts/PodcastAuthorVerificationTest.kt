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
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.PodcastMetadataEvent
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.tags.AuthorTag
import com.vitorpamplona.quartz.utils.DeterministicSigner
import com.vitorpamplona.quartz.utils.nsecToKeyPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The cross-check the UI uses to mark a podcast's claimed author "verified": a show (kind:10154)
 * names authors via `p` tags (unverified), and an author's own counter-claim (kind:10064) lists the
 * podcasts they actually author. Verified iff the author's 10064 lists this podcast's pubkey.
 */
class PodcastAuthorVerificationTest {
    private val podcastSigner =
        DeterministicSigner("nsec10g0wheggqn9dawlc0yuv6adnat6n09anr7eyykevw2dm8xa5fffs0wsdsr".nsecToKeyPair())
    private val authorSigner =
        DeterministicSigner("nsec1vl029mgpspedva04g90vltkh6fvh240zqtv9k0t9af8935ke9laqsnlfe5".nsecToKeyPair())

    @Test
    fun `claimed author with a matching 10064 counter-claim verifies`() {
        val authorPubkey = authorSigner.pubKey
        val metadata =
            podcastSigner.sign<PodcastMetadataEvent>(
                PodcastMetadataEvent.build(
                    title = "My Show",
                    image = "https://x/cover.jpg",
                    description = "desc",
                    authors = listOf(AuthorTag(authorPubkey, AuthorTag.ROLE_HOST)),
                ),
            )
        val podcastPubkey = metadata.pubKey

        val claimed = metadata.claimedAuthors().single()
        assertEquals(authorPubkey, claimed.pubKey)
        assertEquals(AuthorTag.ROLE_HOST, claimed.role)

        // The author's own 10064 lists this podcast -> the show's claim is verified.
        val authored = authorSigner.sign<AuthoredPodcastsEvent>(AuthoredPodcastsEvent.build(listOf(UserTag(podcastPubkey))))
        assertTrue(authored.authors(podcastPubkey))
    }

    @Test
    fun `author whose 10064 omits the podcast is not verified`() {
        val unrelated = "0000000000000000000000000000000000000000000000000000000000000001"
        val authored = authorSigner.sign<AuthoredPodcastsEvent>(AuthoredPodcastsEvent.build(listOf(UserTag(unrelated))))
        assertFalse(authored.authors(podcastSigner.pubKey))
    }
}
