/**
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
package com.vitorpamplona.quartz.nip17Dm

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.DeterministicSigner
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapPrivateEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NIP17FactoryTest {
    private val signer = TestSigner()
    private val factory = TestNIP17Factory()
    private val recipients =
        listOf(
            PTag("01".repeat(32)),
            PTag("02".repeat(32)),
        )

    @Test
    fun `chat messages are emitted as unsigned rumors`() =
        runTest {
            val template = ChatMessageEvent.build("hi", recipients, createdAt = FIXED_CREATED_AT)

            val result = factory.createMessageNIP17(template, signer)

            assertUnsignedRumor(result.msg, template)
        }

    @Test
    fun `reactions stay unsigned inside gift wraps`() =
        runTest {
            val parentTemplate = ChatMessageEvent.build("root", recipients, createdAt = FIXED_CREATED_AT)
            val parent = factory.createMessageNIP17(parentTemplate, signer).msg as ChatMessageEvent
            val bundle = EventHintBundle<Event>(parent)
            val to = parent.groupMembers().toList()

            val result = factory.createReactionWithinGroup("❤️", bundle, to, signer)

            assertUnsignedRumor(result.msg, ReactionEvent.build("❤️", bundle, createdAt = result.msg.createdAt))
        }

    @Test
    fun `emoji reactions are also unsigned`() =
        runTest {
            val parentTemplate = ChatMessageEvent.build("root", recipients, createdAt = FIXED_CREATED_AT)
            val parent = factory.createMessageNIP17(parentTemplate, signer).msg as ChatMessageEvent
            val bundle = EventHintBundle<Event>(parent)
            val to = parent.groupMembers().toList()
            val emoji = EmojiUrlTag(code = "wave", url = "https://example.com/wave.png")

            val result = factory.createReactionWithinGroup(emoji, bundle, to, signer)

            assertUnsignedRumor(
                result.msg,
                ReactionEvent.build(emoji, bundle, createdAt = result.msg.createdAt),
            )
        }

    private fun assertUnsignedRumor(
        rumor: Event,
        template: EventTemplate<out Event>,
    ) {
        assertTrue(rumor.sig.isEmpty(), "Rumor should keep sig blank")
        assertEquals(signer.pubKey, rumor.pubKey, "Rumor must keep sender's public key")
        assertTrue(
            EventHasher.hashIdEquals(rumor.id, rumor.pubKey, template.createdAt, template.kind, template.tags, template.content),
            "Rumor id should match the template hash",
        )
    }

    private class TestSigner : NostrSigner(PRIMARY_KEY.pubKey.toHexKey()) {
        private val deterministicSigner = DeterministicSigner(PRIMARY_KEY)

        override fun isWriteable() = true

        override suspend fun <T : Event> sign(
            createdAt: Long,
            kind: Int,
            tags: Array<Array<String>>,
            content: String,
        ): T = deterministicSigner.sign(createdAt, kind, tags, content)

        override suspend fun nip04Encrypt(
            plaintext: String,
            toPublicKey: HexKey,
        ) = plaintext

        override suspend fun nip04Decrypt(
            ciphertext: String,
            fromPublicKey: HexKey,
        ) = ciphertext

        override suspend fun nip44Encrypt(
            plaintext: String,
            toPublicKey: HexKey,
        ) = plaintext

        override suspend fun nip44Decrypt(
            ciphertext: String,
            fromPublicKey: HexKey,
        ) = ciphertext

        override suspend fun decryptZapEvent(event: LnZapRequestEvent): LnZapPrivateEvent = throw UnsupportedOperationException("Not needed for this test")

        override suspend fun deriveKey(nonce: HexKey): HexKey = nonce

        override fun hasForegroundSupport() = true
    }

    private class TestNIP17Factory : NIP17Factory() {
        override suspend fun createWraps(
            event: Event,
            to: Set<HexKey>,
            signer: NostrSigner,
        ): List<GiftWrapEvent> {
            require(event.sig.isBlank())
            return emptyList()
        }
    }

    companion object {
        private val PRIMARY_KEY = KeyPair(ByteArray(32) { 1 })
        private const val FIXED_CREATED_AT = 1_706_000_000L
    }
}
