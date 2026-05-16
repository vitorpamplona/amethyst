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
package com.vitorpamplona.amethyst.service.okhttp

import okhttp3.Dns
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.UnknownHostException

class SurgeDnsStoreTest {
    @get:Rule val tempFolder = TemporaryFolder()

    private fun ip(value: String): ByteArray = InetAddress.getByName(value).address

    /** Drives [SurgeDnsStore] without going through a real upstream resolver. */
    private class StubDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> = throw UnknownHostException(hostname)
    }

    private fun newStore(file: File): Pair<SurgeDnsStore, SurgeDns> {
        val dns = SurgeDns(delegate = StubDns(), positiveTtlMs = 60_000, positiveTtlJitterMs = 0)
        return SurgeDnsStore(file, dns) to dns
    }

    @Test
    fun `round trips empty list`() {
        val file = File(tempFolder.root, "cache.bin")
        val (store, dns) = newStore(file)

        // Force a save with no entries by marking dirty then snapshotting empty.
        dns.markDirty()
        store.save()
        assertTrue("empty save should still produce a file", file.exists())

        val (store2, dns2) = newStore(file)
        store2.load()
        assertTrue("loading empty blob should not populate cache", dns2.snapshot().isEmpty())
    }

    @Test
    fun `round trips mixed ipv4 and ipv6 with multiple ips per host`() {
        val file = File(tempFolder.root, "cache.bin")
        val (store, dns) = newStore(file)

        val expires = System.currentTimeMillis() + 60_000
        dns.restore(
            listOf(
                DnsCacheRecord("relay.example", listOf(ip("1.2.3.4"), ip("5.6.7.8")), expires),
                DnsCacheRecord("v6.example", listOf(ip("2001:db8::1")), expires + 1),
                DnsCacheRecord(
                    "dual.example",
                    listOf(ip("9.9.9.9"), ip("2001:db8::dead:beef"), ip("10.0.0.1")),
                    expires + 2,
                ),
            ),
        )
        dns.markDirty()
        store.save()

        val (store2, dns2) = newStore(file)
        store2.load()

        val restored = dns2.snapshot().sortedBy { it.hostname }
        assertEquals(3, restored.size)

        val dual = restored.single { it.hostname == "dual.example" }
        assertEquals(3, dual.addresses.size)
        assertArrayEquals(ip("9.9.9.9"), dual.addresses[0])
        assertArrayEquals(ip("2001:db8::dead:beef"), dual.addresses[1])
        assertArrayEquals(ip("10.0.0.1"), dual.addresses[2])
        assertEquals(expires + 2, dual.expiresAtMillis)

        val v6 = restored.single { it.hostname == "v6.example" }
        assertArrayEquals(ip("2001:db8::1"), v6.addresses.single())

        val relay = restored.single { it.hostname == "relay.example" }
        assertArrayEquals(ip("1.2.3.4"), relay.addresses[0])
        assertArrayEquals(ip("5.6.7.8"), relay.addresses[1])
    }

    @Test
    fun `load deletes file with bad magic`() {
        val file = File(tempFolder.root, "cache.bin")
        DataOutputStream(FileOutputStream(file)).use { out ->
            out.writeInt(0xDEADBEEF.toInt())
            out.writeShort(1)
            out.writeInt(0)
        }
        val (store, dns) = newStore(file)
        store.load()
        assertFalse("corrupt blob should be deleted", file.exists())
        assertTrue(dns.snapshot().isEmpty())
    }

    @Test
    fun `load deletes truncated file`() {
        val file = File(tempFolder.root, "cache.bin")
        // Write a header claiming one record, then EOF before the record.
        DataOutputStream(FileOutputStream(file)).use { out ->
            out.writeInt(0x534E5343) // 'SNSC'
            out.writeShort(1)
            out.writeInt(1)
            // No record body — readFully will throw EOFException.
        }
        val (store, _) = newStore(file)
        store.load()
        assertFalse(file.exists())
    }

    @Test
    fun `load rejects oversized record count`() {
        val file = File(tempFolder.root, "cache.bin")
        DataOutputStream(FileOutputStream(file)).use { out ->
            out.writeInt(0x534E5343)
            out.writeShort(1)
            // Way over the 100_000 cap.
            out.writeInt(50_000_000)
        }
        val (store, _) = newStore(file)
        store.load()
        assertFalse("oversized record count should be treated as corruption", file.exists())
    }

    @Test
    fun `load tolerates missing file`() {
        val file = File(tempFolder.root, "absent.bin")
        val (store, dns) = newStore(file)
        store.load() // must not throw
        assertTrue(dns.snapshot().isEmpty())
    }

    @Test
    fun `construction deletes the legacy json sibling`() {
        val legacy = File(tempFolder.root, "dns_cache_v1.json")
        legacy.writeText("[]")
        assertTrue(legacy.exists())

        val target = File(tempFolder.root, "dns_cache_v1.bin")
        SurgeDnsStore(target, SurgeDns(delegate = StubDns()))

        assertFalse("legacy json blob should be reclaimed", legacy.exists())
    }
}
