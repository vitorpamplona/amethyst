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
package com.vitorpamplona.quartz.nipC0CodeSnippets

import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import kotlin.test.Test
import kotlin.test.assertEquals

class CodeSnippetHintProviderTest {
    private val author = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val owner = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.damus.io/")!!

    private fun snippet(repo: String) = CodeSnippetEvent("00".repeat(32), author, 1700000000, arrayOf(arrayOf("repo", repo)), "println()", "00".repeat(64))

    @Test
    fun aNip34NaddrRepoIsAnAddressWithItsRelays() {
        val event = snippet(NAddress.create(30617, owner, "amethyst", relay))
        assertEquals(listOf("30617:$owner:amethyst"), event.linkedAddressIds())
        assertEquals(listOf(AddressHint("30617:$owner:amethyst", relay)), event.addressHints())
    }

    @Test
    fun aNostrUriAndARawAddressIdAreAddressesToo() {
        assertEquals(listOf("30617:$owner:amethyst"), snippet("nostr:" + NAddress.create(30617, owner, "amethyst", relay)).linkedAddressIds())

        val raw = snippet("30617:$owner:amethyst")
        assertEquals(listOf("30617:$owner:amethyst"), raw.linkedAddressIds())
        assertEquals(emptyList(), raw.addressHints())
    }

    @Test
    fun aUrlRepoIsNotAnAddress() {
        val event = snippet("https://github.com/vitorpamplona/amethyst")
        assertEquals(emptyList(), event.linkedAddressIds())
        assertEquals(emptyList(), event.addressHints())
    }
}
