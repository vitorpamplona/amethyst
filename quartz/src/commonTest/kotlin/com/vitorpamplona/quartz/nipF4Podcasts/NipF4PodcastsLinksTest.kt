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

import com.vitorpamplona.quartz.graph.Link
import com.vitorpamplona.quartz.graph.LinkTarget
import com.vitorpamplona.quartz.graph.Relation
import com.vitorpamplona.quartz.graph.props.RoleProps
import com.vitorpamplona.quartz.nipF4Podcasts.authored.AuthoredPodcastsEvent
import com.vitorpamplona.quartz.nipF4Podcasts.favorites.FavoritePodcastsListEvent
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.PodcastMetadataEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class NipF4PodcastsLinksTest {
    private val id = "0".repeat(64)
    private val author = "1".repeat(64)
    private val sig = "0".repeat(128)
    private val podcast = "a".repeat(64)
    private val host = "b".repeat(64)
    private val editor = "c".repeat(64)

    @Test
    fun listsNameTheirPodcasts() {
        val tags = arrayOf(arrayOf("p", podcast))
        assertEquals(listOf(Link(Relation.FAVORITE, LinkTarget.User(podcast), "p")), FavoritePodcastsListEvent(id, author, 1, tags, "", sig).links())
        assertEquals(listOf(Link(Relation.AUTHORED, LinkTarget.User(podcast), "p")), AuthoredPodcastsEvent(id, author, 1, tags, "", sig).links())
    }

    @Test
    fun podcastClaimsItsAuthorsWithTheirRoles() {
        val event = PodcastMetadataEvent(id, podcast, 1, arrayOf(arrayOf("p", host, "host"), arrayOf("p", editor, "janitor")), "", sig)
        assertEquals(
            listOf(
                Link(Relation.PODCAST_AUTHOR, LinkTarget.User(host), "p", RoleProps(listOf("host"))),
                Link(Relation.PODCAST_AUTHOR, LinkTarget.User(editor), "p"),
            ),
            event.links(),
        )
    }
}
