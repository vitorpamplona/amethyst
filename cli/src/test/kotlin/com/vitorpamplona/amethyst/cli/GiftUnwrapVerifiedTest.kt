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
package com.vitorpamplona.amethyst.cli

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip19Bech32.toNsec
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Quartz unwraps without checking what the wrap held, so `amy gift unwrap` says whether the seal
 * verifies, and prints the event either way.
 */
class GiftUnwrapVerifiedTest {
    private val me = KeyPair()
    private val sender = NostrSignerInternal(KeyPair())
    private val inner = NostrSignerSync(KeyPair()).sign(TextNoteEvent.build("psst"))

    private fun unwrap(wrap: GiftWrapEvent): JsonNode =
        withSharedAmyHome {
            val init = amy("--secret-backend", "plaintext", "--account", "me", "init", "--nsec", me.privKey!!.toNsec())
            assertEquals(0, init.exit, init.stderr)
            val r = amy("--json", "gift", "unwrap", wrap.toJson())
            assertEquals(0, r.exit, r.stderr)
            JsonNode.parse(r.stdoutLines.single())
        }

    private fun wrapOf(seal: SealEvent) = runBlocking { GiftWrapEvent.create(event = seal, recipientPubKey = me.pubKey.toHexKey()) }

    @Test
    fun aSoundSealVerifies() {
        val seal = runBlocking { SealEvent.create(event = inner, encryptTo = me.pubKey.toHexKey(), signer = sender) }
        val out = unwrap(wrapOf(seal))
        assertEquals("true", out["verified"].element.toString())
        assertEquals("psst", out["event"]["content"].element.toString().trim('"'))
    }

    @Test
    fun aSealWithABadSignatureIsPrintedButNotVerified() {
        val seal = runBlocking { SealEvent.create(event = inner, encryptTo = me.pubKey.toHexKey(), signer = sender) }
        val forged = SealEvent(seal.id, seal.pubKey, seal.createdAt, seal.tags, seal.content, "00".repeat(64))
        val out = unwrap(wrapOf(forged))
        assertEquals("false", out["verified"].element.toString())
        assertEquals("psst", out["event"]["content"].element.toString().trim('"'), "printed anyway")
    }
}
