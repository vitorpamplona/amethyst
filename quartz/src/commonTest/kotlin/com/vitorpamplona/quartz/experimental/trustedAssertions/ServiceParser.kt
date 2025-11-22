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
package com.vitorpamplona.quartz.experimental.trustedAssertions

import com.vitorpamplona.quartz.experimental.trustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.experimental.trustedAssertions.list.tags.ServiceProviderTag
import com.vitorpamplona.quartz.experimental.trustedAssertions.list.tags.ServiceType
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlin.test.Test
import kotlin.test.assertEquals

class ServiceParser {
    val brainstorm =
        TrustProviderListEvent(
            id = "d74ce2e62a152e787001f81b51f7f18b69816cbafc064e38af99f1ee55895424",
            pubKey = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c",
            createdAt = 1762635473,
            tags =
                arrayOf(
                    arrayOf("30382:rank", "a49e1bdd16cb0d720cd04c4c4cd9e04c21ea2292121fbbe99f03f4a6c1fd2a98", "wss://nip85.brainstorm.world"),
                    arrayOf("30382:followers", "a49e1bdd16cb0d720cd04c4c4cd9e04c21ea2292121fbbe99f03f4a6c1fd2a98", "wss://nip85.brainstorm.world"),
                    arrayOf("30382:personalizedGrapeRank_influence", "a49e1bdd16cb0d720cd04c4c4cd9e04c21ea2292121fbbe99f03f4a6c1fd2a98", "wss://nip85.brainstorm.world"),
                    arrayOf("30382:personalizedGrapeRank_average", "a49e1bdd16cb0d720cd04c4c4cd9e04c21ea2292121fbbe99f03f4a6c1fd2a98", "wss://nip85.brainstorm.world"),
                    arrayOf("30382:personalizedGrapeRank_confidence", "a49e1bdd16cb0d720cd04c4c4cd9e04c21ea2292121fbbe99f03f4a6c1fd2a98", "wss://nip85.brainstorm.world"),
                    arrayOf("30382:personalizedGrapeRank_input", "a49e1bdd16cb0d720cd04c4c4cd9e04c21ea2292121fbbe99f03f4a6c1fd2a98", "wss://nip85.brainstorm.world"),
                    arrayOf("30382:personalizedPageRank", "a49e1bdd16cb0d720cd04c4c4cd9e04c21ea2292121fbbe99f03f4a6c1fd2a98", "wss://nip85.brainstorm.world"),
                    arrayOf("30382:verifiedFollowersCount", "a49e1bdd16cb0d720cd04c4c4cd9e04c21ea2292121fbbe99f03f4a6c1fd2a98", "wss://nip85.brainstorm.world"),
                    arrayOf("30382:verifiedMutersCount", "a49e1bdd16cb0d720cd04c4c4cd9e04c21ea2292121fbbe99f03f4a6c1fd2a98", "wss://nip85.brainstorm.world"),
                    arrayOf("30382:verifiedReportersCount", "a49e1bdd16cb0d720cd04c4c4cd9e04c21ea2292121fbbe99f03f4a6c1fd2a98", "wss://nip85.brainstorm.world"),
                    arrayOf("30382:hops", "a49e1bdd16cb0d720cd04c4c4cd9e04c21ea2292121fbbe99f03f4a6c1fd2a98", "wss://nip85.brainstorm.world"),
                ),
            content = "",
            sig = "8947d7b83b9483d241aba1b3af09c5b95b4b053d3c9e4832e1b39eb805ba33a156ea62150e99416a38ff07026f8f99f6a0bbdedb928413ba404c8b9631415c21",
        )

    @Test()
    fun parseService() {
        assertEquals(
            ServiceProviderTag(
                ServiceType(30382, "rank"),
                "a49e1bdd16cb0d720cd04c4c4cd9e04c21ea2292121fbbe99f03f4a6c1fd2a98",
                RelayUrlNormalizer.normalize("wss://nip85.brainstorm.world"),
            ),
            brainstorm.serviceProviders()[0],
        )
    }
}
