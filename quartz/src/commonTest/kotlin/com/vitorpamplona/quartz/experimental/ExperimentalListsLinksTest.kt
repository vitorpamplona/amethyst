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
package com.vitorpamplona.quartz.experimental

import com.vitorpamplona.quartz.experimental.decentralizedLists.header.AddressableListHeaderEvent
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.AddressableListItemEvent
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.ListItemEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryPrologueEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryReadingStateEvent
import com.vitorpamplona.quartz.experimental.library.BlossomPieceIndexEvent
import com.vitorpamplona.quartz.experimental.library.BookshelfDirectoryEvent
import com.vitorpamplona.quartz.experimental.library.LearningResourceEvent
import com.vitorpamplona.quartz.experimental.music.playlist.MusicPlaylistEvent
import com.vitorpamplona.quartz.experimental.music.track.MusicTrackEvent
import com.vitorpamplona.quartz.experimental.publications.PublicationContentEvent
import com.vitorpamplona.quartz.experimental.publications.PublicationIndexEvent
import com.vitorpamplona.quartz.experimental.trustedLists.addressables.AddressableTrustedListEvent
import com.vitorpamplona.quartz.experimental.trustedLists.events.EventTrustedListEvent
import com.vitorpamplona.quartz.experimental.trustedLists.externalIds.ExternalIdTrustedListEvent
import com.vitorpamplona.quartz.experimental.trustedLists.users.UserTrustedListEvent
import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkTarget
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip01Core.links.props.ItemProps
import com.vitorpamplona.quartz.nip01Core.links.props.MemberProps
import com.vitorpamplona.quartz.nip01Core.links.props.OrderProps
import kotlin.test.Test
import kotlin.test.assertEquals

class ExperimentalListsLinksTest {
    private val me = "0".repeat(64)
    private val alice = "1".repeat(64)
    private val bob = "2".repeat(64)
    private val carol = "3".repeat(64)
    private val note1 = "e1".repeat(32)
    private val note2 = "e2".repeat(32)
    private val article = "30023:$alice:post"

    private fun tags(vararg tags: Array<String>) = arrayOf(*tags)

    @Test
    fun listItemLinksItsListsAndItsItem() {
        val header = "39998:$alice:dogs"
        val tags =
            tags(
                arrayOf("z", note1),
                arrayOf("z", header),
                arrayOf("z", "dog"),
                arrayOf("p", bob),
                arrayOf("e", note2),
                arrayOf("a", article),
                arrayOf("t", "Switzerland"),
                arrayOf("name", "Fido"),
            )
        assertEquals(
            listOf(
                Link(Relation.PARENT_LIST, LinkTarget.Event(note1), "z"),
                Link(Relation.PARENT_LIST, LinkTarget.Address(header), "z"),
                Link(Relation.PARENT_LIST, LinkTarget.Tag("z", "dog"), "z"),
                Link(Relation.ITEM, LinkTarget.User(bob), "p"),
                Link(Relation.ITEM, LinkTarget.Event(note2), "e"),
                Link(Relation.ITEM, LinkTarget.Address(article), "a"),
                // a list value, case preserved: not a hashtag
                Link(Relation.ITEM, LinkTarget.Tag("t", "Switzerland"), "t"),
            ),
            ListItemEvent(me, me, 0, tags, "", me).links(),
        )
    }

    @Test
    fun addressableListItemAddsClassThreadsQuotesAndPolarity() {
        val concept = "39998:$alice:tags"
        val tagApplied = "39999:$alice:podcaster"
        val tags =
            tags(
                arrayOf("d", "profile-tag-podcaster-11111111-00000000"),
                arrayOf("z", concept),
                arrayOf("p", bob),
                arrayOf("a", tagApplied),
                arrayOf("polarity", "-1"),
                arrayOf("b", "39999:$alice:twin", "pointer"),
                arrayOf("b", "b-tag-deferred"),
                arrayOf("n", "39999:$alice:set"),
                arrayOf("s", "39999:$alice:superset"),
                arrayOf("q", note1, "", alice),
            )
        val disputed = ItemProps(polarity = -1.0)
        assertEquals(
            listOf(
                Link(Relation.PARENT_LIST, LinkTarget.Address(concept), "z"),
                Link(Relation.ITEM, LinkTarget.User(bob), "p", disputed),
                Link(Relation.ITEM, LinkTarget.Address(tagApplied), "a", disputed),
                Link(Relation.INHERIT_FROM, LinkTarget.Address("39999:$alice:twin"), "b"),
                Link(Relation.ELEMENT_OF, LinkTarget.Address("39999:$alice:set"), "n"),
                Link(Relation.SUBSET_OF, LinkTarget.Address("39999:$alice:superset"), "s"),
                Link(Relation.QUOTE, LinkTarget.Event(note1), "q"),
            ),
            AddressableListItemEvent(me, me, 0, tags, "", me).links(),
        )
    }

    @Test
    fun addressableListHeaderLinksOnlyAWrittenConceptGraph() {
        val parent = "39998:$alice:animals"
        val conceptGraph = "39999:$me:dogs-concept-graph"
        assertEquals(
            listOf(
                Link(Relation.INHERIT_FROM, LinkTarget.Address(parent), "b"),
                Link(Relation.CONCEPT_GRAPH, LinkTarget.Address(conceptGraph), "concept-graph"),
            ),
            AddressableListHeaderEvent(
                me,
                me,
                0,
                tags(arrayOf("d", "dogs"), arrayOf("names", "dog", "dogs"), arrayOf("b", parent, "inherit"), arrayOf("concept-graph", conceptGraph)),
                "",
                me,
            ).links(),
        )
        // no tag: the computable node is not stated
        assertEquals(
            emptyList<Link<*>>(),
            AddressableListHeaderEvent(me, me, 0, tags(arrayOf("d", "dogs")), "", me).links(),
        )
    }

    @Test
    fun trustedListsLinkMembersWithScoresAndTheirDiscoveryTags() {
        val tagCoordinate = "39999:$alice:bitcoiners"
        val provenance =
            tags(
                arrayOf("observer", carol),
                arrayOf("source-tag", note2, alice, "bitcoiners"),
            )
        assertEquals(
            listOf(
                Link(Relation.MEMBER, LinkTarget.User(alice), "p", mapOf("score" to 87)),
                // 950 is not on the 0..100 scale: the member stands, unscored
                Link(Relation.MEMBER, LinkTarget.User(bob), "p"),
                Link(Relation.ABOUT, LinkTarget.Address(tagCoordinate), "a"),
                Link(Relation.OBSERVER, LinkTarget.User(carol), "observer"),
                Link(Relation.SOURCE_TAG, LinkTarget.Event(note2), "source-tag"),
            ),
            UserTrustedListEvent(
                me,
                me,
                0,
                tags(arrayOf("d", "l"), arrayOf("p", alice, "", "87"), arrayOf("p", bob, "", "950"), arrayOf("a", tagCoordinate)) + provenance,
                "",
                me,
            ).links(),
        )
        assertEquals(
            listOf(
                Link(Relation.MEMBER, LinkTarget.Event(note1), "e", mapOf("score" to 50)),
                Link(Relation.ABOUT, LinkTarget.Address(tagCoordinate), "a"),
                Link(Relation.ABOUT, LinkTarget.User(carol), "p"),
            ),
            EventTrustedListEvent(me, me, 0, tags(arrayOf("d", "l"), arrayOf("e", note1, "", "50"), arrayOf("a", tagCoordinate), arrayOf("p", carol)), "", me).links(),
        )
        assertEquals(
            listOf(
                Link(Relation.MEMBER, LinkTarget.Address(article), "a", mapOf("score" to 10)),
                Link(Relation.ABOUT, LinkTarget.User(carol), "p"),
            ),
            AddressableTrustedListEvent(me, me, 0, tags(arrayOf("d", "l"), arrayOf("a", article, "", "10"), arrayOf("p", carol)), "", me).links(),
        )
        assertEquals(
            listOf(Link(Relation.MEMBER, LinkTarget.Tag("i", "isbn:9780765382030"), "i", mapOf("score" to 5))),
            ExternalIdTrustedListEvent(me, me, 0, tags(arrayOf("d", "l"), arrayOf("i", "isbn:9780765382030", "", "5")), "", me).links(),
        )
    }

    @Test
    fun libraryKinds() {
        assertEquals(
            listOf(Link(Relation.TAG, LinkTarget.Tag("r", "https://cdn.example/file.mkv"), "r")),
            BlossomPieceIndexEvent(me, me, 0, tags(arrayOf("d", "f"), arrayOf("r", "https://cdn.example/file.mkv"), arrayOf("x", "abc"), arrayOf("b", "def", "1024")), "", me).links(),
        )
        assertEquals(
            listOf(
                Link(Relation.MEMBER, LinkTarget.Address("30040:$alice:book"), "a"),
                Link(Relation.MEMBER, LinkTarget.Event(note1), "e"),
            ),
            BookshelfDirectoryEvent(me, me, 0, tags(arrayOf("d", "shelf"), arrayOf("a", "30040:$alice:book", "A Book"), arrayOf("e", note1)), "", me).links(),
        )
        assertEquals(
            listOf(Link(Relation.HASHTAG, LinkTarget.Tag("t", "math"), "t")),
            LearningResourceEvent(me, me, 0, tags(arrayOf("d", "r"), arrayOf("t", "Math")), "", me).links(),
        )
    }

    @Test
    fun playlistCuratesItsTracksInOrder() {
        val track1 = "36787:$alice:one"
        val track2 = "36787:$bob:two"
        val tags =
            tags(
                arrayOf("d", "mix"),
                arrayOf("a", track1),
                arrayOf("a", article),
                arrayOf("a", track2),
                arrayOf("t", "playlist"),
            )
        assertEquals(
            listOf(
                Link(Relation.CURATED, LinkTarget.Address(track1), "a", OrderProps(order = 0)),
                Link(Relation.CURATED, LinkTarget.Address(track2), "a", OrderProps(order = 1)),
                Link(Relation.HASHTAG, LinkTarget.Tag("t", "playlist"), "t"),
            ),
            MusicPlaylistEvent(me, me, 0, tags, "", me).links(),
        )
        assertEquals(
            listOf(Link(Relation.HASHTAG, LinkTarget.Tag("t", "music"), "t")),
            MusicTrackEvent(me, me, 0, tags(arrayOf("d", "one"), arrayOf("t", "music"), arrayOf("artist", "Someone")), "", me).links(),
        )
    }

    @Test
    fun publicationIndexLinksItsTableOfContents() {
        val chapter = "30041:$alice:ch1"
        val original = "30040:$bob:original"
        val tags =
            tags(
                arrayOf("d", "book"),
                arrayOf("title", "Fables"),
                arrayOf("a", chapter, "Chapter 1"),
                arrayOf("e", note1, "wss://relay.example.com", "2"),
                arrayOf("p", bob),
                arrayOf("t", "Fables"),
                arrayOf("A", original),
                arrayOf("E", note2),
            )
        assertEquals(
            listOf(
                Link(Relation.MEMBER, LinkTarget.Address(chapter), "a", MemberProps(order = 0, level = 1, title = "Chapter 1")),
                Link(Relation.MEMBER, LinkTarget.Event(note1), "e", MemberProps(order = 1, level = 2)),
                Link(Relation.MENTION, LinkTarget.User(bob), "p"),
                Link(Relation.HASHTAG, LinkTarget.Tag("t", "fables"), "t"),
                Link(Relation.SOURCE, LinkTarget.Address(original), "A"),
                Link(Relation.SOURCE, LinkTarget.Event(note2), "E"),
            ),
            PublicationIndexEvent(me, me, 0, tags, "", me).links(),
        )
    }

    @Test
    fun publicationSectionLinksItsBookAndWikilinks() {
        val tags =
            tags(
                arrayOf("d", "ch1"),
                arrayOf("c", "book"),
                arrayOf("wikilink", "the-farmer", bob, "", note1),
                arrayOf("wikilink", "fox"),
            )
        assertEquals(
            listOf(
                Link(Relation.PUBLICATION, LinkTarget.Address("30040:$me:book"), "c"),
                Link(Relation.WIKILINK, LinkTarget.Event(note1), "wikilink"),
                Link(Relation.WIKILINK_AUTHOR, LinkTarget.User(bob), "wikilink"),
                Link(Relation.WIKILINK, LinkTarget.Tag("wikilink", "fox"), "wikilink"),
            ),
            PublicationContentEvent(me, me, 0, tags, "The [[fox]] and [[the Farmer]]", me).links(),
        )
    }

    @Test
    fun storiesLinkTheirOptionsAndReadingState() {
        val left = "30297:$alice:left"
        assertEquals(
            listOf(Link(Relation.OPTION, LinkTarget.Address(left), "option")),
            InteractiveStoryPrologueEvent(me, me, 0, tags(arrayOf("d", "story"), arrayOf("option", "Go left", left), arrayOf("option", "broken")), "", me).links(),
        )

        val story = "30296:$alice:story"
        val scene = "30297:$alice:scene"
        assertEquals(
            listOf(
                Link(Relation.ROOT, LinkTarget.Address(story), "A"),
                Link(Relation.CURRENT_SCENE, LinkTarget.Address(scene), "a"),
            ),
            InteractiveStoryReadingStateEvent(me, me, 0, tags(arrayOf("d", story), arrayOf("A", story), arrayOf("a", scene)), "", me).links(),
        )
        // a legacy state with only its d: the d is not a link
        assertEquals(
            emptyList<Link<*>>(),
            InteractiveStoryReadingStateEvent(me, me, 0, tags(arrayOf("d", story)), "", me).links(),
        )
    }
}
