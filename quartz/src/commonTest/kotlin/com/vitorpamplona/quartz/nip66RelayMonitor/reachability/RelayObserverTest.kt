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
package com.vitorpamplona.quartz.nip66RelayMonitor.reachability

import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.AuthMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NoticeMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CloseCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * These records get published under a monitor's own key, so what matters is what
 * the observer is willing to CLAIM: an unmeasured latency must never be reported
 * as a measurement, a relay nobody dialled must never be reported at all, and one
 * bad minute must not bury a relay that works.
 */
class RelayObserverTest {
    private val url = RelayUrlNormalizer.normalize("wss://relay.example")
    private val other = RelayUrlNormalizer.normalize("wss://other.example")

    private class FakeRelayClient(
        override val url: NormalizedRelayUrl,
    ) : IRelayClient {
        override fun connect() = Unit

        override fun needsToReconnect() = false

        override fun connectAndSyncFiltersIfDisconnected(ignoreRetryDelays: Boolean) = Unit

        override fun isConnected() = true

        override fun sendOrConnectAndSync(cmd: Command) = Unit

        override fun sendIfConnected(cmd: Command) = Unit

        override fun disconnect() = Unit
    }

    private fun client(u: NormalizedRelayUrl) = FakeRelayClient(u)

    private fun RelayObserver.only() = collectUnreported().single()

    // ---- what we measured ---------------------------------------------------

    @Test
    fun `an opened connection is timed, not assumed`() {
        val o = RelayObserver()
        o.onConnecting(client(url))
        o.onConnected(client(url), 1, true)

        val obs = o.only()
        assertTrue(obs.reachable)
        assertNotNull(obs.rttOpenMs, "rtt-open must be measured — aggregators rank on it")
        assertNull(obs.error)
    }

    @Test
    fun `the read clock runs from the first REQ to the first EOSE`() {
        val o = RelayObserver()
        o.onConnecting(client(url))
        o.onConnected(client(url), 1, true)
        o.onSent(client(url), "", ReqCmd("sub", emptyList()), true)
        o.onIncomingMessage(client(url), "", EoseMessage("sub"))

        assertNotNull(o.only().rttReadMs)
    }

    @Test
    fun `the write clock runs from the first EVENT to its OK`() {
        val o = RelayObserver()
        o.onConnecting(client(url))
        o.onConnected(client(url), 1, true)
        o.onIncomingMessage(client(url), "", OkMessage("id", true, ""))

        assertNull(o.collectUnreported().single().rttWriteMs, "an OK with nothing sent behind it times nothing")
    }

    @Test
    fun `a non-REQ command does not start the read clock`() {
        val o = RelayObserver()
        o.onConnecting(client(url))
        o.onConnected(client(url), 1, true)
        o.onSent(client(url), "", CloseCmd("sub"), true)
        o.onIncomingMessage(client(url), "", EoseMessage("sub"))

        assertNull(o.only().rttReadMs)
    }

    // ---- what we refuse to claim --------------------------------------------

    @Test
    fun `a connection that never opened records the reason and no latency`() {
        val o = RelayObserver()
        o.onConnecting(client(url))
        o.onCannotConnect(client(url), "Expected HTTP 101 response but was '503 Service Unavailable'")

        val obs = o.only()
        assertFalse(obs.reachable)
        assertNull(obs.rttOpenMs, "nothing opened, so there is nothing to time")
        assertTrue(obs.error!!.contains("503"))
    }

    @Test
    fun `a relay that answered stays answered through a later failure`() {
        // A relay that worked a minute ago and blipped now is not the same thing
        // as one that never answered, and only the writer decides which record
        // that becomes. A single failure must not erase the success under it.
        val o = RelayObserver()
        o.onConnecting(client(url))
        o.onConnected(client(url), 1, true)
        o.onCannotConnect(client(url), "connection reset")

        assertTrue(o.only().reachable, "one bad minute must not bury a relay that answered")
    }

    @Test
    fun `a reconnect clears the previous attempt's error`() {
        val o = RelayObserver()
        o.onConnecting(client(url))
        o.onCannotConnect(client(url), "timeout")
        o.onConnecting(client(url))

        assertNull(o.only().error, "a stale error would report a live relay as broken forever")
    }

    // ---- AUTH, which is why an anonymous crawl finds a relay empty ------------

    @Test
    fun `a demand for AUTH is recorded, from either shape`() {
        val challenged = RelayObserver()
        challenged.onIncomingMessage(client(url), "", AuthMessage("challenge"))
        assertTrue(challenged.only().authRequired)

        val closed = RelayObserver()
        closed.onIncomingMessage(client(url), "", ClosedMessage("sub", "auth-required: subscribers only"))
        val obs = closed.only()
        assertTrue(obs.authRequired)
        assertEquals("auth-required", obs.closedReason)
    }

    @Test
    fun `a CLOSED that is not about auth is categorised, not misread`() {
        val o = RelayObserver()
        o.onIncomingMessage(client(url), "", ClosedMessage("sub", "rate-limited: slow down"))

        val obs = o.only()
        assertEquals("rate-limited", obs.closedReason)
        assertFalse(obs.authRequired, "only an auth refusal means auth is required")
    }

    // ---- publishing bookkeeping ---------------------------------------------

    @Test
    fun `an unchanged relay is not re-reported, and its measurement survives`() {
        // Re-writing a record refreshes its freshness window, so a relay nobody
        // re-measured must be left out. But the measurement itself has to stay:
        // a long-lived socket fires onConnected once, and if publishing erased
        // it, the relays we know best would be the ones we could never describe
        // again.
        val o = RelayObserver()
        o.onConnecting(client(url))
        o.onConnected(client(url), 1, true)

        val first = o.collectUnreported().single()
        assertNotNull(first.rttOpenMs)
        assertEquals(0, o.collectUnreported().size, "nothing new to say")

        o.onIncomingMessage(client(url), "", NoticeMessage("slow down"))
        val second = o.collectUnreported().single()
        assertEquals(first.rttOpenMs, second.rttOpenMs, "the last real measurement still stands")
    }

    @Test
    fun `each relay is observed on its own`() {
        val o = RelayObserver()
        o.onConnecting(client(url))
        o.onConnected(client(url), 1, true)
        o.onConnecting(client(other))
        o.onCannotConnect(client(other), "nodename nor servname provided")

        val byUrl = o.collectUnreported().associateBy { it.url }
        assertTrue(byUrl.getValue(url).reachable)
        assertFalse(byUrl.getValue(other).reachable)
    }

    // ---- the run-level summary (what RelayDiagnostics used to give) -----------

    @Test
    fun `the summary tallies feedback across every relay`() {
        val o = RelayObserver()
        assertFalse(o.hadFeedback())

        o.onIncomingMessage(client(url), "", AuthMessage("c1"))
        o.onIncomingMessage(client(other), "", AuthMessage("c2"))
        o.onIncomingMessage(client(url), "", ClosedMessage("s", "rate-limited: slow"))
        o.onIncomingMessage(client(other), "", ClosedMessage("s", "rate-limited: slow"))
        o.onIncomingMessage(client(url), "", NoticeMessage("too many REQs"))

        assertTrue(o.hadFeedback())
        val s = o.summary()
        assertEquals(2L, s["auth_challenges"])
        assertEquals(2, s["auth_required_relays"])
        assertEquals(mapOf("rate-limited" to 2L), s["closed_by_reason"])
        assertEquals(1L, s["notices"])
    }

    @Test
    fun `the summary outlives publishing`() {
        // It answers "how did this run go", which must not be reset by the
        // unrelated act of writing records out.
        val o = RelayObserver()
        o.onIncomingMessage(client(url), "", AuthMessage("c"))
        o.collectUnreported()

        assertTrue(o.hadFeedback(), "a flush must not erase the run's tally")
        assertEquals(1L, o.summary()["auth_challenges"])
    }

    @Test
    fun `a machine-readable prefix is extracted, or 'other'`() {
        assertEquals("auth-required", RelayObserver.prefixOf("auth-required: come back signed"))
        assertEquals("other", RelayObserver.prefixOf("just some prose"))
        assertEquals("other", RelayObserver.prefixOf(""))
    }
}
