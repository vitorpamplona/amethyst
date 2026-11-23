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
package com.vitorpamplona.amethyst.service.notifications

import android.os.Looper
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip22Comments.notify
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The cache-observer predicate of [NotificationDispatcher]: a private group rumor that looks like a
 * public NIP-22 reply must not reach the public reply notification (plaintext past the privacy lock
 * and a Reply action that broadcasts publicly), while public replies and NIP-17 DMs still notify.
 */
class NotificationDispatcherPredicateTest {
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.example/")!!
    private val me = NostrSignerInternal(KeyPair())
    private val other = NostrSignerInternal(KeyPair())
    private val since = TimeUtils.now() - 60

    @Before
    fun setup() {
        // LocalCache.consume refuses the main thread; plain JVM tests have no Looper (null == null reads
        // as "main"). Distinct mocks make it a worker thread. See BuzzDmNotificationResolutionTest.
        mockkStatic(Looper::class)
        every { Looper.myLooper() } returns mockk<Looper>()
        every { Looper.getMainLooper() } returns mockk<Looper>()
    }

    @After
    fun tearDown() {
        unmockkStatic(Looper::class)
    }

    /** The same event without its signature: how a decrypted rumor sits in the cache. */
    private fun CommentEvent.asRumor() = CommentEvent(id, pubKey, createdAt, tags, content, "")

    private fun ChatEvent.asRumor() = ChatEvent(id, pubKey, createdAt, tags, content, "")

    private fun ChatMessageEvent.asRumor() = ChatMessageEvent(id, pubKey, createdAt, tags, content, "")

    private fun accepts(event: Event) = NotificationDispatcher.isNotificationCandidate(event, setOf(me.pubKey), since)

    @Test
    fun `a signed public kind-1111 reply to my note still notifies`() =
        runBlocking {
            val myNote = me.sign(TextNoteEvent.build("my public note"))
            LocalCache.justConsume(myNote, relay, false)

            val reply = other.sign(CommentEvent.replyBuilder("a public reply", EventHintBundle(myNote)))
            LocalCache.justConsume(reply, relay, false)

            assertTrue("a signed reply that p-tags me notifies", accepts(reply))
        }

    @Test
    fun `an unsigned kind-1111 group thread message that p-tags me does not reach the public path`() =
        runBlocking {
            // A Marmot group's kind-9 message by me, then a member's kind-1111 thread reply to it that
            // p-tags me, as MDK sends a thread message mentioning a member. Both are unsigned rumors.
            val myGroupMessage = me.sign(ChatEvent.build("my group message")).asRumor()
            LocalCache.justConsume(myGroupMessage, null, true)

            val threadReply =
                other
                    .sign(
                        CommentEvent.replyBuilder("a private thread reply", EventHintBundle(myGroupMessage)) {
                            notify(PTag(me.pubKey))
                        },
                    ).asRumor()
            LocalCache.justConsume(threadReply, null, true)

            assertTrue("the rumor is in the cache, so the observer would see it", LocalCache.getNoteIfExists(threadReply.id)?.event != null)
            assertFalse("an unsigned rumor never goes to the public reply notification", accepts(threadReply))
        }

    @Test
    fun `a rumor cannot pass by borrowing a public event's signature`() =
        runBlocking {
            val myNote = me.sign(TextNoteEvent.build("my public note 2"))
            LocalCache.justConsume(myNote, relay, false)

            val public = other.sign(CommentEvent.replyBuilder("public", EventHintBundle(myNote)))
            // Same id and signature, different content: only the id check catches it.
            val forged = CommentEvent(public.id, public.pubKey, public.createdAt, public.tags, "private text", public.sig)
            LocalCache.justConsume(forged, null, true)

            assertFalse("the id no longer hashes the content", accepts(forged))
        }

    @Test
    fun `an unsigned NIP-17 DM rumor addressed to me still notifies`() =
        runBlocking {
            val dm = other.sign(ChatMessageEvent.build("hi", listOf(PTag(me.pubKey)))).asRumor()
            LocalCache.justConsume(dm, null, true)

            assertTrue("DM rumors are routed to the private DM renderer on purpose", accepts(dm))
        }
}
