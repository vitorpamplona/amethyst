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
package com.vitorpamplona.quartz.nipA0VoiceMessages

import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VoiceReplyRootScopeTest {
    private val audio = AudioMeta("https://blossom.example/a.m4a", "audio/mp4", "f".repeat(64), 3, listOf(0.1f))

    @Test
    fun aVoiceReplyNamesItsThreadsRootScope() {
        val signer = NostrSignerSync()
        val voice = VoiceEvent("9".repeat(64), "1".repeat(64), 1, arrayOf(arrayOf("imeta", "url https://blossom.example/v.m4a")), "", "0".repeat(128))

        val reply = signer.sign(VoiceReplyEvent.build(audio, EventHintBundle(voice)))
        assertEquals(voice.id, reply.rootEventId())
        assertEquals(voice.pubKey, reply.rootAuthorKey())
        assertEquals(voice.id, reply.replyingTo())

        // A reply to that reply keeps the thread's root and names its new parent.
        val nested = signer.sign(VoiceReplyEvent.build(audio, EventHintBundle<BaseVoiceEvent>(reply)))
        assertEquals(voice.id, nested.rootEventId())
        assertEquals(reply.id, nested.replyingTo())
    }

    @Test
    fun aReplyToAnAddressRootedReplyKeepsTheAddressRoot() {
        val article = "30023:${"2".repeat(64)}:post"
        val parent =
            VoiceReplyEvent(
                "8".repeat(64),
                "3".repeat(64),
                1,
                arrayOf(arrayOf("A", article), arrayOf("K", "30023"), arrayOf("P", "2".repeat(64)), arrayOf("e", "7".repeat(64)), arrayOf("k", "1111")),
                "",
                "0".repeat(128),
            )
        val reply = NostrSignerSync().sign(VoiceReplyEvent.build(audio, EventHintBundle<BaseVoiceEvent>(parent)))
        assertEquals(listOf(article), reply.tags.filter { it[0] == "A" }.map { it[1] })
        assertEquals(listOf("30023"), reply.tags.filter { it[0] == "K" }.map { it[1] })
        assertTrue(reply.tags.none { it[0] == "E" })
        assertEquals(parent.id, reply.replyingTo())
    }

    @Test
    fun aReplyToALegacyReplyFindsTheVoiceMessageRoot() {
        // Published before voice replies wrote NIP-22 root tags: only the lowercase parent.
        val voiceId = "9".repeat(64)
        val voiceAuthor = "1".repeat(64)
        val legacy =
            VoiceReplyEvent(
                "8".repeat(64),
                "3".repeat(64),
                1,
                arrayOf(arrayOf("e", voiceId, "", voiceAuthor), arrayOf("k", "1222"), arrayOf("p", voiceAuthor)),
                "",
                "0".repeat(128),
            )
        val reply = NostrSignerSync().sign(VoiceReplyEvent.build(audio, EventHintBundle<BaseVoiceEvent>(legacy)))
        assertEquals(voiceId, reply.rootEventId())
        assertEquals(voiceAuthor, reply.rootAuthorKey())
        assertEquals(listOf("1222"), reply.tags.filter { it[0] == "K" }.map { it[1] })
        assertEquals(legacy.id, reply.replyingTo())
    }
}
