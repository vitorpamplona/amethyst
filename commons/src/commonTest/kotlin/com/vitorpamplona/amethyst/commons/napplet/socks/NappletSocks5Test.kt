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
package com.vitorpamplona.amethyst.commons.napplet.socks

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NappletSocks5Test {
    /** A peer that sends [input] and records what it is sent. */
    private class Script(
        input: ByteArray,
    ) : NappletSocksIo {
        private var at = 0
        private val incoming = input
        val sent = ArrayList<Byte>()

        override suspend fun readFully(count: Int): ByteArray {
            check(at + count <= incoming.size) { "the peer hung up" }
            return incoming.copyOfRange(at, at + count).also { at += count }
        }

        override suspend fun write(bytes: ByteArray) {
            sent += bytes.toList()
        }
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun acceptsANameConnect() =
        runTest {
            val name = "Example.ORG".encodeToByteArray()
            val peer = Script(bytes(5, 1, 0) + bytes(5, 1, 0, 3, name.size) + name + bytes(1, 187))
            assertEquals(NappletSocksTarget("Example.ORG", 443), NappletSocks5.accept(peer))
            assertContentEquals(bytes(5, 0), peer.sent.toByteArray())
        }

    @Test
    fun acceptsAddressConnects() =
        runTest {
            assertEquals(NappletSocksTarget("192.168.1.1", 80), NappletSocks5.accept(Script(bytes(5, 1, 0, 5, 1, 0, 1, 192, 168, 1, 1, 0, 80))))
            val v6 = bytes(5, 1, 0, 5, 1, 0, 4) + ByteArray(15) + bytes(1) + bytes(0, 80)
            assertEquals(NappletSocksTarget("::1", 80), NappletSocks5.accept(Script(v6)))
        }

    @Test
    fun refusesAuthenticationOnlyClientsAndOtherCommands() =
        runTest {
            val auth = Script(bytes(5, 1, 2))
            assertNull(NappletSocks5.accept(auth))
            assertContentEquals(bytes(5, 0xFF), auth.sent.toByteArray())

            // BIND (2) is not served.
            val bind = Script(bytes(5, 1, 0, 5, 2, 0, 1, 1, 2, 3, 4, 0, 80))
            assertNull(NappletSocks5.accept(bind))
            assertEquals(NappletSocks5.REPLY_COMMAND_UNSUPPORTED, bind.sent[3].toInt())
        }

    @Test
    fun connectsThroughAnUpstreamByName() =
        runTest {
            val upstream = Script(bytes(5, 0) + bytes(5, 0, 0, 1, 0, 0, 0, 0, 0, 0))
            assertEquals(NappletSocks5.REPLY_OK, NappletSocks5.connect(upstream, NappletSocksTarget("abc.onion", 80)))
            val name = "abc.onion".encodeToByteArray()
            // The name goes up unresolved (ATYP 3), so the upstream (Tor) resolves it.
            assertContentEquals(bytes(5, 1, 0) + bytes(5, 1, 0, 3, name.size) + name + bytes(0, 80), upstream.sent.toByteArray())

            val refused = Script(bytes(5, 0) + bytes(5, 4, 0, 1, 0, 0, 0, 0, 0, 0))
            assertEquals(NappletSocks5.REPLY_HOST_UNREACHABLE, NappletSocks5.connect(refused, NappletSocksTarget("1.2.3.4", 443)))
        }

    @Test
    fun refusesLocalNamesAndLiterals() {
        listOf(
            "localhost",
            "app.localhost",
            "printer.local",
            "router.lan",
            "nas.home.arpa",
            "db.internal",
            "napplet.local",
            "nabc.napplet.local",
            "127.0.0.1",
            "10.0.0.8",
            "172.16.4.4",
            "192.168.1.1",
            "169.254.169.254",
            "100.64.0.1",
            "0.0.0.0",
            "::1",
            "[::1]",
            "fe80::1",
            "fd00::5",
            "::ffff:192.168.1.1",
            "::ffff:7f00:1",
            // Literal-looking but not canonical: refused rather than guessed at.
            "127.1",
            "999.1.1.1",
            "",
        ).forEach { assertTrue(NappletSocksPolicy.refusesName(it), "$it should be refused") }

        listOf("example.org", "Example.ORG.", "1.1.1.1", "2606:4700:4700::1111", "localtest.me").forEach {
            assertFalse(NappletSocksPolicy.refusesName(it), "$it should pass the name check")
        }
        // A test's own local server is allowed only when asked for; the app's names never are.
        assertFalse(NappletSocksPolicy.refusesName("127.0.0.1", allowLocal = true))
        assertTrue(NappletSocksPolicy.refusesName("napplet.local", allowLocal = true))
    }

    @Test
    fun keepsOnlyPublicResolvedAddresses() {
        val loopback = bytes(127, 0, 0, 1)
        val public = bytes(93, 184, 216, 34)
        // localtest.me's answer: refused, whatever the name looked like.
        assertTrue(NappletSocksPolicy.connectable(listOf(loopback)).isEmpty())
        assertEquals(1, NappletSocksPolicy.connectable(listOf(loopback, public)).size)
        assertEquals(2, NappletSocksPolicy.connectable(listOf(loopback, public), allowLocal = true).size)
    }

    @Test
    fun ipLiteralsRoundTrip() {
        listOf("1.2.3.4", "::1", "2001:db8::1", "fe80::1:2", "2606:4700:4700::1111", "1:2:3:4:5:6:7:8", "::", "::ffff:c0a8:101").forEach {
            assertEquals(it, NappletIpLiterals.format(NappletIpLiterals.parse(it)!!), it)
        }
        assertContentEquals(NappletIpLiterals.parse("::ffff:192.168.1.1"), NappletIpLiterals.parse("::ffff:c0a8:101"))
        listOf("1.2.3", "1.2.3.4.5", "01234.1.1.1", "1::2::3", "12345::", "g::1", "fe80::1%en0").forEach { assertNull(NappletIpLiterals.parse(it), it) }
    }
}
