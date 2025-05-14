/**
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

import com.vitorpamplona.quartz.nip01Core.tags.addressables.ATag
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.entities.Note
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NIP19ParserTest {
    @Test()
    fun uri_to_route_null() {
        val actual = Nip19Parser.uriToRoute(null)

        Assert.assertEquals(null, actual)
    }

    @Test()
    fun uri_to_route_unknown() {
        val actual = Nip19Parser.uriToRoute("nostr:unknown")

        Assert.assertEquals(null, actual)
    }

    @Test()
    fun uri_to_route_npub() {
        val actual =
            Nip19Parser.uriToRoute("nostr:npub1hv7k2s755n697sptva8vkh9jz40lzfzklnwj6ekewfmxp5crwdjs27007y")

        Assert.assertTrue(actual?.entity is NPub)
        Assert.assertEquals(
            "bb3d6543d4a4f45f402b674ecb5cb2155ff12456fcdd2d66d9727660d3037365",
            (actual?.entity as? NPub)?.hex,
        )
    }

    @Test()
    fun uri_to_route_note() {
        val result =
            Nip19Parser.uriToRoute("nostr:note1stqea6wmwezg9x6yyr6qkukw95ewtdukyaztycws65l8wppjmtpscawevv")?.entity as? Note

        assertNotNull(result)
        Assert.assertEquals(
            "82c19ee9db7644829b4420f40b72ce2d32e5b7962744b261d0d53e770432dac3",
            result?.hex,
        )
    }

    @Test()
    fun uri_to_route_nprofile() {
        val actual = Nip19Parser.uriToRoute("nostr:nprofile")

        Assert.assertEquals(null, actual)
    }

    @Test()
    fun uri_to_route_incomplete_nevent() {
        val actual = Nip19Parser.uriToRoute("nostr:nevent")

        Assert.assertEquals(null, actual)
    }

    @Test()
    fun uri_to_route_incomplete_nrelay() {
        val actual = Nip19Parser.uriToRoute("nostr:nrelay")

        Assert.assertEquals(null, actual)
    }

    @Test()
    fun uri_to_route_incomplete_naddr() {
        val actual = Nip19Parser.uriToRoute("nostr:naddr")

        Assert.assertEquals(null, actual)
    }

    @Test()
    fun uri_to_route_complete_nprofile_2() {
        val actual = Nip19Parser.uriToRoute("nostr:nprofile1qqsyvrp9u6p0mfur9dfdru3d853tx9mdjuhkphxuxgfwmryja7zsvhqpzamhxue69uhhv6t5daezumn0wd68yvfwvdhk6tcpz9mhxue69uhkummnw3ezuamfdejj7qgwwaehxw309ahx7uewd3hkctcscpyug")

        Assert.assertNotNull(actual)
        Assert.assertTrue(actual?.entity is NProfile)
        Assert.assertEquals("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c", (actual?.entity as? NProfile)?.hex)
        Assert.assertEquals("wss://vitor.nostr1.com/", (actual?.entity as? NProfile)?.relay?.first())
    }

    @Test()
    fun uri_to_route_complete_nprofile() {
        val actual = Nip19Parser.uriToRoute("nostr:nprofile1qy2hwumn8ghj7un9d3shjtnyv9kh2uewd9hj7qgwwaehxw309ahx7uewd3hkctcpr9mhxue69uhhyetvv9ujuumwdae8gtnnda3kjctv9uqzq9thu3vem5gvsc6f3l3uyz7c92h6lq56t9wws0zulzkrgc6nrvym5jfztf")

        Assert.assertTrue(actual?.entity is NProfile)
        Assert.assertEquals("1577e4599dd10c863498fe3c20bd82aafaf829a595ce83c5cf8ac3463531b09b", (actual?.entity as? NProfile)?.hex)
    }

    @Test()
    fun uri_to_route_complete_nevent() {
        val actual = Nip19Parser.uriToRoute("nostr:nevent1qy2hwumn8ghj7un9d3shjtnyv9kh2uewd9hj7qgwwaehxw309ahx7uewd3hkctcpr9mhxue69uhhyetvv9ujuumwdae8gtnnda3kjctv9uq36amnwvaz7tmjv4kxz7fwvd5xjcmpvahhqmr9vfejucm0d5hsz9mhwden5te0wfjkccte9ec8y6tdv9kzumn9wshsz8thwden5te0dehhxarj9ekh2arfdeuhwctvd3jhgtnrdakj7qg3waehxw309ucngvpwvcmh5tnfduhszythwden5te0dehhxarj9emkjmn99uq3jamnwvaz7tmhv4kxxmmdv5hxummnw3ezuamfdejj7qpqvsup5xk3e2quedxjvn2gjppc0lqny5dmnr2ypc9tftwmdxta0yjqrd6n50")

        Assert.assertTrue(actual?.entity is NEvent)
        Assert.assertEquals("64381a1ad1ca81ccb4d264d48904387fc13251bb98d440e0ab4addb6997d7924", (actual?.entity as? NEvent)?.hex)
    }

    @Test()
    fun uri_to_route_complete_naddr() {
        val actual = Nip19Parser.uriToRoute("nostr:naddr1qqyxzmt9w358jum5qyt8wumn8ghj7un9d3shjtnwdaehgu3wvfskueqzypd7v3r24z33cydnk3fmlrd0exe5dlej3506zxs05q4puerp765mzqcyqqq8scsq6mk7u")

        Assert.assertTrue(actual?.entity is NAddress)
        Assert.assertEquals("30818:5be6446aa8a31c11b3b453bf8dafc9b346ff328d1fa11a0fa02a1e6461f6a9b1:amethyst", (actual?.entity as? NAddress)?.aTag())
    }

    @Test
    fun nAddrParser() {
        val result =
            Nip19Parser.uriToRoute(
                "nostr:naddr1qqqqygzxpsj7dqha57pjk5k37gkn6g4nzakewtmqmnwryyhd3jfwlpgxtspsgqqqw4rs3xyxus",
            )
        assertEquals(
            "30023:460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c:",
            (result?.entity as? NAddress)?.aTag(),
        )
    }

    @Test
    fun nAddrParser2() {
        val result =
            Nip19Parser.uriToRoute(
                "nostr:naddr1qq8kwatfv3jj6amfwfjkwatpwfjqygxsm6lelvfda7qlg0tud9pfhduysy4vrexj65azqtdk4tr75j6xdspsgqqqw4rsg32ag8",
            )
        assertEquals(
            "30023:d0debf9fb12def81f43d7c69429bb784812ac1e4d2d53a202db6aac7ea4b466c:guide-wireguard",
            (result?.entity as? NAddress)?.aTag(),
        )
    }

    @Test
    fun nAddrParse3() {
        val result =
            Nip19Parser.uriToRoute(
                "naddr1qqyrswtyv5mnjv3sqy28wumn8ghj7un9d3shjtnyv9kh2uewd9hsygx3uczxts4hwue9ayfn7ggq62anzstde2qs749pm9tx2csuthhpjvpsgqqqw4rs8pmj38",
            )
        assertTrue(result?.entity is NAddress)
        assertEquals(
            "30023:d1e60465c2b777325e9133f2100d2bb31416dca810f54a1d95665621c5dee193:89de7920",
            (result?.entity as? NAddress)?.aTag(),
        )
        assertEquals("wss://relay.damus.io", (result?.entity as? NAddress)?.relay?.get(0))
    }

    @Test
    fun nAddrATagParse3() {
        val address =
            ATag.parse(
                "30023:d1e60465c2b777325e9133f2100d2bb31416dca810f54a1d95665621c5dee193:89de7920",
                "wss://relay.damus.io",
            )
        assertEquals(30023, address?.kind)
        assertEquals(
            "d1e60465c2b777325e9133f2100d2bb31416dca810f54a1d95665621c5dee193",
            address?.pubKeyHex,
        )
        assertEquals("89de7920", address?.dTag)
        assertEquals("wss://relay.damus.io", address?.relay)
        assertEquals(
            "naddr1qqyrswtyv5mnjv3sqy28wumn8ghj7un9d3shjtnyv9kh2uewd9hsygx3uczxts4hwue9ayfn7ggq62anzstde2qs749pm9tx2csuthhpjvpsgqqqw4rs8pmj38",
            address?.toNAddr(),
        )
    }

    @Test
    fun nAddrFormatter() {
        val address =
            ATag(
                30023,
                "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c",
                "",
                null,
            )
        assertEquals(
            "naddr1qqqqygzxpsj7dqha57pjk5k37gkn6g4nzakewtmqmnwryyhd3jfwlpgxtspsgqqqw4rs3xyxus",
            address.toNAddr(),
        )
    }

    @Test
    fun nAddrFormatter2() {
        val address =
            ATag(
                30023,
                "d0debf9fb12def81f43d7c69429bb784812ac1e4d2d53a202db6aac7ea4b466c",
                "guide-wireguard",
                null,
            )
        assertEquals(
            "naddr1qq8kwatfv3jj6amfwfjkwatpwfjqygxsm6lelvfda7qlg0tud9pfhduysy4vrexj65azqtdk4tr75j6xdspsgqqqw4rsg32ag8",
            address.toNAddr(),
        )
    }

    @Test
    fun nAddrFormatter3() {
        val address =
            ATag(
                30023,
                "d1e60465c2b777325e9133f2100d2bb31416dca810f54a1d95665621c5dee193",
                "89de7920",
                "wss://relay.damus.io",
            )
        assertEquals(
            "naddr1qqyrswtyv5mnjv3sqy28wumn8ghj7un9d3shjtnyv9kh2uewd9hsygx3uczxts4hwue9ayfn7ggq62anzstde2qs749pm9tx2csuthhpjvpsgqqqw4rs8pmj38",
            address.toNAddr(),
        )
    }

    @Test
    fun nAddrParserPablo() {
        val result =
            Nip19Parser
                .uriToRoute(
                    "naddr1qq2hs7p30p6kcunxxamkgcnyd33xxve3veshyq3qyujphdcz69z6jafxpnldae3xtymdekfeatkt3r4qusr3w5krqspqxpqqqpaxjlg805f",
                )?.entity as? NAddress

        assertNotNull(result)
        assertEquals(
            "31337:27241bb702d145a975260cfedee6265936dcd939eaecb88ea0e4071752c30402:xx1xulrf7wdbdlbc31far",
            result?.aTag(),
        )
        assertEquals(true, result?.relay?.isEmpty())
        assertEquals("27241bb702d145a975260cfedee6265936dcd939eaecb88ea0e4071752c30402", result?.author)
        assertEquals(31337, result?.kind)
    }

    @Test
    fun nAddrParserGizmo() {
        val result =
            Nip19Parser
                .uriToRoute(
                    "naddr1qpqrvvfnvccrzdryxgunzvtxvgukge34xfjnqdpcv9sk2desxgmrscesvserzd3h8ycrywphvg6nsvf58ycnqef3v5mnsvt98pjnqdfs8ypzq3huhccxt6h34eupz3jeynjgjgek8lel2f4adaea0svyk94a3njdqvzqqqr4gudhrkyk",
                )?.entity as? NAddress

        assertNotNull(result)
        assertEquals(
            "30023:46fcbe3065eaf1ae7811465924e48923363ff3f526bd6f73d7c184b16bd8ce4d:613f014d2911fb9df52e048aae70268c0d216790287b5814910e1e781e8e0509",
            result?.aTag(),
        )
        assertEquals(true, result?.relay?.isEmpty())
        assertEquals("46fcbe3065eaf1ae7811465924e48923363ff3f526bd6f73d7c184b16bd8ce4d", result?.author)
        assertEquals(30023, result?.kind)
    }

    @Test
    fun nAddrParserGizmo2() {
        val result =
            Nip19Parser
                .uriToRoute(
                    "naddr1qq9rzd3h8y6nqwf5xyuqygzxljlrqe027xh8sy2xtyjwfzfrxcll8afxh4hh847psjckhkxwf5psgqqqw4rsty50fx",
                )?.entity as? NAddress

        assertNotNull(result)
        assertEquals(
            "30023:46fcbe3065eaf1ae7811465924e48923363ff3f526bd6f73d7c184b16bd8ce4d:1679509418",
            result?.aTag(),
        )
        assertEquals(true, result?.relay?.isEmpty())
        assertEquals("46fcbe3065eaf1ae7811465924e48923363ff3f526bd6f73d7c184b16bd8ce4d", result?.author)
        assertEquals(30023, result?.kind)
    }

    @Test
    fun nEventParserCompleteTest() {
        val result =
            Nip19Parser.uriToRoute("nostr:nevent1qqsdw6xpk28tjnrajz4xhy2jqg0md8ywxj6997rsutjzxs0207tedjspz4mhxue69uhhyetvv9ujumn0wd68ytnzvuhsygx2crjrydvqdksffurc0fdsfc566pxtrg78afw0v8kursecwdqg9vpsgqqqqqqsnknas6")?.entity as? NEvent

        assertNotNull(result)
        assertEquals("d768c1b28eb94c7d90aa6b9152021fb69c8e34b452f870e2e42341ea7f9796ca", result?.hex)
        assertEquals("wss://relay.nostr.bg/", result?.relay?.firstOrNull())
        assertEquals("cac0e43235806da094f0787a5b04e29ad04cb1a3c7ea5cf61edc1c338734082b", result?.author)
        assertEquals(1, result?.kind)
    }

    @Test
    fun nEventParserTest() {
        val result =
            Nip19Parser.uriToRoute("nostr:nevent1qqs0tsw8hjacs4fppgdg7f5yhgwwfkyua4xcs3re9wwkpkk2qeu6mhql22rcy")?.entity as? NEvent

        assertNotNull(result)
        assertEquals("f5c1c7bcbb8855210a1a8f2684ba1ce4d89ced4d8844792b9d60daca0679addc", result?.hex)
        assertEquals(true, result?.relay?.isEmpty())
        assertEquals(null, result?.author)
        assertEquals(null, result?.kind)
    }

    @Test
    fun nEventParser2Test() {
        val result =
            Nip19Parser.uriToRoute("nostr:nevent1qqsfvaa2w3nkw472lt2ezr6x5x347k8hht398vp7hrl6wrdjldry86sprfmhxue69uhhyetvv9ujuam9wd6x2unwvf6xxtnrdaks5myyah")?.entity as? NEvent

        assertNotNull(result)
        assertEquals("9677aa74676757cafad5910f46a1a35f58f7bae253b03eb8ffa70db2fb4643ea", result?.hex)
        assertEquals("wss://relay.westernbtc.com", result?.relay?.firstOrNull())
        assertEquals(null, result?.author)
        assertEquals(null, result?.kind)
    }

    @Test
    fun nEventParser() {
        val result =
            Nip19Parser
                .uriToRoute(
                    "nostr:nevent1qqstvrl6wftd8ht4g0vrp6m30tjs6pdxcvk977g769dcvlptkzu4ftqppamhxue69uhkummnw3ezumt0d5pzp78lz8r60568sd2a8dx3wnj6gume02gxaf92vx4fk67qv5kpagt6qvzqqqqqqygqr86c",
                )?.entity as? NEvent

        assertNotNull(result)
        assertEquals("b60ffa7256d3dd7543d830eb717ae50d05a6c32c5f791ed15b867c2bb0b954ac", result?.hex)
        assertEquals("wss://nostr.mom", result?.relay?.get(0))
        assertEquals("f8ff11c7a7d3478355d3b4d174e5a473797a906ea4aa61aa9b6bc0652c1ea17a", result?.author)
        assertEquals(1, result?.kind)
    }

    @Test
    fun nEventParser2() {
        val result =
            Nip19Parser
                .uriToRoute(
                    "nostr:nevent1qqsplpuwsgrrmq85rfup6w3w777rxmcmadu590emfx6z4msj2844euqpz3mhxue69uhhyetvv9ujuerpd46hxtnfdupzq3svyhng9ld8sv44950j957j9vchdktj7cxumsep9mvvjthc2pjuqvzqqqqqqye3a70w",
                )?.entity as? NEvent

        assertNotNull(result)
        assertEquals("1f878e82063d80f41a781d3a2ef7bc336f1beb7942bf3b49b42aee1251eb5cf0", result?.hex)
        assertEquals("wss://relay.damus.io", result?.relay?.get(0))
        assertEquals("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c", result?.author)
        assertEquals(1, result?.kind)
    }

    @Test
    fun nEventParser3() {
        val result =
            Nip19Parser
                .uriToRoute(
                    "nostr:nevent1qqsg6gechd3dhzx38n4z8a2lylzgsmmgeamhmtzz72m9ummsnf0xjfspsdmhxue69uhkummn9ekx7mpvwaehxw309ahx7um5wghx77r5wghxgetk93mhxue69uhhyetvv9ujumn0wd68ytnzvuk8wumn8ghj7mn0wd68ytn9d9h82mny0fmkzmn6d9njuumsv93k2trhwden5te0wfjkccte9ehx7um5wghxyctwvsk8wumn8ghj7un9d3shjtnyv9kh2uewd9hs3kqsdn",
                )?.entity as? NEvent

        assertNotNull(result)
        assertEquals("8d2338bb62db88d13cea23f55f27c4886f68cf777dac42f2b65e6f709a5e6926", result?.hex)
        assertEquals(
            "wss://nos.lol,wss://nostr.oxtr.dev,wss://relay.nostr.bg,wss://nostr.einundzwanzig.space,wss://relay.nostr.band,wss://relay.damus.io",
            result?.relay?.joinToString(","),
        )
    }

    @Test
    fun nEventParserInvalidChecksum() {
        val result =
            Nip19Parser
                .uriToRoute(
                    "nostr:nevent1qqsyxq8v0730nz38dupnjzp5jegkyz4gu2ptwcps4v32hjnrap0q0espz3mhxue69uhhyetvv9ujuerpd46hxtnfdupzq3svyhng9ld8sv44950j957j9vchdktj7cxumsep9mvvjthc2pjuqvzqqqqqqyn3t9gj",
                )?.entity as? NEvent

        assertNotNull(result)
        assertEquals("4300ec7fa2f98a276f033908349651620aa8e282b76030ab22abca63e85e07e6", result?.hex)
        assertEquals("wss://relay.damus.io", result?.relay?.get(0))
        assertEquals("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c", result?.author)
        assertEquals(1, result?.kind)
    }

    @Test
    fun nEventFormatter() {
        val nevent =
            NEvent.create(
                "f5c1c7bcbb8855210a1a8f2684ba1ce4d89ced4d8844792b9d60daca0679addc",
                null,
                null,
                null,
            )
        assertEquals("nevent1qqs0tsw8hjacs4fppgdg7f5yhgwwfkyua4xcs3re9wwkpkk2qeu6mhql22rcy", nevent)
    }

    @Test
    fun nEventFormatterWithExtraInfo() {
        val nevent =
            NEvent.create(
                "f5c1c7bcbb8855210a1a8f2684ba1ce4d89ced4d8844792b9d60daca0679addc",
                "7fa56f5d6962ab1e3cd424e758c3002b8665f7b0d8dcee9fe9e288d7751ac194",
                40,
                null,
            )
        assertEquals(
            "nevent1qqs0tsw8hjacs4fppgdg7f5yhgwwfkyua4xcs3re9wwkpkk2qeu6mhqzypl62m6ad932k83u6sjwwkxrqq4cve0hkrvdem5la83g34m4rtqegqcyqqqqq2qh26va4",
            nevent,
        )
    }

    @Test
    fun nEventFormatterWithFullInfo() {
        val nevent =
            NEvent.create(
                "1f878e82063d80f41a781d3a2ef7bc336f1beb7942bf3b49b42aee1251eb5cf0",
                "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c",
                1,
                "wss://relay.damus.io",
            )
        assertEquals(
            "nevent1qqsplpuwsgrrmq85rfup6w3w777rxmcmadu590emfx6z4msj2844euqpz3mhxue69uhhyetvv9ujuerpd46hxtnfdupzq3svyhng9ld8sv44950j957j9vchdktj7cxumsep9mvvjthc2pjuqvzqqqqqqye3a70w",
            nevent,
        )
    }

    @Test
    fun decodeBech32WithInvisibleCharacter() {
        val bomChar = '\uFEFF'
        val withBom = bomChar + "nsec1lfkarc7439n4l3uahr45ej8mrjc39dd879t0ps355550dj8j9uzs3rnw24"

        assertEquals(
            "nsec1lfkarc7439n4l3uahr45ej8mrjc39dd879t0ps355550dj8j9uzs3rnw24",
            withBom.bechToBytes().toNsec(),
        )
    }
}
