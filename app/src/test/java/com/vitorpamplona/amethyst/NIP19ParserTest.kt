package com.vitorpamplona.amethyst

import com.vitorpamplona.amethyst.service.model.ATag
import com.vitorpamplona.amethyst.service.nip19.Nip19
import org.junit.Assert.assertEquals
import org.junit.Test

class NIP19ParserTest {
    @Test
    fun nAddrParser() {
        val result = Nip19.uriToRoute("nostr:naddr1qqqqygzxpsj7dqha57pjk5k37gkn6g4nzakewtmqmnwryyhd3jfwlpgxtspsgqqqw4rs3xyxus")
        assertEquals("30023:460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c:", result?.hex)
    }

    @Test
    fun nAddrParser2() {
        val result = Nip19.uriToRoute("nostr:naddr1qq8kwatfv3jj6amfwfjkwatpwfjqygxsm6lelvfda7qlg0tud9pfhduysy4vrexj65azqtdk4tr75j6xdspsgqqqw4rsg32ag8")
        assertEquals("30023:d0debf9fb12def81f43d7c69429bb784812ac1e4d2d53a202db6aac7ea4b466c:guide-wireguard", result?.hex)
    }

    @Test
    fun nAddrParse3() {
        val result = Nip19.uriToRoute("naddr1qqyrswtyv5mnjv3sqy28wumn8ghj7un9d3shjtnyv9kh2uewd9hsygx3uczxts4hwue9ayfn7ggq62anzstde2qs749pm9tx2csuthhpjvpsgqqqw4rs8pmj38")
        assertEquals(Nip19.Type.ADDRESS, result?.type)
        assertEquals("30023:d1e60465c2b777325e9133f2100d2bb31416dca810f54a1d95665621c5dee193:89de7920", result?.hex)
        assertEquals("wss://relay.damus.io", result?.relay)
    }

    @Test
    fun nAddrATagParse3() {
        val address = ATag.parse("30023:d1e60465c2b777325e9133f2100d2bb31416dca810f54a1d95665621c5dee193:89de7920", "wss://relay.damus.io")
        assertEquals(30023, address?.kind)
        assertEquals("d1e60465c2b777325e9133f2100d2bb31416dca810f54a1d95665621c5dee193", address?.pubKeyHex)
        assertEquals("89de7920", address?.dTag)
        assertEquals("wss://relay.damus.io", address?.relay)
        assertEquals("naddr1qqyrswtyv5mnjv3sqy28wumn8ghj7un9d3shjtnyv9kh2uewd9hsygx3uczxts4hwue9ayfn7ggq62anzstde2qs749pm9tx2csuthhpjvpsgqqqw4rs8pmj38", address?.toNAddr())
    }

    @Test
    fun nAddrFormatter() {
        val address = ATag(30023, "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c", "", null)
        assertEquals("naddr1qqqqygzxpsj7dqha57pjk5k37gkn6g4nzakewtmqmnwryyhd3jfwlpgxtspsgqqqw4rs3xyxus", address.toNAddr())
    }

    @Test
    fun nAddrFormatter2() {
        val address = ATag(30023, "d0debf9fb12def81f43d7c69429bb784812ac1e4d2d53a202db6aac7ea4b466c", "guide-wireguard", null)
        assertEquals("naddr1qq8kwatfv3jj6amfwfjkwatpwfjqygxsm6lelvfda7qlg0tud9pfhduysy4vrexj65azqtdk4tr75j6xdspsgqqqw4rsg32ag8", address.toNAddr())
    }

    @Test
    fun nAddrFormatter3() {
        val address = ATag(30023, "d1e60465c2b777325e9133f2100d2bb31416dca810f54a1d95665621c5dee193", "89de7920", "wss://relay.damus.io")
        assertEquals("naddr1qqyrswtyv5mnjv3sqy28wumn8ghj7un9d3shjtnyv9kh2uewd9hsygx3uczxts4hwue9ayfn7ggq62anzstde2qs749pm9tx2csuthhpjvpsgqqqw4rs8pmj38", address.toNAddr())
    }
}
