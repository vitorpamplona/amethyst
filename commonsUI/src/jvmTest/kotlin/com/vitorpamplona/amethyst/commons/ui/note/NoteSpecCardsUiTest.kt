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
package com.vitorpamplona.amethyst.commons.ui.note

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.relayClient.event.LocalEventFinder
import com.vitorpamplona.amethyst.commons.relayClient.user.LocalUserFinder
import com.vitorpamplona.amethyst.commons.relayClient.user.LocalUserFinderAccount
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.theme.AmethystPreviewTheme
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.utils.EventFactory
import com.vitorpamplona.quartz.utils.TimeUtils
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The cards of kinds whose tags are the whole event, drawn from those tags as their spec defines
 * them: each test builds an event the way its NIP (or Ditto's NIP.md, or Zapstr) lays it out and
 * checks the card shows the fields the spec gives it, and, for cards that quote, that the quote
 * stops where the row's budget does.
 */
@OptIn(ExperimentalTestApi::class)
class NoteSpecCardsUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val alice = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val bob = "7fa56f5d6962ab1e3cd424e758c3002b8665f7b0d8dcee9fe9e288d7751ac194"

    /** LocalCache holds notes weakly: the fixtures are kept here so they outlive a collection. */
    private val held = ArrayList<Note>()

    private val nav = mockk<INav>(relaxed = true)

    /** A new author, so a replaceable fixture never collides with another test's in the shared cache. */
    private fun newKey() = KeyPair().pubKey.toHexKey()

    private fun id(seed: String) = seed.repeat(64 / seed.length)

    private fun event(
        seed: String,
        kind: Int,
        tags: Array<Array<String>>,
        content: String = "",
        pubKey: String = alice,
        createdAt: Long = 1_790_900_000,
    ): Event = EventFactory.create(id(seed), pubKey, createdAt, kind, tags, content, "0".repeat(128))

    /** The note for [event] as the cache stores it, or loaded into its note when the cache declines it. */
    private fun consume(event: Event): Note {
        val note = if (event.kind in 30000..39999 || event.kind in 10000..19999 || event.kind == 3) addressable(event) else LocalCache.getOrCreateNote(event.id)
        held += note
        LocalCache.justConsume(event, null, wasVerified = true)
        if (note.event == null) note.loadEvent(event, LocalCache.getOrCreateUser(event.pubKey), emptyList())
        assertTrue(note.event != null, "could not load ${event.kind}:${event.id}")
        return note
    }

    private fun addressable(event: Event): Note {
        val d = event.tags.firstOrNull { it.size > 1 && it[0] == "d" }?.get(1) ?: ""
        return LocalCache.getOrCreateAddressableNote(Address(event.kind, event.pubKey, d))
    }

    /** [event] loaded straight into its note, as a preview or a link hands one over, without the cache's consume path. */
    private fun loadOnly(event: Event): Note {
        val note = LocalCache.getOrCreateNote(event.id)
        held += note
        note.loadEvent(event, LocalCache.getOrCreateUser(event.pubKey), emptyList())
        return note
    }

    private fun show(
        note: Note,
        quotesLeft: Int = 2,
    ) {
        compose.setContent {
            val accountViewModel = mockAccountViewModel()
            AmethystPreviewTheme(dark = false) {
                // The finders AmethystApp provides at its root, which git and community cards reach for.
                CompositionLocalProvider(
                    LocalUserFinder provides accountViewModel.dataSources().userFinder,
                    LocalUserFinderAccount provides accountViewModel.account,
                    LocalEventFinder provides accountViewModel.dataSources().eventFinder,
                ) {
                    NoteCompose(baseNote = note, quotesLeft = quotesLeft, accountViewModel = accountViewModel, nav = nav)
                }
            }
        }
        compose.waitForIdle()
    }

    private fun shown(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    /** Every text on screen, for a failure message. */
    private fun texts() =
        compose
            .onAllNodes(hasText("", substring = true))
            .fetchSemanticsNodes()
            .flatMap { node ->
                node.config
                    .getOrNull(SemanticsProperties.Text)
                    .orEmpty()
                    .map { it.text }
            }.joinToString(" | ")

    private fun described(text: String) = compose.onAllNodesWithContentDescription(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun waitShown(text: String) =
        try {
            compose.waitUntil(WAIT_MILLIS) { shown(text) }
            true
        } catch (e: ComposeTimeoutException) {
            false
        }

    // ---------------------------------------------------------------------------------------------
    // NIP-90 job feedback (7000)
    // ---------------------------------------------------------------------------------------------

    private fun jobRequest(seed: String) = consume(event(seed, 5050, arrayOf(arrayOf("i", "Write a haiku about relays", "text")), pubKey = bob))

    private fun feedback(
        seed: String,
        request: Note,
        status: Array<String>,
        content: String = "",
    ) = consume(
        event(
            seed,
            7000,
            arrayOf(status, arrayOf("amount", "21000"), arrayOf("e", request.idHex), arrayOf("p", bob)),
            content,
        ),
    )

    // `["status", <status>, <extra-info>]`, `["amount", <msats>]`, `e` the request: a pill, the
    // provider's words, the price in sats and the request quoted. The content used to be drawn raw.
    @Test
    fun dvmFeedbackShowsStatusAmountAndRequest() {
        val request = jobRequest("a1")
        show(feedback("a2", request, arrayOf("status", "payment-required", "Pay to get your haiku")))
        assertTrue(shown("Payment required"), "no status pill")
        assertTrue(shown("Pay to get your haiku"), "the status tag's extra info is missing")
        assertTrue(shown("21 sats"), "the amount (21000 msats) is missing")
        assertTrue(shown("Write a haiku about relays"), "the job request is not quoted")
    }

    // A partial result carries a sample in `content` AND the extra info in the tag: both are shown.
    @Test
    fun dvmPartialFeedbackShowsExtraInfoAndSample() {
        val request = jobRequest("a3")
        show(feedback("a4", request, arrayOf("status", "partial", "Two lines of three done"), content = "Sockets hum at dawn"))
        assertTrue(shown("Partial result"), "no status pill")
        assertTrue(shown("Two lines of three done"), "the extra info was replaced by the content")
        assertTrue(shown("Sockets hum at dawn"), "the partial result sample is missing")
    }

    @Test
    fun dvmFeedbackHonoursTheQuoteBudget() {
        val request = jobRequest("a5")
        show(feedback("a6", request, arrayOf("status", "processing")), quotesLeft = 0)
        assertTrue(shown("Processing"), "no status pill")
        assertTrue(!waitShown("Write a haiku about relays"), "quoted the request with no quotes left")
    }

    // ---------------------------------------------------------------------------------------------
    // Kind 31337 audio track (Zapstr) without a playable file
    // ---------------------------------------------------------------------------------------------

    // Zapstr's track: `subject` (title), `c` (type), `cover`, and `p` participants with a role
    // (NIP-53 order here: a relay hint, then the role). Without a file to play it is still a track:
    // its header, not the unknown-kind card.
    @Test
    fun audioTrackWithoutFileShowsItsHeader() {
        show(
            consume(
                event(
                    "a7",
                    31337,
                    arrayOf(
                        arrayOf("d", "relay-blues"),
                        arrayOf("c", "music"),
                        arrayOf("subject", "Relay Blues"),
                        arrayOf("cover", "https://example.com/relay-blues.jpg"),
                        arrayOf("p", bob, "", "Artist"),
                    ),
                ),
            ),
        )
        assertTrue(shown("Relay Blues"), "the track's title is missing")
        assertTrue(shown("Artist"), "the participant's role is missing")
        assertTrue(!shown("Unsupported"), "a titled track became an unknown-kind card")
    }

    // What Zapstr publishes: the role in slot 2, with no relay hint before it (["p", key, "Host"]).
    @Test
    fun audioTrackReadsZapstrRoleInSlotTwo() {
        show(
            consume(
                event(
                    "a8",
                    31337,
                    arrayOf(
                        arrayOf("d", "relay-podcast"),
                        arrayOf("c", "podcast"),
                        arrayOf("subject", "Relay Talk"),
                        arrayOf("p", bob, "Host"),
                    ),
                ),
            ),
        )
        assertTrue(shown("Relay Talk"), "the track's title is missing")
        assertTrue(shown("Host"), "the participant's role in slot 2 is missing")
    }

    // ---------------------------------------------------------------------------------------------
    // NIP-51 / NIP-72 community list (10004)
    // ---------------------------------------------------------------------------------------------

    // `a` tags to kind 34550; NIP-72: a definition's `name` is shown instead of its `d` when it has
    // one. Tapping a community opens it.
    @Test
    fun communityListShowsLoadedNamesAndOpensTheCommunity() {
        consume(event("a8", 34550, arrayOf(arrayOf("d", "nostr-devs"), arrayOf("name", "Nostr Developers"), arrayOf("image", "https://example.com/devs.png"))))
        show(
            consume(
                event(
                    "a9",
                    10004,
                    arrayOf(arrayOf("a", "34550:$alice:nostr-devs"), arrayOf("a", "34550:$bob:cat-pictures")),
                    pubKey = newKey(),
                ),
            ),
        )
        assertTrue(shown("Nostr Developers"), "the community's name tag is not shown")
        assertTrue(!shown("nostr-devs"), "the d-tag is shown although the community has a name")
        assertTrue(shown("cat-pictures"), "a community that has not loaded lost its d-tag")
        compose.onAllNodesWithText("Nostr Developers").onFirst().performClick()
        verify { nav.nav(Route.Community(Address(34550, alice, "nostr-devs"))) }
    }

    // ---------------------------------------------------------------------------------------------
    // NIP-72 post approval (4550)
    // ---------------------------------------------------------------------------------------------

    private val moderator = NostrSignerSync(KeyPair())

    private fun community() = consume(event("b1", 34550, arrayOf(arrayOf("d", "gardening"), arrayOf("name", "Gardeners"))))

    private fun approval(
        seed: String,
        post: Event,
        content: String,
    ) = event(
        seed,
        4550,
        arrayOf(
            arrayOf("a", "34550:$alice:gardening"),
            arrayOf("e", post.id),
            arrayOf("p", post.pubKey),
            arrayOf("k", post.kind.toString()),
        ),
        content,
        pubKey = bob,
    )

    // The approved post is SHOULD-embedded in `content`: with a valid id and signature it stands in
    // for the post until (or unless) a relay serves it.
    @Test
    fun postApprovalFallsBackToTheVerifiedEmbeddedPost() {
        community()
        val post = moderator.sign<Event>(TimeUtils.now(), 1, arrayOf(arrayOf("a", "34550:$alice:gardening")), "Tomatoes love the sun")
        show(loadOnly(approval("b2", post, post.toJson())))
        assertTrue(shown("Gardeners"), "the community is missing")
        assertTrue(waitShown("Tomatoes love the sun"), "the embedded approved post was not shown")
    }

    // An embedded post whose signature does not hold is attacker-controlled JSON: never shown.
    @Test
    fun postApprovalIgnoresAForgedEmbeddedPost() {
        community()
        val forged = event("b3", 1, emptyArray(), "Send your seed words here", pubKey = alice)
        show(loadOnly(approval("b4", forged, forged.toJson())))
        assertTrue(!waitShown("Send your seed words here"), "a forged embedded post was shown")
        assertTrue(shown("Linked event"), "no placeholder for the approved post that has not loaded")
    }

    @Test
    fun postApprovalHonoursTheQuoteBudget() {
        community()
        val post = consume(event("b5", 1, emptyArray(), "Mulch in autumn"))
        show(consume(approval("b6", post.event!!, "")), quotesLeft = 0)
        assertTrue(shown("Gardeners"), "the community is missing")
        assertTrue(!waitShown("Mulch in autumn"), "quoted the approved post with no quotes left")
    }

    // ---------------------------------------------------------------------------------------------
    // NIP-34 status (1630-1633) and the legacy reply (1622)
    // ---------------------------------------------------------------------------------------------

    private fun repository() = consume(event("b7", 30617, arrayOf(arrayOf("d", "garden-app"), arrayOf("name", "Garden App"))))

    private fun issue(seed: String) = consume(event(seed, 1621, arrayOf(arrayOf("a", "30617:$alice:garden-app"), arrayOf("subject", "Crash when watering")), "It crashes."))

    // 1631: `content` is markdown, drawn by the rich text viewer; the `merge-commit` and
    // `applied-as-commits` tags name the commits.
    @Test
    fun gitAppliedStatusShowsContentAndCommits() {
        repository()
        val target = issue("b8")
        val duplicate = consume(event("b9", 1, emptyArray(), "The older report"))
        show(
            consume(
                event(
                    "c9",
                    1631,
                    arrayOf(
                        arrayOf("e", target.idHex, "", "root"),
                        arrayOf("p", alice),
                        arrayOf("merge-commit", "0123456789abcdef0123456789abcdef01234567"),
                        arrayOf("applied-as-commits", "89abcdef0123456789abcdef0123456789abcdef", "fedcba9876543210fedcba9876543210fedcba98"),
                    ),
                    "Fixed, see nostr:${NNote.create(duplicate.idHex)}",
                ),
            ),
        )
        assertTrue(shown("0123456"), "the merge commit is missing")
        assertTrue(shown("89abcde"), "the applied commits are missing")
        assertTrue(shown("Fixed,"), "the content is missing")
        // The rich text viewer lays a paragraph out word by word (the test's plain platform draws
        // each word, link and mention as its own text); a plain Text is one node with all of it.
        assertTrue(!shown("Fixed, see"), "the content is printed as one plain text instead of by the rich text viewer: " + texts())
    }

    // A legacy 1622 reply to another reply names the issue it belongs to and its repository.
    @Suppress("DEPRECATION")
    @Test
    fun legacyGitReplyShowsItsIssueAndRepository() {
        repository()
        val root = issue("d4")
        val first = consume(event("d5", 1622, arrayOf(arrayOf("a", "30617:$alice:garden-app"), arrayOf("e", root.idHex, "", "root")), "Can you share a log?", pubKey = bob))
        show(
            consume(
                event(
                    "d6",
                    1622,
                    arrayOf(arrayOf("a", "30617:$alice:garden-app"), arrayOf("e", root.idHex, "", "root"), arrayOf("e", first.idHex, "", "reply")),
                    "Here it is",
                ),
            ),
        )
        assertTrue(shown("Crash when watering"), "the reply does not name its issue")
        assertTrue(shown("Garden App"), "the reply does not name its repository")
    }

    // A 1622 on the repository itself: the quote above it is the repository, so no second line
    // names it. (The test's note platform draws the quoted repository card empty, so any "Garden
    // App" on screen is that second line.)
    @Suppress("DEPRECATION")
    @Test
    fun legacyGitReplyToTheRepositoryDoesNotRepeatIt() {
        repository()
        show(consume(event("f3", 1622, arrayOf(arrayOf("a", "30617:$alice:garden-app")), "Nice project", pubKey = bob)))
        assertTrue(shown("Nice project"), "the reply's text is missing")
        assertTrue(!shown("Garden App"), "the repository the quote above already shows is named again: " + texts())
    }

    // ---------------------------------------------------------------------------------------------
    // NIP-51 emoji list (10030)
    // ---------------------------------------------------------------------------------------------

    // `emoji` tags (NIP-30 shortcode + url) are drawn as images; `a` tags to 30030 packs show the
    // pack's title once it loads. An `a` of any other kind is no pack.
    @Test
    fun emojiListShowsEmojisAndLoadedPacks() {
        consume(event("d7", 30030, arrayOf(arrayOf("d", "blobs"), arrayOf("title", "Blob cats"), arrayOf("emoji", "blobcat", "https://example.com/blobcat.png"))))
        show(
            consume(
                event(
                    "d8",
                    10030,
                    arrayOf(
                        arrayOf("emoji", "soapbox", "https://example.com/soapbox.png"),
                        arrayOf("emoji", "ditto", "https://example.com/ditto.png"),
                        arrayOf("a", "30030:$alice:blobs"),
                        arrayOf("a", "30023:$alice:not-a-pack"),
                    ),
                    pubKey = newKey(),
                ),
            ),
        )
        assertTrue(described(":soapbox:") || shown(":soapbox:"), "the list's own emojis are not drawn")
        assertTrue(shown("Blob cats"), "the pack's title is not shown")
        assertTrue(!shown("not-a-pack"), "an `a` tag that is not a kind-30030 pack is shown as one")
    }

    // ---------------------------------------------------------------------------------------------
    // Ditto: posting streak (13473), Top 8 (18678), themes (16767 / 36767)
    // ---------------------------------------------------------------------------------------------

    // "Once that window passes, the streak is broken and clients MUST display nothing (or zero)."
    @Test
    fun brokenPostingStreakShowsNoDays() {
        val now = TimeUtils.now()
        show(consume(event("d9", 13473, pubKey = newKey(), tags = arrayOf(arrayOf("start", (now - 10 * DAY).toString()), arrayOf("end", (now - 3 * DAY).toString())))))
        assertTrue(!shown("7-day"), "a broken streak still shows its days")
        assertTrue(shown("Ended"), "a broken streak is not marked ended")
    }

    @Test
    fun livePostingStreakShowsDays() {
        val now = TimeUtils.now()
        show(consume(event("e1", 13473, pubKey = newKey(), tags = arrayOf(arrayOf("start", (now - 5 * DAY).toString()), arrayOf("end", (now - 600).toString())))))
        assertTrue(shown("5-day posting streak"), "a live streak lost its days")
    }

    // Tag order is rank order: the card numbers them.
    @Test
    fun topEightShowsRanks() {
        show(consume(event("e2", 18678, arrayOf(arrayOf("p", bob), arrayOf("p", alice)), pubKey = newKey())))
        assertTrue(shown("#1"), "rank 1 is not numbered")
        assertTrue(shown("#2"), "rank 2 is not numbered")
    }

    // "Clients SHOULD hide kind 18678 events with zero p tags."
    @Test
    fun emptyTopEightIsHidden() {
        show(consume(event("e3", 18678, emptyArray(), pubKey = newKey())))
        assertTrue(!shown("Top 8"), "an empty Top 8 is drawn")
    }

    @Test
    fun themeDefinitionShowsColorsAndFonts() {
        show(
            consume(
                event(
                    "e4",
                    36767,
                    arrayOf(
                        arrayOf("d", "mk-dark"),
                        arrayOf("c", "#1a1a2e", "background"),
                        arrayOf("c", "#e0e0e0", "text"),
                        arrayOf("c", "#6c3ce0", "primary"),
                        arrayOf("f", "Inter", "https://example.com/inter.woff2", "body"),
                        arrayOf("f", "Playfair Display", "https://example.com/playfair.woff2", "title"),
                        arrayOf("title", "MK Dark Theme"),
                    ),
                ),
            ),
        )
        assertTrue(shown("MK Dark Theme"), "the theme's title is missing")
        assertTrue(shown("#6c3ce0"), "the theme's colors are missing")
        assertTrue(shown("Inter"), "the body font is missing")
        assertTrue(shown("Playfair Display"), "the title font is missing")
    }

    // ---------------------------------------------------------------------------------------------
    // NIP-02 follow list (3)
    // ---------------------------------------------------------------------------------------------

    // `["p", <key>, <relay>, <petname>]`: the card shows the petnames the author gave.
    @Test
    fun contactListShowsPetnames() {
        show(consume(event("e5", 3, arrayOf(arrayOf("p", alice, "", "erin"), arrayOf("p", bob)), pubKey = newKey())))
        assertTrue(shown("erin"), "the petname is missing")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    companion object {
        private const val DAY = 86_400L
        private const val WAIT_MILLIS = 3_000L

        @JvmStatic
        @BeforeClass
        fun mainDispatcher() = Dispatchers.setMain(Dispatchers.Unconfined)

        @JvmStatic
        @AfterClass
        fun resetMainDispatcher() = Dispatchers.resetMain()
    }
}
