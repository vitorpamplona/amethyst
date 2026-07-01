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
package com.vitorpamplona.quartz.nip01Core.metadata

import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.utils.nsecToSigner
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
                id = "b7a00acbc8e07b1a555eb0b50b515b54525f129f612b6dc8e3225a2184e79e03",
                pubKey = "70a9b3c312a6b83e476739bd29d60ca700da1d5b982cbca87b5f3d27d4038d67",
                createdAt = 1740669816,
                tags =
                    arrayOf(
                        arrayOf("name", "Vitor"),
                    ),
                content = "{\"name\":\"Vitor\"}",
                sig = "fcb319f3f4ac616d14186a018b40d554f4614bfdddc15079be7027d2059d6cd76d12f0f2ac4fd0ca3694b95ec66b786d6503e350249e9d35d2caa0c39e77fc0a",
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
                id = "e09f5a9f0005bc427ac0c1714c7c239ecb7f2454c8b29c2c5046477c5d945c6c",
                pubKey = "70a9b3c312a6b83e476739bd29d60ca700da1d5b982cbca87b5f3d27d4038d67",
                createdAt = 1740669816,
                tags =
                    arrayOf(
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
                sig = "dddbe96bd6217bb3fce33f6766cc989b300d7e867aa57bdcfc7c9ad915e165ecc76c193770f215728e7e1f59d667a16365f081d1089be27938f40e69c682e623",
            )

        assertEquals(expected, test)

        val expected2 =
            MetadataEvent(
                id = "58c85c5a5183fcb017be943f83f92dc714b5595078f009afa0b5241ee27e8946",
                pubKey = "70a9b3c312a6b83e476739bd29d60ca700da1d5b982cbca87b5f3d27d4038d67",
                createdAt = 1740669817,
                tags =
                    arrayOf(
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
                sig = "162b7a369bb84070c20a2ed5733a5c4519b3c4d1b0c89042448fe16d3f201e42ed60eb544fa8f83c8b0eed73a4dc51a39267c29e3b0d2c05b713c81157662db0",
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
                id = "75488fae3320aebf9af573d5a43b406c3eb93511e484e2db0a348408c0cce42d",
                pubKey = "70a9b3c312a6b83e476739bd29d60ca700da1d5b982cbca87b5f3d27d4038d67",
                createdAt = 1740669817,
                tags =
                    arrayOf(
                        arrayOf("name", "2 Vitor"),
                        arrayOf("picture", "2 https://vitorpamplona.com/images/me_300.jpg"),
                        arrayOf("banner", "2 https://pbs.twimg.com/profile_banners/15064756/1414451651/1080x360"),
                    ),
                content = "{\"name\":\"2 Vitor\",\"picture\":\"2 https://vitorpamplona.com/images/me_300.jpg\",\"banner\":\"2 https://pbs.twimg.com/profile_banners/15064756/1414451651/1080x360\"}",
                sig = "775f6a90c7152632b10c4d20779d9648fd8fec45966add84ef721e58d355ce5c5e1f48b81f334c1b31236c6afe076f51e191f1cf58819c62b6fd64cae04ef01a",
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

    @Test
    fun clinkOfferRoundTrip() {
        val noffer = "noffer1qqsxexampleclinkofferpointer"
        val event = signer.sign(MetadataEvent.createNew(name = "Vitor", clinkOffer = noffer, createdAt = 1740669816))

        // written into kind-0 content under the spec's `clink_offer` key
        assertEquals(true, event.content.contains("\"clink_offer\":\"$noffer\""))

        // and dual-written as a kind-0 tag (NIP-1770 pattern), like the other fields
        assertContentEquals(arrayOf("clink_offer", noffer), event.tags.firstOrNull { it.firstOrNull() == "clink_offer" })

        // and parses back out of the content
        val metadata = event.contactMetaData()
        assertNotNull(metadata)
        assertEquals(noffer, metadata.clinkOffer)
        assertEquals(noffer, metadata.clinkOffer())
    }

    @Test
    fun parseClinkOffer() {
        val metadata = JsonMapper.fromJson<UserMetadata>("""{"name":"Test","clink_offer":"noffer1abc"}""")
        assertEquals("noffer1abc", metadata.clinkOffer)
    }

    @Test
    fun parseBirthdayFull() {
        val json = """{"name":"Test","birthday":{"year":1990,"month":6,"day":15}}"""
        val metadata = JsonMapper.fromJson<UserMetadata>(json)
        assertNotNull(metadata.birthday)
        assertEquals(1990, metadata.birthday!!.year)
        assertEquals(6, metadata.birthday!!.month)
        assertEquals(15, metadata.birthday!!.day)
    }

    @Test
    fun parseBirthdayPartial() {
        val json = """{"name":"Test","birthday":{"month":6,"day":15}}"""
        val metadata = JsonMapper.fromJson<UserMetadata>(json)
        assertNotNull(metadata.birthday)
        assertNull(metadata.birthday!!.year)
        assertEquals(6, metadata.birthday!!.month)
        assertEquals(15, metadata.birthday!!.day)
    }

    @Test
    fun parseBirthdayAbsent() {
        val json = """{"name":"Test"}"""
        val metadata = JsonMapper.fromJson<UserMetadata>(json)
        assertNull(metadata.birthday)
    }

    @Test
    fun birthdayRoundTrip() {
        val json = """{"name":"Test","birthday":{"year":1990,"month":6,"day":15}}"""
        val metadata = JsonMapper.fromJson<UserMetadata>(json)
        val serialized = JsonMapper.toJson(metadata)
        val reparsed = JsonMapper.fromJson<UserMetadata>(serialized)
        assertNotNull(reparsed.birthday)
        assertEquals(1990, reparsed.birthday!!.year)
        assertEquals(6, reparsed.birthday!!.month)
        assertEquals(15, reparsed.birthday!!.day)
    }
}
