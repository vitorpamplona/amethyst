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
package com.vitorpamplona.quartz.nip89AppHandlers

import com.vitorpamplona.quartz.graph.Link
import com.vitorpamplona.quartz.graph.LinkTarget
import com.vitorpamplona.quartz.graph.Relation
import com.vitorpamplona.quartz.graph.allLinks
import com.vitorpamplona.quartz.graph.props.PlatformProps
import com.vitorpamplona.quartz.graph.props.ReleaseProps
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip89AppHandlers.recommendation.AppRecommendationEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class Nip89AppHandlersLinksTest {
    private val id = "0".repeat(64)
    private val sig = "0".repeat(128)
    private val dev = "1".repeat(64)
    private val user = "2".repeat(64)

    @Test
    fun recommendationLinksHandlersWithTheirPlatform() {
        val web = "31990:$dev:web-client"
        val android = "31990:$dev:android-client"
        val event =
            AppRecommendationEvent(
                id,
                user,
                1,
                arrayOf(
                    arrayOf("d", "1"),
                    arrayOf("a", web, "wss://relay.example/", "web"),
                    arrayOf("a", android, "", ""),
                ),
                "",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.RECOMMENDED, LinkTarget.Address(web), "a", PlatformProps(platform = "web")),
                Link(Relation.RECOMMENDED, LinkTarget.Address(android), "a"),
            ),
            event.links(),
        )
    }

    @Test
    fun definitionLinksSiteManifestsKindsAndCategories() {
        val latest = "35128:$dev:app-v2"
        val next = "35128:$dev:app-v3"
        val repo = "30617:$dev:app"
        val handler = "31990:$dev:publisher"
        val event =
            AppDefinitionEvent(
                id,
                dev,
                1,
                arrayOf(
                    arrayOf("d", "app"),
                    arrayOf("k", "1"),
                    arrayOf("latest", latest, "wss://relay.example/"),
                    arrayOf("next", next),
                    arrayOf("a", repo),
                    arrayOf("t", "Social"),
                    arrayOf("web", "https://app.example/e/<bech32>", "nevent"),
                    arrayOf("client", "Publisher", handler),
                ),
                "",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.TAG, LinkTarget.Tag("k", "1"), "k"),
                Link(Relation.SITE_MANIFEST, LinkTarget.Address(latest), "latest", ReleaseProps(release = "latest")),
                Link(Relation.SITE_MANIFEST, LinkTarget.Address(next), "next", ReleaseProps(release = "next")),
                Link(Relation.SITE_MANIFEST, LinkTarget.Address(repo), "a"),
                Link(Relation.HASHTAG, LinkTarget.Tag("t", "social"), "t"),
            ),
            event.links(),
        )
        // The client tag is every kind's, stated once by allLinks().
        assertEquals(
            listOf(
                Link(Relation.AUTHOR, LinkTarget.User(dev)),
                Link(Relation.ADDRESS, LinkTarget.Address("31990:$dev:app")),
                Link(Relation.CLIENT, LinkTarget.Address(handler), "client"),
            ) + event.links(),
            event.allLinks(),
        )
    }
}
