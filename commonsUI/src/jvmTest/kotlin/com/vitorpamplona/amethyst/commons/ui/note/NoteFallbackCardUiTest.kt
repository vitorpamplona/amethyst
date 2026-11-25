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

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.theme.AmethystPreviewTheme
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.utils.EventFactory
import io.mockk.mockk
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
 * What a note row draws for content that is not a note's text: a typed kind whose card declines
 * it (another app's record on a shared kind) reaches the unknown-kind card, never the text path,
 * which would print the raw JSON.
 */
class NoteFallbackCardUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val alice = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"

    /** LocalCache holds notes weakly: the fixtures are kept here so they outlive a collection. */
    private val held = ArrayList<Note>()

    private fun event(
        seed: Char,
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
    ): Event = EventFactory.create("c$seed".repeat(32), alice, 1_790_900_000, kind, tags, content, "0".repeat(128))

    /**
     * The note for [event], as the cache stores it. Some records the cache declines to keep (a
     * stall with no name); a note can still be handed one (a link, a signer preview), so those are
     * loaded into the note directly.
     */
    private fun consume(event: Event): Note {
        val note = LocalCache.getOrCreateNote(event.id)
        held += note
        LocalCache.justConsume(event, null, wasVerified = true)
        if (note.event == null) note.loadEvent(event, LocalCache.getOrCreateUser(event.pubKey), emptyList())
        assertTrue(note.event != null, "could not load ${event.kind}:${event.id}")
        return note
    }

    private fun show(note: Note) {
        val nav = mockk<INav>(relaxed = true)
        compose.setContent {
            AmethystPreviewTheme(dark = false) {
                NoteCompose(baseNote = note, quotesLeft = 2, accountViewModel = mockAccountViewModel(), nav = nav)
            }
        }
        compose.waitForIdle()
    }

    private fun shown(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun assertUnknownKindCard(what: String) {
        assertTrue(shown(UNSUPPORTED), "$what: no unknown-kind card")
        assertTrue(!shown("{\""), "$what: printed its raw JSON content")
    }

    // The control: kind 1's content is the note's text (NIP-10), JSON or not, and stays text.
    @Test
    fun textNoteKeepsItsText() {
        show(consume(event('1', 1, emptyArray(), """{"a":1}""")))
        assertTrue(shown("""{"a":1}"""), "a kind-1 note lost its text")
        assertTrue(!shown(UNSUPPORTED), "a kind-1 note became an unknown-kind card")
    }

    // NIP-78: an app's own data on 30078 (not the Podcasting-2.0 show metadata `d` tag).
    @Test
    fun appSpecificData() {
        show(consume(event('2', 30078, arrayOf(arrayOf("d", "com.example.settings")), """{"theme":"dark","zap_amount":21}""")))
        assertUnknownKindCard("kind 30078")
    }

    // NIP-15: a stall's JSON content must have a `name`; another app's record on 30017 has none.
    @Test
    fun malformedStall() {
        show(consume(event('3', 30017, arrayOf(arrayOf("d", "typing-game")), """{"score":42,"level":"hard"}""")))
        assertUnknownKindCard("kind 30017 without a name")
    }

    // Kind 31337 without an audio file to play is another app's record (ad-skip data, game state).
    @Test
    fun audioTrackWithoutAudio() {
        show(consume(event('4', 31337, arrayOf(arrayOf("d", "ad-skips")), """{"segments":[[12,40]]}""")))
        assertUnknownKindCard("kind 31337 without audio")
    }

    // Mint-recommendation junk on 38000 drew nothing at all.
    @Test
    fun unrecognizedMintRecommendation() {
        show(consume(event('5', 38000, arrayOf(arrayOf("d", "x")), """{"vote":1}""")))
        assertUnknownKindCard("unrecognized kind 38000")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    companion object {
        private const val UNSUPPORTED = "Unsupported"

        // The preview account launches on Dispatchers.Main, which a headless JVM lacks. Set for the
        // whole class: the compose rule closes its scene after per-test @After methods run.
        @JvmStatic
        @BeforeClass
        fun mainDispatcher() = Dispatchers.setMain(Dispatchers.Unconfined)

        @JvmStatic
        @AfterClass
        fun resetMainDispatcher() = Dispatchers.resetMain()
    }
}
