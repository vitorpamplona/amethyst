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

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalOnlyTest {
    private fun ip(literal: String): InetAddress = InetAddress.getByName(literal)

    @Test
    fun machineAndPrivateNetworkAddressesAreLocal() {
        listOf("127.0.0.1", "127.0.0.2", "::1", "10.1.2.3", "172.17.0.2", "192.168.1.9", "169.254.1.1", "100.64.0.1", "100.127.255.254", "fd12:3456::1", "fe80::1")
            .forEach { assertTrue(LocalOnly.isLocalAddress(ip(it)), it) }
    }

    @Test
    fun publicAddressesAreNot() {
        listOf("1.1.1.1", "8.8.8.8", "100.128.0.1", "172.32.0.1", "2606:4700::1111")
            .forEach { assertFalse(LocalOnly.isLocalAddress(ip(it)), it) }
    }

    @Test
    fun hostOfReadsRelayUrlsAndBareHosts() {
        assertEquals("relay.example", LocalOnly.hostOf("wss://relay.example:443/path"))
        assertEquals("relay.example", LocalOnly.hostOf("Relay.Example"))
        assertEquals("::1", LocalOnly.hostOf("ws://[::1]:7777"))
        assertEquals("host", LocalOnly.hostOf("ws://user@host:1"))
        assertEquals(null, LocalOnly.hostOf(""))
    }

    // A resolver that answers from a table, as SurgeDns answers from its persisted cache.
    private fun resolver(vararg answers: Pair<String, String>): Dns {
        val table = answers.toMap()
        return Dns { host -> listOf(ip(table[host] ?: throw UnknownHostException(host))) }
    }

    @Test
    fun theGuardedResolverRefusesAPublicAddressItServes() {
        val dns = LocalOnly.dns(resolver("stale.example" to "1.1.1.1", "lan.example" to "192.168.1.9"), guarded = true)
        assertFailsWith<UnknownHostException> { dns.lookup("stale.example") }
        assertEquals(listOf(ip("192.168.1.9")), dns.lookup("lan.example"))
    }

    @Test
    fun anUnguardedResolverIsLeftAlone() {
        val inner = resolver("stale.example" to "1.1.1.1")
        assertEquals(inner, LocalOnly.dns(inner, guarded = false))
    }

    @Test
    fun dialableKeepsOnlyLocalRelays() =
        runBlocking {
            val local = RelayUrlNormalizer.normalizeOrNull("ws://127.0.0.1:7777")!!
            val public = RelayUrlNormalizer.normalizeOrNull("wss://1.1.1.1")!!
            assertEquals(setOf(local), LocalOnly.dialable(listOf(local, public), guarded = true))
            assertEquals(setOf(local, public), LocalOnly.dialable(listOf(local, public), guarded = false))
        }
}
