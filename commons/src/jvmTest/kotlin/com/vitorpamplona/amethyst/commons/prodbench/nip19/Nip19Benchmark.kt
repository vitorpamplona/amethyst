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
package com.vitorpamplona.amethyst.commons.prodbench.nip19

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.bech32.Bech32
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.entities.NSec
import com.vitorpamplona.quartz.nip19Bech32.tlv.TlvBuilder
import com.vitorpamplona.quartz.nip19Bech32.toNsec
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import java.lang.management.ManagementFactory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Encode/decode cost of every NIP-19 entity, quartz 2.1.0 vs the [FastNip19] prototype.
 *
 * Encoding is what the UI pays: `Note.toNostrUri()` (an `nevent`/`naddr`) is the `callbackUri`
 * of almost every rendered note, and `User.pubkeyNpub()` / `NPub.create` back every short
 * display name. Decoding is what ingest pays: `Nip19Parser.parseAll` runs `parseComponents` on
 * every `nostr:` reference of every incoming note.
 *
 * Deterministic and offline. Prints ns/op and bytes allocated/op; the assertions only check that
 * both codecs produce the same strings and entities, never wall time (CI machines vary).
 */
class Nip19Benchmark {
    companion object {
        const val N = 1024
        val rnd = Random(42)

        fun hex32() = rnd.nextBytes(32).toHexKey()

        val relays =
            listOf(
                "wss://relay.damus.io/",
                "wss://nos.lol/",
                "wss://relay.primal.net/",
                "wss://nostr.wine/",
                "wss://relay.nostr.band/",
                "wss://nostr-pub.wellorder.net/",
            ).map { RelayUrlNormalizer.normalizeOrNull(it)!! }

        val ids = List(N) { hex32() }
        val authors = List(N) { hex32() }
        val kinds = List(N) { listOf(1, 1, 1, 6, 7, 20, 1111, 9802)[it % 8] }
        val relayHints: List<NormalizedRelayUrl?> = List(N) { if (it % 5 == 0) null else relays[it % relays.size] }
        val relayLists = List(N) { i -> List(i % 4) { relays[(i + it) % relays.size] } }
        val dTags = List(N) { "my-long-form-article-${it * 7919}" }

        private val threadBean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

        data class Row(
            val label: String,
            val ns: Double,
            val bytes: Double,
        )

        /** Best-of-5 rounds of [reps] calls over the N inputs; also reports bytes allocated/op. */
        fun bench(
            label: String,
            reps: Int = 40,
            op: (Int) -> Any?,
        ): Row {
            var sink = 0
            repeat(reps / 2) { r -> for (i in 0 until N) sink += op(i).hashCode() } // warmup
            var best = Double.MAX_VALUE
            var bestBytes = 0.0
            repeat(5) {
                val tid = Thread.currentThread().id
                val a0 = threadBean.getThreadAllocatedBytes(tid)
                val t0 = System.nanoTime()
                repeat(reps) { for (i in 0 until N) sink += op(i).hashCode() }
                val ns = (System.nanoTime() - t0).toDouble() / (reps.toLong() * N)
                val bytes = (threadBean.getThreadAllocatedBytes(tid) - a0).toDouble() / (reps.toLong() * N)
                if (ns < best) {
                    best = ns
                    bestBytes = bytes
                }
            }
            if (sink == 42) println() // keep the results alive
            return Row(label, best, bestBytes)
        }

        fun compare(
            label: String,
            reps: Int = 40,
            quartz: (Int) -> Any?,
            fast: (Int) -> Any?,
        ) {
            val q = bench(label, reps, quartz)
            val f = bench(label, reps, fast)
            println(
                String.format(
                    "%-28s quartz %8.0f ns %7.0f B | fast %7.0f ns %6.0f B | %5.1fx faster %5.1fx less alloc",
                    label,
                    q.ns,
                    q.bytes,
                    f.ns,
                    f.bytes,
                    q.ns / f.ns,
                    q.bytes / f.bytes,
                ),
            )
        }

        fun body(nip19: String) = nip19.substring(nip19.indexOf('1') + 1)
    }

    // Quartz's own encoders, as the app calls them.
    private fun qNpub(i: Int) = NPub.create(authors[i])

    private fun qNsec(i: Int) = ids[i].hexToByteArray().toNsec()

    private fun qNote(i: Int) = NNote.create(ids[i])

    private fun qNEvent(i: Int) = NEvent.create(ids[i], authors[i], kinds[i], relayHints[i])

    private fun qNProfile(i: Int) = NProfile.create(authors[i], relayLists[i])

    private fun qNAddr(i: Int) = NAddress.create(30023, authors[i], dTags[i], relayHints[i])

    private fun qNRelay(i: Int) = TlvBuilder().apply { addString(0, relays[i % relays.size].url) }.build().let { Bech32.encodeBytes("nrelay", it, Bech32.Encoding.Bech32) }

    @Test
    fun outputsMatchQuartz() {
        for (i in 0 until N) {
            assertEquals(qNpub(i), FastNip19.npub(authors[i]))
            assertEquals(qNsec(i), FastNip19.nsec(ids[i]))
            assertEquals(qNote(i), FastNip19.note(ids[i]))
            assertEquals(qNEvent(i), FastNip19.nevent(ids[i], authors[i], kinds[i], relayHints[i]))
            assertEquals(NEvent.create(ids[i], null, null, null as NormalizedRelayUrl?), FastNip19.nevent(ids[i], null, null, null))
            assertEquals(qNProfile(i), FastNip19.nprofile(authors[i], relayLists[i]))
            assertEquals(qNAddr(i), FastNip19.naddr(30023, authors[i], dTags[i], relayHints[i]))
            assertEquals("nostr:" + qNEvent(i), FastNip19.nevent(ids[i], authors[i], kinds[i], relayHints[i], prefix = "nostr:"))

            // decoding: same bytes, same entities
            for (s in listOf(qNpub(i), qNote(i), qNEvent(i), qNProfile(i), qNAddr(i), qNRelay(i))) {
                assertContentEquals(Bech32.decodeBytes(s).second, FastBech32.decode(s))
                assertContentEquals(Bech32.decodeBytes(s.uppercase()).second, FastBech32.decode(s.uppercase()))
            }
            assertEquals(Nip19Parser.uriToRoute(qNEvent(i))?.entity, FastNip19.parseNEvent(FastBech32.decode(qNEvent(i), "nevent")!!))
            assertEquals(Nip19Parser.uriToRoute(qNProfile(i))?.entity, FastNip19.parseNProfile(FastBech32.decode(qNProfile(i), "nprofile")!!))
            assertEquals(Nip19Parser.uriToRoute(qNAddr(i))?.entity, FastNip19.parseNAddress(FastBech32.decode(qNAddr(i), "naddr")!!))
        }

        // non-ASCII dTag goes through the UTF-8 path
        val emoji = "café-😀"
        assertEquals(NAddress.create(30023, authors[0], emoji, relays[0]), FastNip19.naddr(30023, authors[0], emoji, relays[0]))

        // a corrupted checksum is rejected by both
        val bad = qNpub(0).dropLast(1) + (if (qNpub(0).last() == 'q') 'p' else 'q')
        assertEquals(null, runCatching { Bech32.decodeBytes(bad) }.getOrNull())
        assertEquals(null, FastBech32.decode(bad))
    }

    @Test
    fun encoders() {
        println("NIP-19 encode (per call; inputs vary over $N ids/keys/relays)")
        compare("npub (NPub.create)", quartz = ::qNpub, fast = { FastNip19.npub(authors[it]) })
        compare("nsec", quartz = ::qNsec, fast = { FastNip19.nsec(ids[it]) })
        compare("note (NNote.create)", quartz = ::qNote, fast = { FastNip19.note(ids[it]) })
        compare("nevent id+author+kind+relay", quartz = ::qNEvent, fast = { FastNip19.nevent(ids[it], authors[it], kinds[it], relayHints[it]) })
        compare("nevent id only", quartz = { NEvent.create(ids[it], null, null, null as NormalizedRelayUrl?) }, fast = { FastNip19.nevent(ids[it], null, null, null) })
        compare("nprofile 0-3 relays", quartz = ::qNProfile, fast = { FastNip19.nprofile(authors[it], relayLists[it]) })
        compare("naddr kind+author+d+relay", quartz = ::qNAddr, fast = { FastNip19.naddr(30023, authors[it], dTags[it], relayHints[it]) })
        compare("nrelay", quartz = ::qNRelay, fast = {
            val url = relays[it % relays.size].url
            val w = FastTlvWriter(2 + url.length)
            w.string(0, url, url.length)
            FastBech32.encode("nrelay", w.buf)
        })

        println("NIP-21 uri (\"nostr:\" + entity, what Note/User.toNostrUri() build)")
        compare("nostr:nevent", quartz = { "nostr:${qNEvent(it)}" }, fast = { FastNip19.nevent(ids[it], authors[it], kinds[it], relayHints[it], prefix = "nostr:") })
        compare("nostr:nprofile", quartz = { "nostr:${qNProfile(it)}" }, fast = { FastNip19.nprofile(authors[it], relayLists[it], prefix = "nostr:") })
        compare("nostr:naddr", quartz = { "nostr:${qNAddr(it)}" }, fast = { FastNip19.naddr(30023, authors[it], dTags[it], relayHints[it], prefix = "nostr:") })
    }

    @Test
    fun decoders() {
        val npubs = List(N) { qNpub(it) }
        val nsecs = List(N) { qNsec(it) }
        val notes = List(N) { qNote(it) }
        val nevents = List(N) { qNEvent(it) }
        val nprofiles = List(N) { qNProfile(it) }
        val naddrs = List(N) { qNAddr(it) }
        val nrelays = List(N) { qNRelay(it) }

        println("NIP-19 decode, raw bech32 -> bytes (Bech32.decodeBytes vs FastBech32.decode)")
        compare("npub", quartz = { Bech32.decodeBytes(npubs[it]).second }, fast = { FastBech32.decode(npubs[it]) })
        compare("nevent", quartz = { Bech32.decodeBytes(nevents[it]).second }, fast = { FastBech32.decode(nevents[it]) })
        compare("naddr", quartz = { Bech32.decodeBytes(naddrs[it]).second }, fast = { FastBech32.decode(naddrs[it]) })

        println("NIP-19 decode to entity (Nip19Parser.parseComponents, what parseAll runs per reference)")
        compare("npub", quartz = { Nip19Parser.parseComponents("npub1", body(npubs[it]), null) }, fast = {
            FastBech32.decode(npubs[it], "npub")?.let { b -> if (b.size == 32) NPub(FastBech32.hex(b, 0, 32)) else null }
        })
        compare("nsec", quartz = { Nip19Parser.parseComponents("nsec1", body(nsecs[it]), null) }, fast = {
            FastBech32.decode(nsecs[it], "nsec")?.let { b -> NSec(FastBech32.hex(b, 0, b.size)) }
        })
        compare("note", quartz = { Nip19Parser.parseComponents("note1", body(notes[it]), null) }, fast = {
            FastBech32.decode(notes[it], "note")?.let { b -> NNote(FastBech32.hex(b, 0, b.size)) }
        })
        compare("nevent", quartz = { Nip19Parser.parseComponents("nevent1", body(nevents[it]), null) }, fast = {
            FastBech32.decode(nevents[it], "nevent")?.let { FastNip19.parseNEvent(it) }
        })
        compare("nprofile", quartz = { Nip19Parser.parseComponents("nprofile1", body(nprofiles[it]), null) }, fast = {
            FastBech32.decode(nprofiles[it], "nprofile")?.let { FastNip19.parseNProfile(it) }
        })
        compare("naddr", quartz = { Nip19Parser.parseComponents("naddr1", body(naddrs[it]), null) }, fast = {
            FastBech32.decode(naddrs[it], "naddr")?.let { FastNip19.parseNAddress(it) }
        })
        val r = bench("nrelay (no fast parser)") { Nip19Parser.parseComponents("nrelay1", body(nrelays[it]), null) }
        println(String.format("%-28s quartz %8.0f ns %7.0f B", r.label, r.ns, r.bytes))

        println("Nip19Parser.uriToRoute (regex find + parseComponents) for reference")
        val q = bench("uriToRoute nevent") { Nip19Parser.uriToRoute("nostr:" + nevents[it]) }
        println(String.format("%-28s quartz %8.0f ns %7.0f B", q.label, q.ns, q.bytes))
        val q2 = bench("uriToRoute npub") { Nip19Parser.uriToRoute("nostr:" + npubs[it]) }
        println(String.format("%-28s quartz %8.0f ns %7.0f B", q2.label, q2.ns, q2.bytes))
    }

    /**
     * What the UI actually calls: `Note.toNostrUri()` and `User.toNostrUri()`, with an author whose
     * kind:10002 lists 8 relays (the common shape) and a note seen on 3 relays. Split into the
     * relay-hint lookup and the encode, to show which half the time goes to.
     */
    @Test
    fun appToNostrUri() {
        val users =
            List(N) { i ->
                User(authors[i]) { addr -> AddressableNote(addr) }.also { u ->
                    val tags = Array(8) { r -> arrayOf("r", relays[(i + r) % relays.size].url + if (r >= relays.size) "x" else "") }
                    u.nip65RelayListNote.event = AdvertisedRelayListEvent(hex32(), authors[i], 1L, tags, "", "")
                }
            }
        val notes =
            List(N) { i ->
                Note(ids[i]).also { n ->
                    n.event = TextNoteEvent(ids[i], authors[i], 1L, emptyArray(), "hello", "")
                    n.author = users[i]
                    repeat(3) { r -> n.addRelaySync(relays[(i + r * 2) % relays.size]) }
                }
            }

        println("App call sites (commons model)")
        val all = bench("Note.toNostrUri()") { notes[it].toNostrUri() }
        val hint = bench("  Note.relayHintUrl()") { notes[it].relayHintUrl() }
        val enc = bench("  NEvent.create (hint given)") { NEvent.create(ids[it], authors[it], 1, relays[it % relays.size]) }
        val fast = bench("  FastNip19 nostr:nevent") { FastNip19.nevent(ids[it], authors[it], 1, relays[it % relays.size], prefix = "nostr:") }
        val uAll = bench("User.toNostrUri()") { users[it].toNostrUri() }
        val uHint = bench("  User.relayHints()") { users[it].relayHints() }
        val uNpub = bench("User.pubkeyNpub()") { users[it].pubkeyNpub() }
        val uShort = bench("User.pubkeyDisplayHex()") { users[it].pubkeyDisplayHex() }
        for (r in listOf(all, hint, enc, fast, uAll, uHint, uNpub, uShort)) {
            println(String.format("%-28s        %8.0f ns %7.0f B", r.label, r.ns, r.bytes))
        }
    }
}
