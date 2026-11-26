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

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.richtext.RichTextViewerState
import com.vitorpamplona.amethyst.commons.ui.components.DefaultInlineQuoteRenderer
import com.vitorpamplona.amethyst.commons.ui.components.InlineQuoteRenderer
import com.vitorpamplona.amethyst.commons.ui.components.LocalInlineQuoteRenderer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.richtext.DefaultRichTextSegmentRenderer
import com.vitorpamplona.amethyst.commons.ui.richtext.LocalRichTextPlatform
import com.vitorpamplona.amethyst.commons.ui.richtext.RichTextPlatform
import com.vitorpamplona.amethyst.commons.ui.richtext.RichTextSegmentRenderer
import com.vitorpamplona.amethyst.commons.ui.theme.AmethystPreviewTheme
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.utils.EventFactory
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * A card's own text must spend the note row's quote budget, not a fresh one.
 *
 * Each fixture is a pair of events whose texts (a NIP-71 summary, a NIP-56 comment, an app
 * description, release notes, a poll option...) quote each other. Drawn in a row with
 * `quotesLeft = 1`, the quote must draw once and stop there: the quoted card has nothing left to
 * spend. A renderer that hands its text a hard-coded budget instead quotes back at every level,
 * without end (the counting renderer below cuts it off at [DEPTH_CAP]).
 *
 * A short row (`makeItShort`, as in a notification or a boosted note) loads no quotes or previews
 * at all, so the same fixtures drawn short must quote nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NoteQuoteBudgetTest {
    private val alice = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val bob = "7fa56f5d6962ab1e3cd424e758c3002b8665f7b0d8dcee9fe9e288d7751ac194"

    /** LocalCache holds notes weakly: the fixtures are kept here so they outlive a collection. */
    private val held = ArrayList<Note>()

    @BeforeTest
    fun mainDispatcher() = Dispatchers.setMain(Dispatchers.Unconfined)

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()

    private fun id(seed: String) = seed.repeat(64 / seed.length)

    /** "<text> nostr:note1…" pointing at the event seeded with [seed]. */
    private fun quoting(
        seed: String,
        text: String,
    ) = "$text nostr:${NNote.create(id(seed))}"

    private fun event(
        seed: String,
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
        pubKey: String = alice,
    ): Event = EventFactory.create(id(seed), pubKey, 1_790_900_000, kind, tags, content, "0".repeat(128))

    private fun consume(event: Event): Note {
        val note = LocalCache.getOrCreateNote(event.id)
        held += note
        LocalCache.justConsume(event, null, wasVerified = true)
        assertTrue(note.event != null, "the cache refused ${event.kind}:${event.id}")
        return note
    }

    /**
     * Two events of one kind whose texts quote each other, built by [build] (its own seed, the
     * other's seed): like two videos citing each other, each quote leads back to the other.
     */
    private fun pair(
        seed: String,
        peer: String,
        build: (me: String, other: String) -> Event,
    ): Event {
        consume(build(peer, seed))
        return build(seed, peer)
    }

    // NIP-10: the text path, which already spends the row's budget. The control case.
    @Test fun textNote() = check(pair("10", "11") { me, other -> event(me, 1, emptyArray(), quoting(other, "See also")) })

    // NIP-71: `content` is the video's summary, `title` is required, the file is an `imeta`.
    @Test fun video() =
        check(
            pair("12", "13") { me, other ->
                event(
                    me,
                    21,
                    arrayOf(
                        arrayOf("title", "A walk in the park $me"),
                        arrayOf("imeta", "url https://example.com/walk-$me.mp4", "m video/mp4", "dim 1280x720"),
                    ),
                    quoting(other, "Filmed on a Sunday, like"),
                )
            },
        )

    // NIP-56: `p` with the report type in its third slot; `content` is the reporter's comment.
    @Test fun report() = check(pair("14", "15") { me, other -> event(me, 1984, arrayOf(arrayOf("p", bob, "spam")), quoting(other, "Same as")) })

    // Kind 1808 audio header: `content` is the description.
    @Test fun audioHeader() = check(pair("16", "17") { me, other -> event(me, 1808, arrayOf(arrayOf("stream_url", "https://example.com/talk-$me.mp3")), quoting(other, "A reply to")) })

    // NIP-82 application: `d` is the app id, `name` its name, `content` the description.
    @Test fun softwareApp() =
        check(
            pair("18", "19") { me, other ->
                event(me, 32267, arrayOf(arrayOf("d", "com.example.app$me"), arrayOf("name", "Example $me")), quoting(other, "A sibling of"))
            },
        )

    // NIP-82 release on kind 30063: `i` (app id) and `version` tell it from a NIP-51 set; `content` is the notes.
    @Test fun softwareRelease() =
        check(
            pair("1a", "1b") { me, other ->
                event(
                    me,
                    30063,
                    arrayOf(arrayOf("d", "com.example.app@1.0.$me"), arrayOf("i", "com.example.app"), arrayOf("version", "1.0.$me")),
                    quoting(other, "Fixes what came in"),
                )
            },
        )

    // NIP-F4 episode: `title`, `description` and `audio` (url, mime type) tags.
    @Test fun podcastEpisode() =
        check(
            pair("1c", "1d") { me, other ->
                event(
                    me,
                    54,
                    arrayOf(
                        arrayOf("title", "Episode $me"),
                        arrayOf("description", quoting(other, "Follows")),
                        arrayOf("audio", "https://example.com/ep-$me.mp3", "audio/mpeg"),
                    ),
                    "",
                )
            },
        )

    // Music track (36787): `content` is the description, beside `title` and `url`.
    @Test fun musicTrack() =
        check(
            pair("1e", "1f") { me, other ->
                event(me, 36787, arrayOf(arrayOf("d", "song-$me"), arrayOf("title", "Song $me"), arrayOf("url", "https://example.com/song-$me.mp3")), quoting(other, "B-side of"))
            },
        )

    // Music playlist (34139): `content` is the long description.
    @Test fun musicPlaylist() = check(pair("20", "21") { me, other -> event(me, 34139, arrayOf(arrayOf("d", "mix-$me"), arrayOf("title", "Mix $me")), quoting(other, "Continues")) })

    // Zap poll (6969): each `poll_option` is `[index, text]`.
    @Test fun zapPoll() =
        check(
            pair("22", "23") { me, other ->
                event(me, 6969, arrayOf(arrayOf("poll_option", "0", quoting(other, "The one in")), arrayOf("poll_option", "1", "Neither")), "Which one?")
            },
        )

    // Attestation (31871): `content` is the attestor's note. Its card already spends the row's budget.
    @Test fun attestation() = check(pair("24", "25") { me, other -> event(me, 31871, arrayOf(arrayOf("d", "attest-$me"), arrayOf("s", "valid")), quoting(other, "Checked with")) })

    // Kind 1010 edit proposal: the card embeds the note it edits, drawn with the proposed text
    // (`content`), which here quotes a note that quotes back.
    @Test fun textModification() {
        val other = pair("26", "27") { me, peer -> event(me, 1, emptyArray(), quoting(peer, "See")) }
        consume(other)
        val original = consume(event("28", 1, emptyArray(), "Original"))
        check(event("ee", 1010, arrayOf(arrayOf("e", original.idHex)), quoting("26", "Fixed, see")))
    }

    private fun check(event: Event) {
        val note = consume(event)
        val full = drawQuotes(note, makeItShort = false, quotesLeft = 1)
        assertTrue(full.drawn > 0, "kind ${event.kind}: the fixture never drew its quote, so it tests nothing")
        assertEquals(1, full.maxDepth, "kind ${event.kind}: quotes nested ${full.maxDepth} levels deep from a row with quotesLeft = 1")

        val short = drawQuotes(note, makeItShort = true, quotesLeft = 2)
        assertEquals(0, short.drawn, "kind ${event.kind}: a short row drew ${short.drawn} quotes")
    }

    private class Quotes {
        var drawn = 0
        var maxDepth = 0
    }

    private val localDepth = compositionLocalOf { 0 }

    private fun drawQuotes(
        note: Note,
        makeItShort: Boolean,
        quotesLeft: Int,
    ): Quotes {
        val quotes = Quotes()
        val counting =
            InlineQuoteRenderer { quoted, left, background, accountViewModel, nav ->
                val depth = localDepth.current + 1
                quotes.drawn++
                if (depth > quotes.maxDepth) quotes.maxDepth = depth
                if (depth < DEPTH_CAP) {
                    CompositionLocalProvider(localDepth provides depth) {
                        DefaultInlineQuoteRenderer.Render(quoted, left, background, accountViewModel, nav)
                    }
                }
            }
        val nav = mockk<INav>(relaxed = true)
        val scene =
            ImageComposeScene(width = 800, height = 4000, density = Density(1f)) {
                AmethystPreviewTheme(dark = false) {
                    CompositionLocalProvider(LocalInlineQuoteRenderer provides counting, LocalRichTextPlatform provides QuotingPlatform) {
                        Surface(color = MaterialTheme.colorScheme.background) {
                            Column {
                                NoteCompose(baseNote = note, makeItShort = makeItShort, quotesLeft = quotesLeft, accountViewModel = mockAccountViewModel(), nav = nav)
                            }
                        }
                    }
                }
            }
        try {
            scene.render(0)
            repeat(SETTLE_FRAMES) { frame ->
                Thread.sleep(FRAME_MILLIS)
                scene.render((frame + 1) * FRAME_MILLIS * 1_000_000L)
            }
        } catch (e: Throwable) {
            fail("rendering kind ${note.event?.kind} threw", e)
        } finally {
            scene.close()
        }
        return quotes
    }

    /**
     * The front ends' segment renderer, which turns a `nostr:` mention into a quote (the default
     * platform prints it as text). A kind whose NIP says Markdown (NIP-82's releases) goes to the
     * shared document renderer, the platform's default [RichTextPlatform.Markdown], so its quotes
     * are counted there too. Secret messages are not exercised.
     */
    private object QuotingPlatform : RichTextPlatform {
        override fun segmentRenderer(
            accountViewModel: AccountViewModel,
            nav: INav,
            backgroundColor: MutableState<Color>,
            callbackUri: String?,
            canPreview: Boolean,
        ): RichTextSegmentRenderer = DefaultRichTextSegmentRenderer(accountViewModel, nav, backgroundColor, callbackUri, canPreview)

        @Composable
        override fun SecretMessage(
            content: RichTextViewerState,
            callbackUri: String?,
            quotesLeft: Int,
            backgroundColor: MutableState<Color>,
            accountViewModel: AccountViewModel,
            nav: INav,
        ) = Text(content.paragraphs.joinToString("\n") { paragraph -> paragraph.words.joinToString(" ") { it.segmentText } })
    }

    private companion object {
        const val DEPTH_CAP = 5
        const val SETTLE_FRAMES = 15
        const val FRAME_MILLIS = 60L
    }
}
