/**
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
package com.vitorpamplona.quartz.nip01Core.metadata

import com.vitorpamplona.quartz.utils.nsecToSigner
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class UpdateMetadataTest {
    val signer = "nsec10g0wheggqn9dawlc0yuv6adnat6n09anr7eyykevw2dm8xa5fffs0wsdsr".nsecToSigner()

    fun assertEquals(
        expected: MetadataEvent,
        test: MetadataEvent,
    ) {
        assertEquals(expected.id, test.id)
        assertEquals(expected.pubKey, test.pubKey)
        assertEquals(expected.createdAt, test.createdAt)
        assertEquals(expected.kind, test.kind)
        assertEquals(expected.tags.size, test.tags.size)
        repeat(expected.tags.size) {
            assertContentEquals(expected.tags[it], test.tags[it])
        }
        assertEquals(expected.content, test.content)
        assertEquals(expected.sig, test.sig)
    }

    @Test
    fun createNewMetadata() {
        val test = signer.sign(MetadataEvent.createNew("Vitor", createdAt = 1740669816))

        val expected =
            MetadataEvent(
                id = "490d7439e530423f2540d4f2bdb73a0a2935f3df9e1f2a6f699a140c7db311fe",
                pubKey = "70a9b3c312a6b83e476739bd29d60ca700da1d5b982cbca87b5f3d27d4038d67",
                createdAt = 1740669816,
                tags =
                    arrayOf(
                        arrayOf("alt", "User profile for Vitor"),
                        arrayOf("name", "Vitor"),
                    ),
                content = "{\"name\":\"Vitor\"}",
                sig = "977a6152199f17d103d8d56736ed1b7767054464cf9423d017c01c8cdd2344698f0a5e13da8dff98d01bb1f798837e3b6271e1fd1cac861bb90686f622ae6ef4",
            )

        assertEquals(expected, test)
    }

    @Test
    fun updateMetadata() {
        val test =
            signer.sign(
                MetadataEvent.createNew(
                    name = "Vitor",
                    displayName = "Vitor Pamplona",
                    about = "Nostr's Chief Android Officer - #Amethyst",
                    picture = "https://vitorpamplona.com/images/me_300.jpg",
                    banner = "https://pbs.twimg.com/profile_banners/15064756/1414451651/1080x360",
                    pronouns = "he/him",
                    website = "https://vitorpamplona.com",
                    nip05 = "_@vitorpamplona.com",
                    lnAddress = "vitor@vitorpamplona.com",
                    lnURL = "TEST",
                    github = "https://gist.github.com/vitorpamplona/cf19e2d1d7f8dac6348ad37b35ec8421",
                    createdAt = 1740669816,
                ),
            )

        val expected =
            MetadataEvent(
                id = "2b2761d66db4a83d5fb7a98cafb8414e9b0c238ceb67d729bedacc1a092d516f",
                pubKey = "70a9b3c312a6b83e476739bd29d60ca700da1d5b982cbca87b5f3d27d4038d67",
                createdAt = 1740669816,
                tags =
                    arrayOf(
                        arrayOf("alt", "User profile for Vitor"),
                        arrayOf("name", "Vitor"),
                        arrayOf("display_name", "Vitor Pamplona"),
                        arrayOf("picture", "https://vitorpamplona.com/images/me_300.jpg"),
                        arrayOf("banner", "https://pbs.twimg.com/profile_banners/15064756/1414451651/1080x360"),
                        arrayOf("website", "https://vitorpamplona.com"),
                        arrayOf("pronouns", "he/him"),
                        arrayOf("about", "Nostr's Chief Android Officer - #Amethyst"),
                        arrayOf("nip05", "_@vitorpamplona.com"),
                        arrayOf("lud16", "vitor@vitorpamplona.com"),
                        arrayOf("lud06", "TEST"),
                        arrayOf("i", "github:vitorpamplona", "cf19e2d1d7f8dac6348ad37b35ec8421"),
                    ),
                content = "{\"name\":\"Vitor\",\"display_name\":\"Vitor Pamplona\",\"picture\":\"https://vitorpamplona.com/images/me_300.jpg\",\"banner\":\"https://pbs.twimg.com/profile_banners/15064756/1414451651/1080x360\",\"website\":\"https://vitorpamplona.com\",\"pronouns\":\"he/him\",\"about\":\"Nostr's Chief Android Officer - #Amethyst\",\"nip05\":\"_@vitorpamplona.com\",\"lud16\":\"vitor@vitorpamplona.com\",\"lud06\":\"TEST\"}",
                sig = "0a8c78eb0c5e0ba46e4781cc445fb7b6d275b434cead9231bd19b4f95671e3ab872264e50d4456d6036a84cc81e517bfa24229571519aabefcbce431e0c7163e",
            )

        assertEquals(expected, test)

        val expected2 =
            MetadataEvent(
                id = "2e1e57fae4e4baddac025ea0b49afc093f2aa27610a05e584184ed26b29d7590",
                pubKey = "70a9b3c312a6b83e476739bd29d60ca700da1d5b982cbca87b5f3d27d4038d67",
                createdAt = 1740669817,
                tags =
                    arrayOf(
                        arrayOf("alt", "User profile for 2 Vitor"),
                        arrayOf("name", "2 Vitor"),
                        arrayOf("display_name", "2 Vitor Pamplona"),
                        arrayOf("picture", "2 https://vitorpamplona.com/images/me_300.jpg"),
                        arrayOf("banner", "2 https://pbs.twimg.com/profile_banners/15064756/1414451651/1080x360"),
                        arrayOf("website", "2 https://vitorpamplona.com"),
                        arrayOf("pronouns", "2 he/him"),
                        arrayOf("about", "2 Nostr's Chief Android Officer - #Amethyst"),
                        arrayOf("nip05", "2 _@vitorpamplona.com"),
                        arrayOf("lud16", "2 vitor@vitorpamplona.com"),
                        arrayOf("lud06", "2 TEST"),
                        arrayOf("i", "github:vitorpamplona", "2cf19e2d1d7f8dac6348ad37b35ec8421"),
                    ),
                content = "{\"name\":\"2 Vitor\",\"display_name\":\"2 Vitor Pamplona\",\"picture\":\"2 https://vitorpamplona.com/images/me_300.jpg\",\"banner\":\"2 https://pbs.twimg.com/profile_banners/15064756/1414451651/1080x360\",\"website\":\"2 https://vitorpamplona.com\",\"pronouns\":\"2 he/him\",\"about\":\"2 Nostr's Chief Android Officer - #Amethyst\",\"nip05\":\"2 _@vitorpamplona.com\",\"lud16\":\"2 vitor@vitorpamplona.com\",\"lud06\":\"2 TEST\"}",
                sig = "a25483bc0fcc79ccd337e3ff846351097109fc13f1cd1c9cc15f7f2ad46417aeb7c05c1524aeadbab93036fc0743c8c310a5b2b7b482e1808649b12a3ee29d49",
            )

        val test2 =
            signer.sign(
                MetadataEvent.updateFromPast(
                    latest = test,
                    name = "2 Vitor",
                    displayName = "2 Vitor Pamplona",
                    about = "2 Nostr's Chief Android Officer - #Amethyst",
                    picture = "2 https://vitorpamplona.com/images/me_300.jpg",
                    banner = "2 https://pbs.twimg.com/profile_banners/15064756/1414451651/1080x360",
                    pronouns = "2 he/him",
                    website = "2 https://vitorpamplona.com",
                    nip05 = "2 _@vitorpamplona.com",
                    lnAddress = "2 vitor@vitorpamplona.com",
                    lnURL = "2 TEST",
                    github = "https://gist.github.com/vitorpamplona/2cf19e2d1d7f8dac6348ad37b35ec8421",
                    createdAt = 1740669817,
                ),
            )

        assertEquals(expected2, test2)

        val expected3 =
            MetadataEvent(
                id = "94879b9a27fecf1337ede32013006ec4dfd5a3286a1e819abddfa1c3c132f008",
                pubKey = "70a9b3c312a6b83e476739bd29d60ca700da1d5b982cbca87b5f3d27d4038d67",
                createdAt = 1740669817,
                tags =
                    arrayOf(
                        arrayOf("alt", "User profile for 2 Vitor"),
                        arrayOf("name", "2 Vitor"),
                        arrayOf("picture", "2 https://vitorpamplona.com/images/me_300.jpg"),
                        arrayOf("banner", "2 https://pbs.twimg.com/profile_banners/15064756/1414451651/1080x360"),
                    ),
                content = "{\"name\":\"2 Vitor\",\"picture\":\"2 https://vitorpamplona.com/images/me_300.jpg\",\"banner\":\"2 https://pbs.twimg.com/profile_banners/15064756/1414451651/1080x360\"}",
                sig = "729ee02364b4d429b6a66400ec850e80424602e3981ff2ad8a14d61bdee04f76ec68ddc4a0f38b0527f0218f7fa67668012a5c0f3c8ef943517b504040caa07e",
            )

        val test3 =
            signer.sign(
                MetadataEvent.updateFromPast(
                    latest = test2,
                    name = null,
                    displayName = "",
                    about = "",
                    picture = null,
                    banner = null,
                    pronouns = "",
                    website = "",
                    nip05 = "",
                    lnAddress = "",
                    lnURL = "",
                    github = "",
                    createdAt = 1740669817,
                ),
            )

        assertEquals(expected3, test3)
    }
}
