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
package com.vitorpamplona.quartz.contextvm.cep06Announcements

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CEP-6 announcements captured off the public relays on 2026-09-22, from
 * coordinators that serve the eleven cordn tools.
 *
 * These are real, signature-valid events, so they pin the parser to what other
 * implementations actually emit rather than to what we would emit ourselves.
 */
class ServerAnnouncementLiveVectorTest {
    /** A named, hosted coordinator — the only one on the network carrying a website. */
    private val dojopop =
        """{"id":"1a65d58ffbaae66cd14d15e275d57e08dea50bbd06ac4a606f2a7312d1cb29f8","pubkey":"d969813a5c0e3e65dad03fc9e1d2db5933dda8b307ddce474e8da60b4e288259","created_at":1786946186,"kind":11316,"tags":[["name","dojopop-cordn"],["about","DojoPop MLS group messaging coordinator"],["website","https://dojopop.live"],["support_encryption"],["support_encryption_ephemeral"],["support_oversized_transfer"],["support_open_stream"]],"content":"{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{\"tools\":{\"listChanged\":true}},\"serverInfo\":{\"name\":\"cordn-server\",\"version\":\"0.1.0\"}}","sig":"33ad89ba030b935bbcd08e98ed7b952dfe3d072e165ab96b9f98635cb81a2987e66d7d75a391aa904f824e9dab090b3edd16f0002f9522f5dfb9a3fbc15db88e"}"""

    /** A browser-tab coordinator — same shape, no website tag. */
    private val browserTab =
        """{"id":"acead902457220f407bcb7857020c034089d0c35e4c29d18066ac17fab055aa7","pubkey":"35e2a4020dd6b9f5f45cf27d3e65f4a286a8e89d75c8ae141d9a794c1236dc26","created_at":1786113384,"kind":11316,"tags":[["name","My coordinator"],["about","Cordn coordinator running in a browser tab; key package quota 33 per identity"],["support_encryption"],["support_encryption_ephemeral"],["support_oversized_transfer"],["support_open_stream"]],"content":"{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{\"tools\":{\"listChanged\":true}},\"serverInfo\":{\"name\":\"My coordinator\",\"version\":\"0.1.0\"}}","sig":"a0f29d13955636d407635eaa59ee307fa9b39d54466189e247f0ff9c61c17070084d0bd604efd29287a19c4e841a21c7c69dd1a500b245c98561dacc09b550a7"}"""

    private fun parse(json: String): ServerAnnouncement {
        val event: Event = JacksonMapper.fromJson(json)
        assertTrue("captured vector must still verify", event.verify())
        return ServerAnnouncement.parseOrNull(event)!!
    }

    @Test
    fun readsANamedCoordinatorAnnouncement() {
        val ann = parse(dojopop)

        assertEquals(11316, ann.kind)
        assertEquals("d969813a5c0e3e65dad03fc9e1d2db5933dda8b307ddce474e8da60b4e288259", ann.pubKey)
        assertEquals("dojopop-cordn", ann.discovery.name)
        assertEquals("DojoPop MLS group messaging coordinator", ann.discovery.about)
        assertEquals("https://dojopop.live", ann.discovery.website)
        assertNull(ann.discovery.picture)
        assertTrue(ann.discovery.supportsEncryption)
        assertTrue(ann.discovery.supportsEphemeralEncryption)
        assertTrue(ann.discovery.supportsOversizedTransfer)
        assertTrue(ann.discovery.supportsOpenStream)
    }

    @Test
    fun readsACoordinatorThatOmitsTheOptionalTags() {
        val ann = parse(browserTab)

        assertEquals("My coordinator", ann.discovery.name)
        assertNull(ann.discovery.website)
        assertNull(ann.discovery.picture)
        assertTrue(ann.discovery.supportsEncryption)
    }

    /**
     * Nothing in a CEP-6 announcement says which relays reach the coordinator.
     *
     * A client that learns a coordinator this way only has its pubkey, so it has
     * to keep talking on the relay it heard the announcement on. Asserted so the
     * day a routing tag appears in the wild, this test is what flags it.
     */
    @Test
    fun carriesNoRelayHint() {
        listOf(dojopop, browserTab).forEach {
            assertEquals(emptyList<Any>(), parse(it).discovery.unknownTags)
        }
    }

    /** Replaceable kinds: a lagging relay's older copy must not win. */
    @Test
    fun keepsTheNewestAnnouncementPerKind() {
        val events = listOf(JacksonMapper.fromJson(browserTab), JacksonMapper.fromJson(dojopop))

        val latest = ServerAnnouncement.latestPerKind(events)

        assertEquals(1, latest.size)
        assertEquals("dojopop-cordn", latest[11316]!!.discovery.name)
    }
}
