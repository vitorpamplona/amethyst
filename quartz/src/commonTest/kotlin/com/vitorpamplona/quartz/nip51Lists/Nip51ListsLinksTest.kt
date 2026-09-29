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
package com.vitorpamplona.quartz.nip51Lists

import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkTarget
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip01Core.links.props.LinkProps
import com.vitorpamplona.quartz.nip01Core.links.props.MuteProps
import com.vitorpamplona.quartz.nip51Lists.appCurationSet.AppCurationSetEvent
import com.vitorpamplona.quartz.nip51Lists.articleCurationSet.ArticleCurationSetEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.OldBookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkSet.BookmarkSetEvent
import com.vitorpamplona.quartz.nip51Lists.favoriteAlgoFeedsList.FavoriteAlgoFeedsListEvent
import com.vitorpamplona.quartz.nip51Lists.favoriteFollowSetsList.FavoriteFollowSetsListEvent
import com.vitorpamplona.quartz.nip51Lists.followSet.FollowSetEvent
import com.vitorpamplona.quartz.nip51Lists.geohashList.GeohashListEvent
import com.vitorpamplona.quartz.nip51Lists.gitAuthorList.GitAuthorListEvent
import com.vitorpamplona.quartz.nip51Lists.gitRepositoryList.GitRepositoryListEvent
import com.vitorpamplona.quartz.nip51Lists.goodWikiAuthorList.GoodWikiAuthorListEvent
import com.vitorpamplona.quartz.nip51Lists.interestList.InterestListEvent
import com.vitorpamplona.quartz.nip51Lists.interestSet.InterestSetEvent
import com.vitorpamplona.quartz.nip51Lists.kindMuteSet.KindMuteSetEvent
import com.vitorpamplona.quartz.nip51Lists.mediaFollowList.MediaFollowListEvent
import com.vitorpamplona.quartz.nip51Lists.mediaStarterPack.MediaStarterPackEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.pictureCurationSet.PictureCurationSetEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.FavoriteRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.releaseArtifactSet.ReleaseArtifactSetEvent
import com.vitorpamplona.quartz.nip51Lists.simpleGroupList.SimpleGroupListEvent
import com.vitorpamplona.quartz.nip51Lists.starterPack.StarterPackEvent
import com.vitorpamplona.quartz.nip51Lists.videoCurationSet.VideoCurationSetEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class Nip51ListsLinksTest {
    private val id = "0".repeat(64)
    private val me = "f".repeat(64)
    private val sig = "0".repeat(128)

    // NIP-44 ciphertext stand-in: private entries are never read for links
    private val encrypted = "AkF3c2Vy"

    private val alice = "b1".repeat(32)
    private val bob = "b2".repeat(32)
    private val note = "e1".repeat(32)
    private val otherNote = "e2".repeat(32)
    private val article = "30023:$alice:article"

    private fun <P : LinkProps> us(
        relation: Relation<P>,
        pubkey: String,
        props: P? = null,
    ) = Link(relation, LinkTarget.User(pubkey), "p", props)

    private fun <P : LinkProps> ev(
        relation: Relation<P>,
        id: String,
    ) = Link(relation, LinkTarget.Event(id), "e")

    private fun <P : LinkProps> ad(
        relation: Relation<P>,
        address: String,
    ) = Link(relation, LinkTarget.Address(address), "a")

    private fun <P : LinkProps> tg(
        relation: Relation<P>,
        name: String,
        value: String,
        via: String = name,
    ) = Link(relation, LinkTarget.Tag(name, value), via)

    private val bookmarkTags: TagArray =
        arrayOf(
            arrayOf("title", "Reading"),
            arrayOf("e", note, "wss://relay.example/", alice),
            arrayOf("a", article),
            arrayOf("e", otherNote),
        )

    private val bookmarked =
        listOf(
            ev(Relation.BOOKMARK, note),
            ad(Relation.BOOKMARK, article),
            ev(Relation.BOOKMARK, otherNote),
        )

    @Test
    fun aMuteListMutesPeopleThreadsHashtagsAndWords() {
        val event =
            MuteListEvent(
                id,
                me,
                1L,
                arrayOf(
                    arrayOf("p", alice),
                    arrayOf("t", "Politics"),
                    arrayOf("word", "GM"),
                    arrayOf("e", note),
                ),
                encrypted,
                sig,
            )
        assertEquals(
            listOf(
                us(Relation.MUTE, alice),
                tg(Relation.MUTE, "t", "politics"),
                tg(Relation.MUTE, "word", "gm"),
                ev(Relation.MUTE, note),
            ),
            event.links(),
        )
    }

    @Test
    fun aFollowSetHoldsMembersUnlessItIsTheDeprecatedMuteSet() {
        val set = FollowSetEvent(id, me, 1L, arrayOf(arrayOf("d", "friends"), arrayOf("p", alice), arrayOf("p", bob)), encrypted, sig)
        assertEquals(listOf(us(Relation.MEMBER, alice), us(Relation.MEMBER, bob)), set.links())

        val mute = FollowSetEvent(id, me, 1L, arrayOf(arrayOf("d", "mute"), arrayOf("p", alice), arrayOf("word", "spam")), encrypted, sig)
        assertEquals(listOf(us(Relation.MUTE, alice), tg(Relation.MUTE, "word", "spam")), mute.links())
    }

    @Test
    fun aKindMuteSetMutesForTheKindItsDNames() {
        val event = KindMuteSetEvent(id, me, 1L, arrayOf(arrayOf("d", "1"), arrayOf("p", alice)), encrypted, sig)
        assertEquals(listOf(us(Relation.MUTE, alice, MuteProps(mutedKind = 1))), event.links())
    }

    @Test
    fun pinsAndBookmarks() {
        assertEquals(
            listOf(ev(Relation.PIN, note)),
            PinListEvent(id, me, 1L, arrayOf(arrayOf("e", note)), "", sig).links(),
        )
        assertEquals(bookmarked, BookmarkListEvent(id, me, 1L, bookmarkTags, encrypted, sig).links())
        assertEquals(bookmarked, BookmarkSetEvent(id, me, 1L, bookmarkTags + arrayOf(arrayOf("d", "reading")), encrypted, sig).links())
    }

    @Test
    fun theDeprecatedListMeansWhatItsReplacementMeans() {
        val pins = OldBookmarkListEvent(id, me, 1L, arrayOf(arrayOf("d", "pin"), arrayOf("e", note)), "", sig)
        assertEquals(listOf(ev(Relation.PIN, note)), pins.links())

        val community = "34550:$alice:nostr"
        val communities = OldBookmarkListEvent(id, me, 1L, arrayOf(arrayOf("d", "communities"), arrayOf("a", community)), "", sig)
        assertEquals(listOf(ad(Relation.SUBSCRIBED, community)), communities.links())

        val bookmarks = OldBookmarkListEvent(id, me, 1L, bookmarkTags + arrayOf(arrayOf("d", "bookmark")), "", sig)
        assertEquals(bookmarked, bookmarks.links())
    }

    @Test
    fun curationSetsCurate() {
        val tags = arrayOf(arrayOf("d", "best"), arrayOf("a", article), arrayOf("e", note))
        val curated = listOf(ad(Relation.CURATED, article), ev(Relation.CURATED, note))
        assertEquals(curated, ArticleCurationSetEvent(id, me, 1L, tags, "", sig).links())
        assertEquals(curated, VideoCurationSetEvent(id, me, 1L, tags, "", sig).links())
        assertEquals(curated, PictureCurationSetEvent(id, me, 1L, tags, "", sig).links())

        val app = "32267:$alice:com.example.app"
        assertEquals(
            listOf(ad(Relation.CURATED, app)),
            AppCurationSetEvent(id, me, 1L, arrayOf(arrayOf("d", "apps"), arrayOf("a", app)), "", sig).links(),
        )
    }

    @Test
    fun aReleaseCuratesItsArtifactsForItsApp() {
        val app = "32267:$alice:com.example.app"
        val event =
            ReleaseArtifactSetEvent(
                id,
                alice,
                1L,
                arrayOf(
                    arrayOf("d", "com.example.app@1.0.0"),
                    arrayOf("i", "com.example.app"),
                    arrayOf("version", "1.0.0"),
                    arrayOf("e", note),
                    arrayOf("a", app),
                ),
                "release notes",
                sig,
            )
        assertEquals(
            listOf(
                tg(Relation.TAG, "i", "com.example.app"),
                ev(Relation.CURATED, note),
                ad(Relation.APP, app),
            ),
            event.links(),
        )
    }

    @Test
    fun favoritesKeepOnlyTheKindsTheirListHolds() {
        val followSet = "30000:$alice:friends"
        val favoriteSets =
            FavoriteFollowSetsListEvent(id, me, 1L, arrayOf(arrayOf("a", followSet), arrayOf("a", article)), encrypted, sig)
        assertEquals(listOf(ad(Relation.FAVORITE, followSet)), favoriteSets.links())

        val relaySet = "30002:$alice:fast"
        val favoriteRelays =
            FavoriteRelayListEvent(id, me, 1L, arrayOf(arrayOf("relay", "wss://relay.example/"), arrayOf("a", relaySet), arrayOf("a", article)), encrypted, sig)
        assertEquals(listOf(ad(Relation.FAVORITE, relaySet)), favoriteRelays.links())

        val feed = "31990:$alice:feed"
        assertEquals(
            listOf(ad(Relation.FAVORITE, feed)),
            FavoriteAlgoFeedsListEvent(id, me, 1L, arrayOf(arrayOf("a", feed)), encrypted, sig).links(),
        )
    }

    @Test
    fun followLikeListsAreSubscriptionsNotFollows() {
        assertEquals(
            listOf(us(Relation.SUBSCRIBED, alice)),
            MediaFollowListEvent(id, me, 1L, arrayOf(arrayOf("p", alice)), encrypted, sig).links(),
        )
        assertEquals(
            listOf(us(Relation.SUBSCRIBED, alice)),
            GitAuthorListEvent(id, me, 1L, arrayOf(arrayOf("p", alice, "wss://relay.example/", "al")), encrypted, sig).links(),
        )
        val repo = "30617:$alice:amethyst"
        assertEquals(
            listOf(ad(Relation.SUBSCRIBED, repo)),
            GitRepositoryListEvent(id, me, 1L, arrayOf(arrayOf("a", repo)), encrypted, sig).links(),
        )
        assertEquals(
            listOf(tg(Relation.SUBSCRIBED, "g", "u4pruy")),
            GeohashListEvent(id, me, 1L, arrayOf(arrayOf("g", "u4pruy")), encrypted, sig).links(),
        )
    }

    @Test
    fun interestsFollowHashtagsAndInterestSets() {
        val interestSet = "30015:$alice:bitcoin"
        val list =
            InterestListEvent(
                id,
                me,
                1L,
                arrayOf(
                    arrayOf("t", "Nostr"),
                    arrayOf("a", interestSet),
                    arrayOf("a", article),
                    arrayOf("t", "zaps"),
                ),
                encrypted,
                sig,
            )
        assertEquals(
            listOf(
                tg(Relation.SUBSCRIBED, "t", "nostr"),
                tg(Relation.SUBSCRIBED, "t", "zaps"),
                ad(Relation.SUBSCRIBED, interestSet),
            ),
            list.links(),
        )

        val set = InterestSetEvent(id, me, 1L, arrayOf(arrayOf("d", "bitcoin"), arrayOf("t", "Bitcoin"), arrayOf("t", "lightning")), encrypted, sig)
        assertEquals(listOf(tg(Relation.MEMBER, "t", "bitcoin"), tg(Relation.MEMBER, "t", "lightning")), set.links())
    }

    @Test
    fun wikiAuthorsAreRecommended() {
        assertEquals(
            listOf(us(Relation.RECOMMENDED, alice)),
            GoodWikiAuthorListEvent(id, me, 1L, arrayOf(arrayOf("p", alice)), encrypted, sig).links(),
        )
    }

    @Test
    fun starterPacksHoldMembers() {
        val pack =
            StarterPackEvent(
                id,
                me,
                1L,
                arrayOf(arrayOf("d", "devs"), arrayOf("p", alice), arrayOf("t", "Dev"), arrayOf("p", bob)),
                "",
                sig,
            )
        assertEquals(
            listOf(us(Relation.MEMBER, alice), us(Relation.MEMBER, bob), tg(Relation.HASHTAG, "t", "dev")),
            pack.links(),
        )

        val media = MediaStarterPackEvent(id, me, 1L, arrayOf(arrayOf("d", "photographers"), arrayOf("p", alice)), "", sig)
        assertEquals(listOf(us(Relation.MEMBER, alice)), media.links())
    }

    @Test
    fun aGroupIsItsIdWhateverRelayHostsIt() {
        val event =
            SimpleGroupListEvent(
                id,
                me,
                1L,
                arrayOf(
                    arrayOf("group", "pizza-lovers", "wss://groups.example/", "Pizza Lovers"),
                    // no host relay: not a group entry
                    arrayOf("group", "orphan"),
                    arrayOf("r", "wss://groups.example/"),
                ),
                encrypted,
                sig,
            )
        assertEquals(listOf(tg(Relation.SUBSCRIBED, "h", "pizza-lovers", "group")), event.links())
    }
}
