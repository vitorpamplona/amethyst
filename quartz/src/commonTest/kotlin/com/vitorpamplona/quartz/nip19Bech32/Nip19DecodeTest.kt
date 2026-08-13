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
package com.vitorpamplona.quartz.nip19Bech32

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip19Bech32.bech32.Bech32
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEmbed
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NRelay
import com.vitorpamplona.quartz.nip19Bech32.tlv.TlvBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Coverage for the TLV-backed entities reached *through the content scan*, and for
 * [Nip19Parser.tryParseAndClean].
 *
 * `NIP19ParserTest` decodes these through `uriToRoute`. This asserts the same
 * entities survive being found inside arbitrary text, which is the path every
 * ingested note takes, and pins the relay hints and identifiers they carry.
 *
 * `nembed1` had no `commonTest` coverage at all (its only fixtures live in
 * `androidDeviceTest`/`iosTest`), and `nrelay1` had no *positive* case anywhere —
 * the codebase can parse it but has no encoder for it, so one is built here from
 * TLV directly.
 */
class Nip19DecodeTest {
    companion object {
        const val PUBKEY = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
        const val NADDR =
            "naddr1qqyxzmt9w358jum5qyt8wumn8ghj7un9d3shjtnwdaehgu3wvfskueqzypd7v3r24z33cydnk3fmlrd0exe5dlej3506zxs05q4puerp765mzqcyqqq8scsq6mk7u"
        const val NEMBED = "nembed1r79ssq9446hkwqhl642ukmku8qg0c92pu7w3j0jyfte8tc7tvg85vmrys8x3sqgle5vjy7jpjswqhphl0kd6yf4sz0n3peyjq5rp3zkat4w6c6j3f7um0724jmfu5456xxgg2yxkn8dp23j64xsn9npcggzafyh2effyntqrqxzja8dp52kpcvc9zqxlj86e8mx05vevzxkeprjkfs4wmppxm3p96vj6yvu2mqgf5l4v99492r2qsggquxuv93uzx244652h2kkj8xseg9xkq0afpygknjtty9j4ju5v0nm9mezux9wyl6s5wr7lzce7cj397mnu0u04ha7aq3w7exelrhe3zs3l3urwa9sp36u80npllrs0hmsxqdn0fsuyav3nv0azjs5suzuurg2uymncjxez8p9xksc2j6gw992enjflgrdd7n5uq2xrpvfrd3rckw624ey0elvm6grr27tyzlf4vaswgm5vc3hdyczsl983g2j8e67r6z5zt30lat84ma4wclkwwxxrcflvdsuwd7346h7zqav4vdwe3gkt9lr87sfk4aqd2aey03tt4eyspldrqcmkx9pqe2pn63rv7grwwalr86akuldnvjm6m87wrw9sdwns8wq0rnsmj57vqwtc3g7hkwum3vl2dda78dwkycgfzw6qna3ufhpatcvq5a4hm4ehl45an8umwt0clf7rn77ctke475qglwu86hhfwhn7dkca4pkfpyc4y75rll6nvr5qc8nlhf8mk22celn5mecvyuzxd830drhdck9tcdpcafymk8wajwu2w8ha8gatggjfvq0a4jlf2sdamzj0ysqks9dk8me3q7a0qpmf6vykurkrcls4pug3u4pn4u26ezx3h8e482n07x2nsmu80dpufxqc0ttcyzhnppguxma4d8aumdawnlsyy7yzcuxl7lw5y9p4nv5h8fn6u8anpm2tsze3p6mgxy9j9uuqfxg2jvlmtjpakna5m4hln0msmw804hnun96h66fh62270yhhljnmmdl7jln07ll5vft7e870hemcld34a09n943ed6629fgtctsftma9q6tf4jfm2p0ukd2j2n2dpz53fqrkk4ctdcy2j5jar095g5jntf6u807ggkzauzt6uqkwk4tg5w7w55kskspc9663zx5dzzzfwpg3q546g2ve4kukr70n0a46eyce2crsqqq247ql5"
        const val NCRYPTSEC = "ncryptsec1qgg9947rlpvqu76pj5ecreduf9jxhselq2nae2kghhvd5g7dgjtcxfqtd67p9m0w57lspw8gsq6yphnm8623nsl8xn9j4jdzz84zm3frztj3z7s35vpzmqf6ksu8r89qk5z2zxfmu5gv8th8wclt0h4p"
    }

    // ---------- nprofile: pubkey + relay hints ----------

    @Test
    fun nprofileWithRelayHintsSurvivesTheContentScan() {
        val relay = RelayUrlNormalizer.normalize("wss://vitor.nostr1.com/")
        val encoded = NProfile.create(PUBKEY, listOf(relay))

        val found = Nip19Parser.parseAll("please follow nostr:$encoded today")
        assertEquals(1, found.size)
        val profile = found[0] as NProfile
        assertEquals(PUBKEY, profile.hex)
        assertEquals(listOf(relay), profile.relay)
    }

    @Test
    fun nprofileWithoutRelayHintsStillDecodes() {
        val encoded = NProfile.create(PUBKEY, emptyList())
        val found = Nip19Parser.parseAll(encoded)
        assertEquals(1, found.size)
        assertEquals(PUBKEY, (found[0] as NProfile).hex)
        assertTrue((found[0] as NProfile).relay.isEmpty())
    }

    @Test
    fun nprofileWithSeveralRelayHintsKeepsAllOfThem() {
        val a = RelayUrlNormalizer.normalize("wss://relay.one/")
        val b = RelayUrlNormalizer.normalize("wss://relay.two/")
        val encoded = NProfile.create(PUBKEY, listOf(a, b))
        val profile = Nip19Parser.parseAll("x $encoded").single() as NProfile
        assertEquals(listOf(a, b), profile.relay)
    }

    // ---------- naddr: kind:author:dTag ----------

    @Test
    fun naddrSurvivesTheContentScan() {
        val found = Nip19Parser.parseAll("read nostr:$NADDR now")
        assertEquals(1, found.size)
        assertEquals(
            "30818:5be6446aa8a31c11b3b453bf8dafc9b346ff328d1fa11a0fa02a1e6461f6a9b1:amethyst",
            (found[0] as NAddress).aTag(),
        )
    }

    // ---------- nrelay: no encoder in the codebase, so build one from TLV ----------

    @Test
    fun nrelayDecodesFromTheContentScan() {
        val url = "wss://relay.example.com/"
        val encoded =
            TlvBuilder()
                .apply { addString(TlvTypes.SPECIAL.id, url) }
                .build()
                .let { Bech32.encodeBytes(hrp = "nrelay", it, Bech32.Encoding.Bech32) }

        val found = Nip19Parser.parseAll("join nostr:$encoded please")
        assertEquals(1, found.size)
        assertEquals(listOf(url), (found[0] as NRelay).relay)
    }

    // ---------- nembed: gzipped event, first commonTest coverage ----------

    @Test
    fun nembedDecodesFromTheContentScan() {
        // fixture mirrored from NIP19EmbedTests (androidDeviceTest/iosTest), which
        // never ran on JVM — the decode goes through GZip + Event parsing
        val found = Nip19Parser.parseAll("look at nostr:$NEMBED")
        assertEquals(1, found.size)
        val embed = found[0] as NEmbed
        assertNotNull(embed.event)
    }

    @Test
    fun truncatedNembedIsRejectedNotThrown() {
        val broken = NEMBED.take(NEMBED.length / 2)
        assertEquals(emptyList(), Nip19Parser.parseAll(broken))
    }

    // ---------- tryParseAndClean ----------

    @Test
    fun tryParseAndCleanStripsSchemeAndTrailingCharacters() {
        val npub = Nip19ScanTest.NPUB
        assertEquals(npub, Nip19Parser.tryParseAndClean("nostr:$npub"))
        assertEquals(npub, Nip19Parser.tryParseAndClean(npub))
        assertEquals(npub, Nip19Parser.tryParseAndClean("@$npub"))
    }

    @Test
    fun tryParseAndCleanReturnsNullForNonEntities() {
        assertEquals(null, Nip19Parser.tryParseAndClean(null))
        assertEquals(null, Nip19Parser.tryParseAndClean(""))
        assertEquals(null, Nip19Parser.tryParseAndClean("just some words"))
        assertEquals(null, Nip19Parser.tryParseAndClean("npub1tooshort"))
    }

    @Test
    fun tryParseAndCleanNeverReturnsALiteralNullSuffix() {
        // `type!! + key` would render "npub1null" if the key group were ever absent
        listOf("nostr:${Nip19ScanTest.NPUB}", Nip19ScanTest.NOTE, "nostr:${Nip19ScanTest.NEVENT}")
            .forEach { assertTrue(Nip19Parser.tryParseAndClean(it)?.endsWith("null") != true, "for $it") }
    }

    @Test
    fun tryParseAndCleanAcceptsNcryptsecWhichTheContentScanDoesNot() {
        // nip19PlusNip46regex includes ncryptsec1; nip19regex (used by parseAll) does not
        val ncryptsec = NCRYPTSEC
        assertEquals(ncryptsec, Nip19Parser.tryParseAndClean(ncryptsec))
        assertEquals(emptyList(), Nip19Parser.parseAll(ncryptsec))
    }
}
