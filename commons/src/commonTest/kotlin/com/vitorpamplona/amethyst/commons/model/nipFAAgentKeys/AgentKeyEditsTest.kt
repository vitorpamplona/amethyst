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
package com.vitorpamplona.amethyst.commons.model.nipFAAgentKeys

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip51Lists.encryption.PrivateTagsInContent
import com.vitorpamplona.quartz.nipFAAgentKeys.AgentKeyEvent
import com.vitorpamplona.quartz.nipFAAgentKeys.UnreadableAgentKeysException
import com.vitorpamplona.quartz.nipFAAgentKeys.tags.AgentKeyTag
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentKeyEditsTest {
    private val signer = NostrSignerInternal(KeyPair())
    private val openRouter = AgentKeyTag("sk-or-v1-aaaa", "https://openrouter.ai/api/v1")
    private val anthropic = AgentKeyTag("sk-ant-bbbb", "https://api.anthropic.com/v1")
    private val ollama = AgentKeyTag("", "http://localhost:11434/v1")

    private suspend fun signed(template: EventTemplate<AgentKeyEvent>?): AgentKeyEvent = assertIs<AgentKeyEvent>(Event.fromJson(signer.sign(template!!).toJson()))

    @Test
    fun aKeyReplacesTheOneForItsUrlWhereItStood() {
        val replacement = AgentKeyTag("sk-ant-cccc", "https://API.anthropic.com/v1/")
        assertEquals(listOf(openRouter, replacement, ollama), AgentKeyEdits.withKey(listOf(openRouter, anthropic, ollama), replacement, makeDefault = false))
    }

    @Test
    fun aNewKeyGoesLastUnlessItIsTheDefault() {
        assertEquals(listOf(openRouter, ollama), AgentKeyEdits.withKey(listOf(openRouter), ollama, makeDefault = false))
        assertEquals(listOf(ollama, openRouter), AgentKeyEdits.withKey(listOf(openRouter), ollama, makeDefault = true))
        assertEquals(listOf(ollama), AgentKeyEdits.withKey(emptyList(), ollama, makeDefault = false))
    }

    @Test
    fun aDefaultAndARemovalAreFoundByUrl() {
        val keys = listOf(openRouter, anthropic, ollama)
        assertEquals(listOf(ollama, openRouter, anthropic), AgentKeyEdits.withDefault(keys, "http://localhost:11434/v1/"))
        assertEquals(listOf(openRouter, ollama), AgentKeyEdits.without(keys, "https://api.anthropic.com/v1"))
        assertNull(AgentKeyEdits.withDefault(keys, "https://nowhere.example/v1"))
        assertNull(AgentKeyEdits.without(keys, "https://nowhere.example/v1"))
        assertEquals(anthropic, AgentKeyEdits.forUrl(keys, "https://api.anthropic.com/v1/"))
    }

    @Test
    fun aKeyIsSavedOnlyWhereNipFaLetsItGo() {
        assertNull(AgentKeyEdits.problem("https://openrouter.ai/api/v1", "sk-or-v1-aaaa"))
        assertNull(AgentKeyEdits.problem("http://127.0.0.1:8080/v1", "sk-local"))
        assertNull(AgentKeyEdits.problem("http://localhost:11434/v1", ""))
        // No key to leak: plain http anywhere.
        assertNull(AgentKeyEdits.problem("http://runner.lan:8080/v1", ""))
        assertEquals(AgentKeyProblem.NEEDS_HTTPS, AgentKeyEdits.problem("http://runner.lan:8080/v1", "sk-x"))
        assertEquals(AgentKeyProblem.BAD_URL, AgentKeyEdits.problem("ftp://example.com", ""))
        assertEquals(AgentKeyProblem.BAD_URL, AgentKeyEdits.problem("openrouter.ai", "sk"))
        assertEquals(AgentKeyProblem.NEEDS_KEY, AgentKeyEdits.problem("https://openrouter.ai/api/v1", ""))
    }

    @Test
    fun aRewriteKeepsEveryOtherTagAndMovesTheClockOn() =
        runTest {
            val earlier =
                signed(
                    EventTemplate(
                        1_000,
                        AgentKeyEvent.KIND,
                        arrayOf(arrayOf("-")),
                        signer.nip44Encrypt(PrivateTagsInContent.encode(arrayOf(openRouter.toTagArray(), arrayOf("model", "x"), arrayOf("key", "sk-odd", "https://a.example/v1", "extra"))), signer.pubKey),
                    ),
                )
            val next = signed(rewriteAgentKeys(earlier, signer, now = 500) { AgentKeyEdits.withKey(it, ollama, makeDefault = true) })
            assertEquals(1_001, next.createdAt)
            assertEquals(listOf(ollama, openRouter, AgentKeyTag("sk-odd", "https://a.example/v1")), next.agentKeys(signer))
            val private = next.privateTags(signer)!!
            assertTrue(private.any { it.contentEquals(arrayOf("model", "x")) })
            // A key tag with values this client doesn't know keeps them.
            assertTrue(private.any { it.contentEquals(arrayOf("key", "sk-odd", "https://a.example/v1", "extra")) })
            assertTrue(next.tags.any { it.contentEquals(arrayOf("-")) })
        }

    @Test
    fun nothingIsWrittenWhenNothingChanges() =
        runTest {
            val earlier = signed(AgentKeyEvent.build(listOf(openRouter), signer))
            assertNull(rewriteAgentKeys(earlier, signer) { AgentKeyEdits.withDefault(it, openRouter.url) })
            assertNull(rewriteAgentKeys(earlier, signer) { AgentKeyEdits.without(it, "https://nowhere.example/v1") })
            assertNull(rewriteAgentKeys(null, signer) { it })
            // Clearing keeps a valid, empty event.
            val cleared = signed(rewriteAgentKeys(earlier, signer) { emptyList() })
            assertEquals(emptyList(), cleared.agentKeys(signer))
        }

    @Test
    fun contentThatIsNotATagArrayIsNeverOverwritten() =
        runTest {
            val odd = signed(EventTemplate(1_000, AgentKeyEvent.KIND, emptyArray(), signer.nip44Encrypt("not json", signer.pubKey)))
            assertFailsWith<UnreadableAgentKeysException> { rewriteAgentKeys(odd, signer) { emptyList() } }
        }

    @Test
    fun keysAreReadFromTheWriteRelaysAndTheOutboxOnlyWithoutAList() {
        val mine = setOf(RelayUrlNormalizer.normalizeOrNull("wss://mine.example")!!)
        val defaults = setOf(RelayUrlNormalizer.normalizeOrNull("wss://default.example")!!)
        // A default relay answering "nothing here" must not stand in for the user's own relays.
        assertEquals(mine, AgentKeyEdits.readRelays(writeRelays = mine, outbox = mine + defaults))
        // No NIP-65 list yet: where this app publishes is all there is.
        assertEquals(defaults, AgentKeyEdits.readRelays(writeRelays = emptySet(), outbox = defaults))
    }
}
