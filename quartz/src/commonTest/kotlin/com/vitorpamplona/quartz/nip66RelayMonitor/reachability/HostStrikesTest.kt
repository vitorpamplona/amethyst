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

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * An outbox list is five figures of urls and mostly corpses. These pin the two
 * rules that make the difference: failures count per HOST (or a filtering
 * relay's hundreds of per-user urls never add up to anything), and a host that
 * has ever delivered is never dropped (or a fan-out this wide sheds working
 * relays on a race).
 */
class HostStrikesTest {
    private fun url(u: String) = RelayUrlNormalizer.normalize(u)

    // ---- the authority key --------------------------------------------------

    @Test
    fun `per-user path urls on one host share an authority`() {
        val a = HostStrikes.authorityOf("wss://filter.nostr.wine/npub1aaaa?broadcast=true")
        val b = HostStrikes.authorityOf("wss://filter.nostr.wine/npub1bbbb")
        assertEquals("filter.nostr.wine", a)
        assertEquals(a, b)
        assertEquals(a, HostStrikes.authorityOf("wss://filter.nostr.wine"))
    }

    @Test
    fun `a subdomain is not folded into its parent`() {
        // Different servers. Shedding the filtering one must never take out the
        // bare host, which may be perfectly open.
        assertTrue(
            HostStrikes.authorityOf("wss://filter.nostr.wine/npub1x") !=
                HostStrikes.authorityOf("wss://nostr.wine"),
        )
    }

    @Test
    fun `the port is part of the authority`() {
        assertEquals("relay.example.com:443", HostStrikes.authorityOf("wss://relay.example.com:443/npub1z"))
        assertTrue(
            HostStrikes.authorityOf("wss://example.com:443") != HostStrikes.authorityOf("wss://example.com:8080"),
        )
    }

    // ---- striking -----------------------------------------------------------

    @Test
    fun `one host is struck out by failures spread across its many urls`() {
        // The whole point: no single url would ever reach the threshold alone.
        val h = HostStrikes()
        h.strike(url("wss://filter.example/npub1aaa"))
        h.strike(url("wss://filter.example/npub1bbb"))
        assertFalse(h.isDead(url("wss://filter.example/npub1ccc")), "two strikes is not yet a verdict")

        h.strike(url("wss://filter.example/npub1ccc"))
        assertTrue(h.isDead(url("wss://filter.example/npub1ddd")), "the host is out — including urls never tried")
    }

    @Test
    fun `eviction returns a verdict exactly once for publishing`() {
        // The eviction is the only finding that will ever exist about the sibling
        // urls under this host: from here they are skipped without being dialled,
        // so nothing observes them again. It must surface exactly once — silent
        // would publish nothing, repeated would rewrite the record every strike.
        val h = HostStrikes()
        assertNull(h.strike(url("wss://filter.example/npub1")), "one strike is not a verdict")
        assertNull(h.strike(url("wss://filter.example/npub2")), "two is not either")

        val evicted = h.strike(url("wss://filter.example/npub3"))
        assertNotNull(evicted, "the third strike is the finding")
        assertEquals("filter.example", evicted.authority)
        assertEquals(3, evicted.strikes)

        assertNull(h.strike(url("wss://filter.example/npub4")), "already evicted — do not report it again")
    }

    @Test
    fun `a host that has delivered is never evicted so nothing is published`() {
        val h = HostStrikes()
        h.produced(url("wss://busy.example/npubY"))
        repeat(5) { assertNull(h.strike(url("wss://busy.example/npub$it")), "ever-produced outranks any strike") }
    }

    @Test
    fun `striking one host leaves every other alone`() {
        val h = HostStrikes()
        repeat(5) { h.strike(url("wss://filter.example/npub$it")) }
        assertTrue(h.isDead(url("wss://filter.example/npub1")))
        assertFalse(h.isDead(url("wss://example.com")))
        assertFalse(h.isDead(url("wss://other.example")))
    }

    @Test
    fun `a host that ever delivered is never dead whichever way the race lands`() {
        // At a hundred relays in flight one worker can strike an authority out at
        // the same instant another is receiving from it. Ever-produced must win in
        // both orders, which is why it overrides rather than clearing strikes.
        val strikeFirst = HostStrikes()
        repeat(3) { strikeFirst.strike(url("wss://busy.example/npub$it")) }
        assertTrue(strikeFirst.isDead(url("wss://busy.example/npubX")))
        strikeFirst.produced(url("wss://busy.example/npubY"))
        assertFalse(strikeFirst.isDead(url("wss://busy.example/npubX")), "a delivery revives the whole host")

        val produceFirst = HostStrikes()
        produceFirst.produced(url("wss://busy.example/npubY"))
        repeat(5) { produceFirst.strike(url("wss://busy.example/npub$it")) }
        assertFalse(produceFirst.isDead(url("wss://busy.example/npubX")), "later strikes cannot bury it")
    }

    @Test
    fun `a zero strike limit disables eviction entirely`() {
        val h = HostStrikes(strikeLimit = 0)
        repeat(50) { h.strike(url("wss://filter.example/npub$it")) }
        assertFalse(h.isDead(url("wss://filter.example/npub1")))
    }

    // ---- what a previous run already learned ---------------------------------

    @Test
    fun `a relay a previous run proved dead is skipped without dialling`() {
        val gone = url("wss://gone.example")
        val h = HostStrikes(knownDead = setOf(gone))
        assertTrue(h.isDead(gone))
        assertFalse(h.isDead(url("wss://alive.example")))
    }

    @Test
    fun `a known-dead relay that answers anyway is believed over the record`() {
        // Relays come back. A TTL'd record is "not now" and never "never again":
        // a delivery this cycle must beat what an earlier one wrote down.
        val back = url("wss://back.example")
        val h = HostStrikes(knownDead = setOf(back))
        h.produced(back)
        assertFalse(h.isDead(back))
    }

    // ---- what gets written back ---------------------------------------------

    @Test
    fun `only relays actually dialled are reported and delivery clears a failure`() {
        val h = HostStrikes()
        val good = url("wss://good.example")
        val bad = url("wss://bad.example")
        h.strike(bad)
        h.strike(good)
        h.produced(good)

        assertEquals(setOf(good), h.reachable)
        assertEquals(setOf(bad), h.unreachable, "a relay that later delivered is not reported dead")
    }
}
