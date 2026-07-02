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
package com.vitorpamplona.quartz.podcasts

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip22Comments.RootScope
import com.vitorpamplona.quartz.nipF4Podcasts.episode.PodcastEpisodeEvent
import com.vitorpamplona.quartz.nipF4Podcasts.episode.tags.AudioTag
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.Podcasting20EpisodeEvent
import com.vitorpamplona.quartz.utils.DeterministicSigner
import com.vitorpamplona.quartz.utils.nsecToKeyPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies that NIP-22 (kind:1111) comments scope correctly onto podcast episodes of both drafts.
 * The app's comment composer always routes through [CommentEvent.replyBuilder], so this exercises
 * exactly the path a "reply to this episode" tap takes — proving comments already work end-to-end.
 */
class PodcastCommentScopeTest {
    private val signer =
        DeterministicSigner(
            "nsec10g0wheggqn9dawlc0yuv6adnat6n09anr7eyykevw2dm8xa5fffs0wsdsr".nsecToKeyPair(),
        )

    @Test
    fun `episode events declare themselves as NIP-22 comment roots`() {
        // Consistency with every other commentable content type (articles, videos, …).
        val pc20 =
            signer.sign<Podcasting20EpisodeEvent>(
                Podcasting20EpisodeEvent.build("ep-1", "E", listOf(PodcastAudio("https://x/a.mp3")), "Thu, 04 Nov 2023 12:00:00 GMT"),
            )
        val f4 =
            signer.sign<PodcastEpisodeEvent>(
                PodcastEpisodeEvent.build("E", "d", listOf(AudioTag("https://x/a.mp3"))),
            )
        assertTrue(pc20 is RootScope)
        assertTrue(f4 is RootScope)
    }

    @Test
    fun `comment on a Podcasting 2 point 0 episode roots on its address`() {
        val episode =
            signer.sign<Podcasting20EpisodeEvent>(
                Podcasting20EpisodeEvent.build("ep-1", "Episode", listOf(PodcastAudio("https://x/a.mp3")), "Thu, 04 Nov 2023 12:00:00 GMT"),
            )
        val comment = signer.sign<CommentEvent>(CommentEvent.replyBuilder("great episode", EventHintBundle<Event>(episode)))

        assertEquals(CommentEvent.KIND, comment.kind)
        // Addressable root: the comment carries the episode's `a`/`A` address (30054:pubkey:d).
        assertTrue(comment.rootAddressIds().contains(episode.addressTag()))
        assertTrue(comment.hasRootScopeKind(Podcasting20EpisodeEvent.KIND.toString()))
        assertTrue(comment.rootEventIds().contains(episode.id))
    }

    @Test
    fun `comment on a NIP-F4 episode roots on its event id`() {
        val episode =
            signer.sign<PodcastEpisodeEvent>(
                PodcastEpisodeEvent.build("Episode", "notes", listOf(AudioTag("https://x/a.mp3"))),
            )
        val comment = signer.sign<CommentEvent>(CommentEvent.replyBuilder("great episode", EventHintBundle<Event>(episode)))

        assertEquals(CommentEvent.KIND, comment.kind)
        // Regular (non-addressable) root: scoped by event id via the `e`/`E` tag, no address root.
        assertTrue(comment.rootEventIds().contains(episode.id))
        assertTrue(comment.hasRootScopeKind(PodcastEpisodeEvent.KIND.toString()))
        assertTrue(comment.rootAddressIds().isEmpty())
    }
}
