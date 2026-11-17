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
package com.vitorpamplona.amethyst.commons.claudecode

import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NgitTest {
    private val id = "a".repeat(64)

    @Test
    fun readsAPublishedProposalAfterTheProgressLines() {
        val nevent = NEvent.create(id, null, null, null)
        // As ngit 3 prints it: progress first, then its pretty-printed JSON document.
        val output =
            """
            fetching updates...
            posting 2 patches with a cover letter
            {
              "command_status": "ok",
              "action": "created",
              "entity": "patch",
              "id": "$nevent"
            }
            """.trimIndent()
        val result = Ngit.parseSend(0, output)
        assertIs<Ngit.SendResult.Published>(result)
        assertEquals(id, result.eventId)
        assertEquals(false, result.updated)
    }

    @Test
    fun saysWhyItFailed() {
        val output = "{\n  \"command_status\": \"error\",\n  \"error\": \"no nostr account: run ngit account login\"\n}"
        val result = Ngit.parseSend(1, output)
        assertIs<Ngit.SendResult.Failed>(result)
        assertEquals("no nostr account: run ngit account login", result.reason)
        // No JSON at all (an old ngit): its own words.
        assertEquals("error: unexpected argument '--json'", (Ngit.parseSend(2, "error: unexpected argument '--json'\n") as Ngit.SendResult.Failed).reason)
    }

    /**
     * Against a real ngit and relay: set NGIT_LIVE_DIR to a checkout on a feature branch whose ngit
     * is logged in, NGIT_LIVE_REPO to the repository's naddr and NGIT_LIVE_BASE to the commit the
     * branch starts from. Publishes a proposal: point it at a throwaway local relay.
     */
    @Test
    fun sendsAProposalThroughTheUsersNgit() {
        val dir = System.getenv("NGIT_LIVE_DIR") ?: return
        val repo = System.getenv("NGIT_LIVE_REPO") ?: return
        val base = System.getenv("NGIT_LIVE_BASE") ?: return
        runBlocking {
            val accounts = Ngit.accounts(File(dir))
            assertTrue(accounts.any { it.active }, "ngit is logged in")
            val result = Ngit.send(File(dir), repo, base, "Second line", "Adds a second line.", proposalId = null)
            assertIs<Ngit.SendResult.Published>(result, result.toString())
            println("published ${result.nevent} ${result.eventId}")
        }
    }

    @Test
    fun readsAccounts() {
        val output =
            """{"accounts":[{"name":"Vitor","npub":"npub1abc","aliases":[],"scopes":["global"],"active":true,"selectors":[],"signers":[]}],"unavailable_accounts":[]}"""
        assertEquals(listOf(Ngit.Account("Vitor", "npub1abc", true)), Ngit.parseAccounts(output))
        assertTrue(Ngit.parseAccounts("""{"accounts":[],"unavailable_accounts":[]}""").isEmpty())
    }
}
