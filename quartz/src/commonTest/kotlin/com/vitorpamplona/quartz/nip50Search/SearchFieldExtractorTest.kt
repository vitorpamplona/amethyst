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
package com.vitorpamplona.quartz.nip50Search

import com.vitorpamplona.quartz.buzz.agentProfiles.AgentProfileEvent
import com.vitorpamplona.quartz.experimental.birdstar.BirdDetectionEvent
import com.vitorpamplona.quartz.experimental.birdstar.BirdexEvent
import com.vitorpamplona.quartz.experimental.nip95.header.FileStorageHeaderEvent
import com.vitorpamplona.quartz.experimental.ps1saves.Ps1SaveEvent
import com.vitorpamplona.quartz.experimental.trustedLists.users.UserTrustedListEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip15Marketplace.product.ProductEvent
import com.vitorpamplona.quartz.nip15Marketplace.stall.StallEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.EditMetadataEvent
import com.vitorpamplona.quartz.nip32Labeling.LabelEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.nip69P2pOrderEvents.P2POrderEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.ContactCardEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nipB0WebBookmarks.WebBookmarkEvent
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.Podcasting20EpisodeEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchFieldExtractorTest {
    private val alice = "a1".repeat(32)

    @Test
    fun kind0DecomposesIntoTheProfileRoles() {
        val content = """{"name":"vitor","display_name":"Vitor P","about":"builds nostr","nip05":"vitor@vitorpamplona.com","lud16":"me@wallet.com","website":"https://vitorpamplona.com","picture":"https://x/y.jpg"}"""
        val fields = SearchFieldExtractor.extract(MetadataEvent("1".repeat(64), alice, 1L, emptyArray(), content, ""))
        assertEquals(
            IndexableFields.Profile(
                name = "vitor",
                displayName = "Vitor P",
                about = "builds nostr",
                nip05 = "vitor@vitorpamplona.com",
                lud16 = "me@wallet.com",
                website = "https://vitorpamplona.com",
            ),
            fields,
        )
    }

    @Test
    fun longFormDecomposesIntoTitleSummaryHashtagsContent() {
        val tags = arrayOf(arrayOf("d", "post"), arrayOf("title", "My Post"), arrayOf("summary", "tl;dr"), arrayOf("t", "nostr"), arrayOf("t", "search"))
        val fields = SearchFieldExtractor.extract(LongTextNoteEvent("2".repeat(64), alice, 1L, tags, "the whole article", ""))
        assertEquals(IndexableFields.Tiered(primary = listOf("My Post"), secondary = listOf("tl;dr"), text = "the whole article", hashtags = listOf("nostr", "search")), fields)
    }

    @Test
    fun notesUseTheSubjectAndHashtags() {
        val tags = arrayOf(arrayOf("subject", "meetup"), arrayOf("t", "brazil"))
        val fields = SearchFieldExtractor.extract(TextNoteEvent("3".repeat(64), alice, 1L, tags, "see you there", ""))
        assertEquals(IndexableFields.Tiered(primary = listOf("meetup"), text = "see you there", hashtags = listOf("brazil")), fields)
    }

    @Test
    fun locationTagsAreCarriedRawLikeHashtags() {
        val tags = arrayOf(arrayOf("location", "Rio de Janeiro"))
        val fields = SearchFieldExtractor.extract(TextNoteEvent("4".repeat(64), alice, 1L, tags, "gm", ""))
        assertEquals(IndexableFields.Tiered(text = "gm", locations = listOf("Rio de Janeiro")), fields)
    }

    @Test
    fun torrentsIndexFileNamesAndTrackers() {
        val tags =
            arrayOf(
                arrayOf("title", "Great Torrent"),
                arrayOf("file", "episode1.mkv"),
                arrayOf("file", "episode2.mkv"),
                arrayOf("tracker", "https://tracker.example.com"),
            )
        val fields = SearchFieldExtractor.extract(TorrentEvent("5".repeat(64), alice, 1L, tags, "a series", ""))
        assertEquals(
            IndexableFields.Tiered(
                primary = listOf("Great Torrent"),
                // Unjoined: the backend decides how file names are indexed.
                secondary = listOf("episode1.mkv", "episode2.mkv"),
                text = "a series",
                websites = listOf("https://tracker.example.com"),
            ),
            fields,
        )
    }

    @Test
    fun webBookmarksAreFindableByTheirUrl() {
        // NIP-B0: the d tag carries the URL scheme-less; url() re-adds https://.
        val tags = arrayOf(arrayOf("d", "vitorpamplona.com/post"), arrayOf("title", "A Post"))
        val fields = SearchFieldExtractor.extract(WebBookmarkEvent("6".repeat(64), alice, 1L, tags, "", ""))
        assertEquals(IndexableFields.Tiered(primary = listOf("A Post"), websites = listOf("https://vitorpamplona.com/post")), fields)
    }

    @Test
    fun appHandlerMetadataReusesTheProfileRoles() {
        val content = """{"name":"CoolApp","about":"an app","website":"https://coolapp.example"}"""
        val fields = SearchFieldExtractor.extract(AppDefinitionEvent("7".repeat(64), alice, 1L, arrayOf(arrayOf("d", "x")), content, ""))
        assertEquals(IndexableFields.Profile(name = "CoolApp", about = "an app", website = "https://coolapp.example"), fields)
    }

    @Test
    fun trustedListsDecomposeIntoTheirTitle() {
        // The title is the whole of indexableContent() for the family, and it
        // is a title: primary, not the body tier. Everything else the list
        // carries -- content echo, member tags, metric, d -- stays out.
        val tags =
            arrayOf(
                arrayOf("d", "tl-pin-verified-human"),
                arrayOf("title", "Verified Human"),
                arrayOf("metric", "pinned-tag-membership"),
                arrayOf("p", alice, "", "87"),
            )
        val fields = SearchFieldExtractor.extract(UserTrustedListEvent("d".repeat(64), alice, 1L, tags, """{"members":[]}""", ""))
        assertEquals(IndexableFields.Tiered(primary = listOf("Verified Human")), fields)
    }

    @Test
    fun titlelessTrustedListsExtractNothing() {
        // Most machine-published lists have no title. The branch reads title()
        // directly rather than indexableContent(), so the None comes from the
        // tiers funnel finding nothing to clean -- not from title() ?: "".
        val tags = arrayOf(arrayOf("d", "tl-pin-untitled"), arrayOf("p", alice, "", "50"))
        assertEquals(IndexableFields.None, SearchFieldExtractor.extract(UserTrustedListEvent("e".repeat(64), alice, 1L, tags, "", "")))
    }

    @Test
    fun contactCardsDecomposeIntoPetnameSummaryAndTopics() {
        // A provider's petname for a person is that provider's NAME for them,
        // so it lands where kind 0's name does. topics() reads `t` tags, so
        // the tiers() funnel carries them once, as hashtags.
        val tags =
            arrayOf(
                arrayOf("d", alice),
                arrayOf("petname", "Verified Human"),
                arrayOf("summary", "vouched by two independent raters"),
                arrayOf("t", "bitcoin"),
                arrayOf("rank", "87"),
            )
        val fields = SearchFieldExtractor.extract(ContactCardEvent("f".repeat(64), alice, 1L, tags, "", ""))
        assertEquals(
            IndexableFields.Tiered(
                primary = listOf("Verified Human"),
                secondary = listOf("vouched by two independent raters"),
                hashtags = listOf("bitcoin"),
            ),
            fields,
        )
    }

    @Test
    fun contactCardsCarryTopicsEvenWithNoPublicPetname() {
        // THE SHAPE THIS LIBRARY ITSELF PUBLISHES: build() puts petname and
        // summary in the NIP-44 content, so a card's only public text is its
        // topics. They must still reach the backend -- through the hashtag
        // role, once -- and a hashtags-only extraction must not normalize to
        // None (Tiered.isEmpty() compares against a fully-empty Tiered).
        val tags = arrayOf(arrayOf("d", alice), arrayOf("t", "bitcoin"), arrayOf("t", "nostr"), arrayOf("rank", "87"))
        val fields = SearchFieldExtractor.extract(ContactCardEvent("2a".repeat(32), alice, 1L, tags, "encrypted", ""))
        assertEquals(IndexableFields.Tiered(hashtags = listOf("bitcoin", "nostr")), fields)
    }

    @Test
    fun contactCardsWithNoPublicTextExtractNothing() {
        // The petname and summary of a private card live in the NIP-44
        // encrypted content, which is never indexed -- so a card carrying only
        // scores has nothing to search.
        val tags = arrayOf(arrayOf("d", alice), arrayOf("rank", "87"), arrayOf("followers", "1200"))
        assertEquals(IndexableFields.None, SearchFieldExtractor.extract(ContactCardEvent("1a".repeat(32), alice, 1L, tags, "encrypted", "")))
    }

    @Test
    fun nonSearchableKindsExtractNothing() {
        // Kind 7 reactions are not SearchableEvent.
        val reaction = Event("8".repeat(64), alice, 1L, 7, emptyArray(), "+", "")
        assertEquals(IndexableFields.None, SearchFieldExtractor.extract(reaction))
    }

    @Test
    fun unmappedSearchableKindsFallBackToTheTextTier() {
        val fields = SearchFieldExtractor.extract(ChatMessageEvent("a".repeat(64), alice, 1L, emptyArray(), "hello group", ""))
        assertEquals(IndexableFields.Tiered(text = "hello group"), fields)
    }

    @Test
    fun blankContentKindsNormalizeToNone() {
        val fields = SearchFieldExtractor.extract(TextNoteEvent("b".repeat(64), alice, 1L, emptyArray(), "   ", ""))
        assertEquals(IndexableFields.None, fields)
    }

    @Test
    fun unparseableBuzzContentStillIndexesItsHashtags() {
        // The branch finds no text, but the tiers() funnel still carries the
        // event's raw tags — the one subtle reachability seam of the port.
        val tags = arrayOf(arrayOf("t", "agents"))
        val fields = SearchFieldExtractor.extract(AgentProfileEvent("c".repeat(64), alice, 1L, tags, "not json", ""))
        assertEquals(IndexableFields.Tiered(hashtags = listOf("agents")), fields)
    }

    @Test
    fun profileShapesNeverGetHashtagFolding() {
        // Hashtags/locations are filled only by the tiers() funnel, which
        // profile branches never use: a kind-0 with t-tags stays a pure
        // profile (and an empty one normalizes to None).
        val tags = arrayOf(arrayOf("t", "nostr"))
        val fields = SearchFieldExtractor.extract(MetadataEvent("9".repeat(64), alice, 1L, tags, "{}", ""))
        assertEquals(IndexableFields.None, fields)
    }

    @Test
    fun marketplaceNamesReachTheTitleTierNotTheBody() {
        // NIP-15 keeps the stall's name inside a JSON content blob. Falling
        // through to the catch-all put that NAME in the body tier, where a
        // weighted backend can never rank it as a title.
        val content = """{"id":"s1","name":"Vitor's Coffee","description":"beans from Minas","currency":"BRL"}"""
        val fields = SearchFieldExtractor.extract(StallEvent("20".repeat(32), alice, 1L, arrayOf(arrayOf("d", "s1")), content, ""))
        assertEquals(IndexableFields.Tiered(primary = listOf("Vitor's Coffee"), secondary = listOf("beans from Minas")), fields)
    }

    @Test
    fun productCategoriesAreCarriedOnceAsHashtags() {
        // categories() is `t` under another name: indexableContent()
        // concatenates it into the flat blob, but the funnel already carries
        // it in the hashtag role, so the branch must not pass it again.
        val content = """{"id":"p1","stall_id":"s1","name":"Bag of Beans","description":"1kg","currency":"BRL","price":90.0}"""
        val tags = arrayOf(arrayOf("d", "p1"), arrayOf("t", "coffee"))
        val fields = SearchFieldExtractor.extract(ProductEvent("21".repeat(32), alice, 1L, tags, content, ""))
        assertEquals(
            IndexableFields.Tiered(primary = listOf("Bag of Beans"), secondary = listOf("1kg"), hashtags = listOf("coffee")),
            fields,
        )
    }

    @Test
    fun unparseableMarketplaceContentStillIndexesItsHashtags() {
        val fields = SearchFieldExtractor.extract(StallEvent("22".repeat(32), alice, 1L, arrayOf(arrayOf("t", "coffee")), "not json", ""))
        assertEquals(IndexableFields.Tiered(hashtags = listOf("coffee")), fields)
    }

    @Test
    fun groupMetadataEditsSplitLikeTheMetadataTheyEdit() {
        // kind 9002 edits what kind 39000 publishes; it was the only half of
        // the pair without a branch. Its hashtags() is `t`: carried once.
        val tags = arrayOf(arrayOf("h", "grp"), arrayOf("name", "Nostr Devs"), arrayOf("about", "we build"), arrayOf("t", "nostr"))
        val fields = SearchFieldExtractor.extract(EditMetadataEvent("23".repeat(32), alice, 1L, tags, "", ""))
        assertEquals(
            IndexableFields.Tiered(primary = listOf("Nostr Devs"), secondary = listOf("we build"), hashtags = listOf("nostr")),
            fields,
        )
    }

    @Test
    fun podcasting20EpisodesSplitLikeEveryOtherTitledKind() {
        val tags = arrayOf(arrayOf("d", "ep1"), arrayOf("title", "Episode 42"), arrayOf("description", "on search"), arrayOf("t", "podcast"))
        val fields = SearchFieldExtractor.extract(Podcasting20EpisodeEvent("24".repeat(32), alice, 1L, tags, "show notes", ""))
        assertEquals(
            IndexableFields.Tiered(primary = listOf("Episode 42"), secondary = listOf("on search"), text = "show notes", hashtags = listOf("podcast")),
            fields,
        )
    }

    @Test
    fun summaryOnlyKindsUseTheSummaryTier() {
        // kind 1065's whole searchable text IS a summary -- it belongs beside
        // kind 1063's, not in the body tier.
        val tags = arrayOf(arrayOf("summary", "the quarterly report"))
        val fields = SearchFieldExtractor.extract(FileStorageHeaderEvent("25".repeat(32), alice, 1L, tags, "", ""))
        assertEquals(IndexableFields.Tiered(secondary = listOf("the quarterly report")), fields)
    }

    @Test
    fun birdSightingsAreNamedByTheirSpeciesUnderBothNames() {
        // The scientific name comes from the `n` tag, the vernacular one from
        // commonName()'s parse of the `alt` -- both are names, so both are
        // titles. The alt still reaches the summary tier whole.
        val tags =
            arrayOf(
                arrayOf("n", "Porphyrio martinica"),
                arrayOf("alt", "Bird detection: Purple Gallinule (Porphyrio martinica)"),
            )
        val fields = SearchFieldExtractor.extract(BirdDetectionEvent("26".repeat(32), alice, 1L, tags, "", ""))
        assertEquals(
            IndexableFields.Tiered(
                primary = listOf("Porphyrio martinica", "Purple Gallinule"),
                secondary = listOf("Bird detection: Purple Gallinule (Porphyrio martinica)"),
            ),
            fields,
        )
    }

    @Test
    fun aBirdSightingKeepsAltTextBeyondTheParsedNames() {
        // commonName() only matches a PREFIX, so a publisher can write
        // anything after the parenthetical. Dropping the alt whenever that
        // prefix parsed lost the tail ("at Lake Merritt, 7am") from every
        // role, while indexableContent() still carried it -- the exact drift
        // this extractor exists to prevent.
        val tags =
            arrayOf(
                arrayOf("n", "Porphyrio martinica"),
                arrayOf("alt", "Bird detection: Purple Gallinule (Porphyrio martinica) at Lake Merritt, 7am"),
            )
        val fields = SearchFieldExtractor.extract(BirdDetectionEvent("3a".repeat(32), alice, 1L, tags, "", ""))
        assertEquals(
            IndexableFields.Tiered(
                primary = listOf("Porphyrio martinica", "Purple Gallinule"),
                secondary = listOf("Bird detection: Purple Gallinule (Porphyrio martinica) at Lake Merritt, 7am"),
            ),
            fields,
        )
    }

    @Test
    fun aBirdSightingWithAnUnrecognizedAltStillIndexesIt() {
        // An alt that does not start with the known prefix parses to no
        // common name, and is carried as the summary it is.
        val tags = arrayOf(arrayOf("n", "Ramphastos toco"), arrayOf("alt", "a toucan at the feeder"))
        val fields = SearchFieldExtractor.extract(BirdDetectionEvent("3c".repeat(32), alice, 1L, tags, "", ""))
        assertEquals(
            IndexableFields.Tiered(primary = listOf("Ramphastos toco"), secondary = listOf("a toucan at the feeder")),
            fields,
        )
    }

    @Test
    fun aLifeListKeepsItsSpeciesOutOfTheTitleTier() {
        // kind 12473 is an unbounded collection, not a sighting: one title
        // band per bird would dilute the tier the way a torrent's file list
        // would.
        val tags = arrayOf(arrayOf("n", "Ramphastos toco"), arrayOf("n", "Porphyrio martinica"), arrayOf("alt", "my life list"))
        val fields = SearchFieldExtractor.extract(BirdexEvent("3b".repeat(32), alice, 1L, tags, "", ""))
        assertEquals(
            IndexableFields.Tiered(secondary = listOf("my life list", "Ramphastos toco", "Porphyrio martinica")),
            fields,
        )
    }

    @Test
    fun saveTitlesAreTitlesAndTheRestAreKeywords() {
        val tags =
            arrayOf(
                arrayOf("d", "save1"),
                arrayOf("title", "Final Fantasy VII"),
                arrayOf("filename", "BASCUS-94163"),
                arrayOf("region", "NTSC-U"),
                arrayOf("alt", "a memory card save"),
            )
        val fields = SearchFieldExtractor.extract(Ps1SaveEvent("27".repeat(32), alice, 1L, tags, "", ""))
        assertEquals(
            IndexableFields.Tiered(
                primary = listOf("Final Fantasy VII"),
                secondary = listOf("a memory card save", "NTSC-U", "BASCUS-94163"),
            ),
            fields,
        )
    }

    @Test
    fun p2pOrdersAreNamedByTheirMaker() {
        val tags = arrayOf(arrayOf("d", "o1"), arrayOf("name", "Satoshi"), arrayOf("f", "BRL"), arrayOf("pm", "pix", "wire"))
        val fields = SearchFieldExtractor.extract(P2POrderEvent("28".repeat(32), alice, 1L, tags, "", ""))
        assertEquals(
            IndexableFields.Tiered(primary = listOf("Satoshi"), secondary = listOf("BRL", "pix", "wire")),
            fields,
        )
    }

    @Test
    fun pollOptionsAreCarriedUnjoinedInTheSecondaryTier() {
        // indexableContent() has to append the labels to the body; the roles
        // keep them separate, so the backend chooses how to weight them.
        val tags = arrayOf(arrayOf("option", "1", "Coffee"), arrayOf("option", "2", "Tea"))
        val fields = SearchFieldExtractor.extract(PollEvent("29".repeat(32), alice, 1L, tags, "what should I drink?", ""))
        assertEquals(
            IndexableFields.Tiered(secondary = listOf("Coffee", "Tea"), text = "what should I drink?"),
            fields,
        )
    }

    @Test
    fun labelValuesAreKeywordsNotBody() {
        val tags = arrayOf(arrayOf("l", "spam", "report"), arrayOf("L", "report"))
        val fields = SearchFieldExtractor.extract(LabelEvent("2b".repeat(32), alice, 1L, tags, "obvious bot", ""))
        assertEquals(IndexableFields.Tiered(secondary = listOf("spam"), text = "obvious bot"), fields)
    }

    @Test
    fun commentHashtagsAreNotIndexedTwice() {
        // indexableContent() concatenates the `t` tags INTO the body for kind
        // 1111; the funnel already carries them in the hashtag role, so the
        // branch passes the body alone.
        val tags = arrayOf(arrayOf("t", "nostr"))
        val fields = SearchFieldExtractor.extract(CommentEvent("2c".repeat(32), alice, 1L, tags, "good point", ""))
        assertEquals(IndexableFields.Tiered(text = "good point", hashtags = listOf("nostr")), fields)
    }

    @Test
    fun repositoriesAreFindableByCloneUrlAndHomepage() {
        val tags =
            arrayOf(
                arrayOf("d", "amethyst"),
                arrayOf("name", "Amethyst"),
                arrayOf("description", "a nostr client"),
                arrayOf("web", "https://amethyst.social"),
                arrayOf("clone", "https://github.com/vitorpamplona/amethyst.git"),
                // A repo whose homepage IS its clone URL must not index it twice.
                arrayOf("clone", "https://amethyst.social"),
            )
        val fields = SearchFieldExtractor.extract(GitRepositoryEvent("2d".repeat(32), alice, 1L, tags, "", ""))
        assertEquals(
            IndexableFields.Tiered(
                primary = listOf("Amethyst"),
                secondary = listOf("a nostr client"),
                websites = listOf("https://amethyst.social", "https://github.com/vitorpamplona/amethyst.git"),
            ),
            fields,
        )
    }

    @Test
    fun meetingSpacesCarryTheirStreamingUrlLikeLiveActivitiesDo() {
        val tags = arrayOf(arrayOf("d", "room1"), arrayOf("title", "Design Sync"), arrayOf("streaming", "https://nests.example/room1"))
        val fields = SearchFieldExtractor.extract(MeetingSpaceEvent("2e".repeat(32), alice, 1L, tags, "", ""))
        assertEquals(
            IndexableFields.Tiered(primary = listOf("Design Sync"), websites = listOf("https://nests.example/room1")),
            fields,
        )
    }

    @Test
    fun staticSitesCarryTheirSourceUrl() {
        val tags = arrayOf(arrayOf("d", "blog"), arrayOf("title", "My Blog"), arrayOf("source", "https://github.com/me/blog"))
        val fields = SearchFieldExtractor.extract(NamedSiteEvent("2f".repeat(32), alice, 1L, tags, "", ""))
        assertEquals(
            IndexableFields.Tiered(primary = listOf("My Blog"), websites = listOf("https://github.com/me/blog")),
            fields,
        )
    }
}
