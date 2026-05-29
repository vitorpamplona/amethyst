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

import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.UserTag
import com.vitorpamplona.quartz.nipF4Podcasts.favorites.FavoritePodcastsListEvent
import com.vitorpamplona.quartz.utils.nsecToKeyPair
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FavoritePodcastsListEventTest {
    private val signer =
        NostrSignerInternal(
            "nsec10g0wheggqn9dawlc0yuv6adnat6n09anr7eyykevw2dm8xa5fffs0wsdsr".nsecToKeyPair(),
        )

    private val podcast1 = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
    private val podcast2 = "32e1827635450ebb3c5a7d12c1f8e7b2b514439ac10a67eef3d9fd9c5c68e245"

    @Test
    fun `kind is 10054`() {
        assertEquals(10054, FavoritePodcastsListEvent.KIND)
    }

    @Test
    fun `create with public favorites round-trips`() =
        runTest {
            val event =
                FavoritePodcastsListEvent.create(
                    publicFavorites = listOf(UserTag(podcast1), UserTag(podcast2)),
                    signer = signer,
                    createdAt = 1_700_000_000,
                )

            val pubs = event.publicFavorites()
            assertEquals(2, pubs.size)
            assertEquals(podcast1, pubs[0].pubKey)
            assertEquals(podcast2, pubs[1].pubKey)
        }

    @Test
    fun `create with private favorites hides them from public tags`() =
        runTest {
            val event =
                FavoritePodcastsListEvent.create(
                    privateFavorites = listOf(UserTag(podcast1)),
                    signer = signer,
                    createdAt = 1_700_000_000,
                )

            assertFalse(
                event.tags.any { it.size >= 2 && it[0] == "p" && it[1] == podcast1 },
                "Private favorite must not be exposed as a public p-tag",
            )
            assertTrue(event.content.isNotEmpty(), "Content must hold encrypted private tags")

            val priv = assertNotNull(event.privateFavorites(signer))
            assertEquals(1, priv.size)
            assertEquals(podcast1, priv[0].pubKey)
        }

    @Test
    fun `add public favorite appends to list and dedupes by pubkey`() =
        runTest {
            val first =
                FavoritePodcastsListEvent.create(
                    publicFavorites = listOf(UserTag(podcast1)),
                    signer = signer,
                    createdAt = 1_700_000_000,
                )

            val second =
                FavoritePodcastsListEvent.add(
                    earlierVersion = first,
                    podcast = UserTag(podcast2),
                    isPrivate = false,
                    signer = signer,
                    createdAt = 1_700_000_001,
                )

            assertEquals(
                listOf(podcast1, podcast2),
                second.publicFavorites().map { it.pubKey },
            )

            // Adding podcast1 again must not double it.
            val third =
                FavoritePodcastsListEvent.add(
                    earlierVersion = second,
                    podcast = UserTag(podcast1),
                    isPrivate = false,
                    signer = signer,
                    createdAt = 1_700_000_002,
                )
            assertEquals(
                listOf(podcast2, podcast1),
                third.publicFavorites().map { it.pubKey },
            )
        }

    @Test
    fun `moving public favorite to private strips the public p-tag`() =
        runTest {
            val publicFirst =
                FavoritePodcastsListEvent.create(
                    publicFavorites = listOf(UserTag(podcast1)),
                    signer = signer,
                    createdAt = 1_700_000_000,
                )

            val nowPrivate =
                FavoritePodcastsListEvent.add(
                    earlierVersion = publicFirst,
                    podcast = UserTag(podcast1),
                    isPrivate = true,
                    signer = signer,
                    createdAt = 1_700_000_001,
                )

            assertFalse(
                nowPrivate.tags.any { it.size >= 2 && it[0] == "p" && it[1] == podcast1 },
                "Moving a public favorite to private must strip it from the unencrypted tag list",
            )
            val priv = assertNotNull(nowPrivate.privateFavorites(signer))
            assertEquals(listOf(podcast1), priv.map { it.pubKey })
        }

    @Test
    fun `moving private favorite to public strips the encrypted entry`() =
        runTest {
            val privateFirst =
                FavoritePodcastsListEvent.create(
                    privateFavorites = listOf(UserTag(podcast1)),
                    signer = signer,
                    createdAt = 1_700_000_000,
                )

            val nowPublic =
                FavoritePodcastsListEvent.add(
                    earlierVersion = privateFirst,
                    podcast = UserTag(podcast1),
                    isPrivate = false,
                    signer = signer,
                    createdAt = 1_700_000_001,
                )

            assertTrue(
                nowPublic.tags.any { it.size >= 2 && it[0] == "p" && it[1] == podcast1 },
                "Promoted entry must be present as a public p-tag",
            )
            val priv = assertNotNull(nowPublic.privateFavorites(signer))
            assertTrue(priv.isEmpty(), "Promoted entry must no longer be in the encrypted list")
        }

    @Test
    fun `remove drops the matching pubkey from public tags`() =
        runTest {
            val first =
                FavoritePodcastsListEvent.create(
                    publicFavorites = listOf(UserTag(podcast1), UserTag(podcast2)),
                    signer = signer,
                    createdAt = 1_700_000_000,
                )

            val second =
                FavoritePodcastsListEvent.remove(
                    earlierVersion = first,
                    podcast = UserTag(podcast1),
                    signer = signer,
                    createdAt = 1_700_000_001,
                )

            assertEquals(listOf(podcast2), second.publicFavorites().map { it.pubKey })
        }

    @Test
    fun `alt tag is always present`() =
        runTest {
            val event =
                FavoritePodcastsListEvent.create(
                    publicFavorites = listOf(UserTag(podcast1)),
                    signer = signer,
                    createdAt = 1_700_000_000,
                )

            assertTrue(event.tags.any { it[0] == "alt" && it[1] == FavoritePodcastsListEvent.ALT })
        }
}
