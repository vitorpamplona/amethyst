package com.vitorpamplona.quartz.encoders

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
        val address = ATag(
            30023,
            "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c",
            "",
            null
        )
        assertEquals("naddr1qqqqygzxpsj7dqha57pjk5k37gkn6g4nzakewtmqmnwryyhd3jfwlpgxtspsgqqqw4rs3xyxus", address.toNAddr())
    }

    @Test
    fun nAddrFormatter2() {
        val address = ATag(
            30023,
            "d0debf9fb12def81f43d7c69429bb784812ac1e4d2d53a202db6aac7ea4b466c",
            "guide-wireguard",
            null
        )
        assertEquals("naddr1qq8kwatfv3jj6amfwfjkwatpwfjqygxsm6lelvfda7qlg0tud9pfhduysy4vrexj65azqtdk4tr75j6xdspsgqqqw4rsg32ag8", address.toNAddr())
    }

    @Test
    fun nAddrFormatter3() {
        val address = ATag(
            30023,
            "d1e60465c2b777325e9133f2100d2bb31416dca810f54a1d95665621c5dee193",
            "89de7920",
            "wss://relay.damus.io"
        )
        assertEquals("naddr1qqyrswtyv5mnjv3sqy28wumn8ghj7un9d3shjtnyv9kh2uewd9hsygx3uczxts4hwue9ayfn7ggq62anzstde2qs749pm9tx2csuthhpjvpsgqqqw4rs8pmj38", address.toNAddr())
    }

    @Test
    fun nAddrParserPablo() {
        val result = Nip19.uriToRoute("naddr1qq2hs7p30p6kcunxxamkgcnyd33xxve3veshyq3qyujphdcz69z6jafxpnldae3xtymdekfeatkt3r4qusr3w5krqspqxpqqqpaxjlg805f")
        assertEquals(Nip19.Type.ADDRESS, result?.type)
        assertEquals("31337:27241bb702d145a975260cfedee6265936dcd939eaecb88ea0e4071752c30402:xx1xulrf7wdbdlbc31far", result?.hex)
        assertEquals(null, result?.relay)
        assertEquals("27241bb702d145a975260cfedee6265936dcd939eaecb88ea0e4071752c30402", result?.author)
        assertEquals(31337, result?.kind)
    }

    @Test
    fun nAddrParserGizmo() {
        val result = Nip19.uriToRoute("naddr1qpqrvvfnvccrzdryxgunzvtxvgukge34xfjnqdpcv9sk2desxgmrscesvserzd3h8ycrywphvg6nsvf58ycnqef3v5mnsvt98pjnqdfs8ypzq3huhccxt6h34eupz3jeynjgjgek8lel2f4adaea0svyk94a3njdqvzqqqr4gudhrkyk")
        assertEquals(Nip19.Type.ADDRESS, result?.type)
        assertEquals("30023:46fcbe3065eaf1ae7811465924e48923363ff3f526bd6f73d7c184b16bd8ce4d:613f014d2911fb9df52e048aae70268c0d216790287b5814910e1e781e8e0509", result?.hex)
        assertEquals(null, result?.relay)
        assertEquals("46fcbe3065eaf1ae7811465924e48923363ff3f526bd6f73d7c184b16bd8ce4d", result?.author)
        assertEquals(30023, result?.kind)
    }

    @Test
    fun nAddrParserGizmo2() {
        val result = Nip19.uriToRoute("naddr1qq9rzd3h8y6nqwf5xyuqygzxljlrqe027xh8sy2xtyjwfzfrxcll8afxh4hh847psjckhkxwf5psgqqqw4rsty50fx")
        assertEquals(Nip19.Type.ADDRESS, result?.type)
        assertEquals("30023:46fcbe3065eaf1ae7811465924e48923363ff3f526bd6f73d7c184b16bd8ce4d:1679509418", result?.hex)
        assertEquals(null, result?.relay)
        assertEquals("46fcbe3065eaf1ae7811465924e48923363ff3f526bd6f73d7c184b16bd8ce4d", result?.author)
        assertEquals(30023, result?.kind)
    }

    @Test
    fun nEventParserTest() {
        val result = Nip19.uriToRoute("nostr:nevent1qqs0tsw8hjacs4fppgdg7f5yhgwwfkyua4xcs3re9wwkpkk2qeu6mhql22rcy")
        assertEquals(Nip19.Type.EVENT, result?.type)
        assertEquals("f5c1c7bcbb8855210a1a8f2684ba1ce4d89ced4d8844792b9d60daca0679addc", result?.hex)
        assertEquals(null, result?.relay)
        assertEquals(null, result?.author)
        assertEquals(null, result?.kind)
    }

    @Test
    fun nEventParser() {
        val result = Nip19.uriToRoute("nostr:nevent1qqstvrl6wftd8ht4g0vrp6m30tjs6pdxcvk977g769dcvlptkzu4ftqppamhxue69uhkummnw3ezumt0d5pzp78lz8r60568sd2a8dx3wnj6gume02gxaf92vx4fk67qv5kpagt6qvzqqqqqqygqr86c")
        assertEquals(Nip19.Type.EVENT, result?.type)
        assertEquals("b60ffa7256d3dd7543d830eb717ae50d05a6c32c5f791ed15b867c2bb0b954ac", result?.hex)
        assertEquals("wss://nostr.mom", result?.relay)
        assertEquals("f8ff11c7a7d3478355d3b4d174e5a473797a906ea4aa61aa9b6bc0652c1ea17a", result?.author)
        assertEquals(1, result?.kind)
    }

    @Test
    fun nEventParser2() {
        val result = Nip19.uriToRoute("nostr:nevent1qqsplpuwsgrrmq85rfup6w3w777rxmcmadu590emfx6z4msj2844euqpz3mhxue69uhhyetvv9ujuerpd46hxtnfdupzq3svyhng9ld8sv44950j957j9vchdktj7cxumsep9mvvjthc2pjuqvzqqqqqqye3a70w")

        assertEquals(Nip19.Type.EVENT, result?.type)
        assertEquals("1f878e82063d80f41a781d3a2ef7bc336f1beb7942bf3b49b42aee1251eb5cf0", result?.hex)
        assertEquals("wss://relay.damus.io", result?.relay)
        assertEquals("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c", result?.author)
        assertEquals(1, result?.kind)
    }

    @Test
    fun nEventParser3() {
        val result = Nip19.uriToRoute("nostr:nevent1qqsg6gechd3dhzx38n4z8a2lylzgsmmgeamhmtzz72m9ummsnf0xjfspsdmhxue69uhkummn9ekx7mpvwaehxw309ahx7um5wghx77r5wghxgetk93mhxue69uhhyetvv9ujumn0wd68ytnzvuk8wumn8ghj7mn0wd68ytn9d9h82mny0fmkzmn6d9njuumsv93k2trhwden5te0wfjkccte9ehx7um5wghxyctwvsk8wumn8ghj7un9d3shjtnyv9kh2uewd9hs3kqsdn")

        assertEquals(Nip19.Type.EVENT, result?.type)
        assertEquals("8d2338bb62db88d13cea23f55f27c4886f68cf777dac42f2b65e6f709a5e6926", result?.hex)
        assertEquals("wss://nos.lol,wss://nostr.oxtr.dev,wss://relay.nostr.bg,wss://nostr.einundzwanzig.space,wss://relay.nostr.band,wss://relay.damus.io", result?.relay)
    }

    @Test
    fun nEventParserInvalidChecksum() {
        val result = Nip19.uriToRoute("nostr:nevent1qqsyxq8v0730nz38dupnjzp5jegkyz4gu2ptwcps4v32hjnrap0q0espz3mhxue69uhhyetvv9ujuerpd46hxtnfdupzq3svyhng9ld8sv44950j957j9vchdktj7cxumsep9mvvjthc2pjuqvzqqqqqqyn3t9gj")

        assertEquals(Nip19.Type.EVENT, result?.type)
        assertEquals("4300ec7fa2f98a276f033908349651620aa8e282b76030ab22abca63e85e07e6", result?.hex)
        assertEquals("wss://relay.damus.io", result?.relay)
        assertEquals("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c", result?.author)
        assertEquals(1, result?.kind)
    }

    @Test
    fun nEventFormatter() {
        val nevent = Nip19.createNEvent("f5c1c7bcbb8855210a1a8f2684ba1ce4d89ced4d8844792b9d60daca0679addc", null, null, null)
        assertEquals("nevent1qqs0tsw8hjacs4fppgdg7f5yhgwwfkyua4xcs3re9wwkpkk2qeu6mhql22rcy", nevent)
    }

    @Test
    fun nEventFormatterWithExtraInfo() {
        val nevent = Nip19.createNEvent("f5c1c7bcbb8855210a1a8f2684ba1ce4d89ced4d8844792b9d60daca0679addc", "7fa56f5d6962ab1e3cd424e758c3002b8665f7b0d8dcee9fe9e288d7751ac194", 40, null)
        assertEquals("nevent1qqs0tsw8hjacs4fppgdg7f5yhgwwfkyua4xcs3re9wwkpkk2qeu6mhqzypl62m6ad932k83u6sjwwkxrqq4cve0hkrvdem5la83g34m4rtqegqcyqqqqq2qh26va4", nevent)
    }

    @Test
    fun nEventFormatterWithFullInfo() {
        val nevent = Nip19.createNEvent(
            "1f878e82063d80f41a781d3a2ef7bc336f1beb7942bf3b49b42aee1251eb5cf0",
            "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c",
            1,
            "wss://relay.damus.io"
        )
        assertEquals("nevent1qqsplpuwsgrrmq85rfup6w3w777rxmcmadu590emfx6z4msj2844euqpz3mhxue69uhhyetvv9ujuerpd46hxtnfdupzq3svyhng9ld8sv44950j957j9vchdktj7cxumsep9mvvjthc2pjuqvzqqqqqqye3a70w", nevent)
    }
}
