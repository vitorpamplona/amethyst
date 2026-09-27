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
package com.vitorpamplona.quartz.nip49PrivKeyEnc

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * ncryptsecs produced by other NIP-49 implementations, which Amethyst must keep opening.
 *
 * Generated once by encrypting the same cases with each library (encryption is randomized,
 * so only decryption can be pinned). Cases cover the three key-security bytes, NFKC (the
 * password is given in its un-normalized typed form), astral-plane emoji, significant
 * spaces, an empty password, a key whose bytes are all >= 0x80, and a non-default LOG_N.
 * The reverse direction (those libraries opening Amethyst's output) was checked at the
 * same time against the same versions.
 */
class Nip49InteropTest {
    private class Vector(
        val producer: String,
        val case: String,
        val ncryptsec: String,
        val password: String,
        val secretKeyHex: String,
        val logn: Int,
        val ksb: Int,
    )

    private val vectors =
        listOf(
            Vector("nostr-tools 2.25.2", "spec-key-ascii", "ncryptsec1qggzef0f9c0e4jahr32p98weq679sydr6ea4chnz0kzys4egytrt89dc8e3ml28z8jfq9daj6hn8rkd3kltru9mxzvpc4v2ppgmyp4dew7ugcp98hfayqyehss36xdl95eulzrjhv9z8mk745yvkq9la", "nostr", "3501454135014541350145413501453fefb02227e449e57cf4d3a3ce05378683", 16, 2),
            Vector("nostr-tools 2.25.2", "ksb0", "ncryptsec1qgg2mwlypqz2an0qvkx5wj5kw2xnaunl6648utjxs4acpud2ewpgp4wcv3xwvhf2vgmqqaudk4zl9rcdtryg97j8z7jg6ex0q3tdsl0e2zh4rsedj43jr0th48z4cv5vgausj6fxhsjjzfs8lvkfdu4v", "correct horse battery", "3501454135014541350145413501453fefb02227e449e57cf4d3a3ce05378683", 16, 0),
            Vector("nostr-tools 2.25.2", "ksb1", "ncryptsec1qggxwrt3vtsyy6fn8mha8danwhdwe4dx39r4l3r0fgh393kr6f7a2yt230vsmkuug0lsrjedklp7r4eltcfckdp37x5afc8uk5fzaxkcnvx5q3cyt69n4fgfdddxuejjmlkzerggxlgjqpesn5vk2djk", "correct horse battery", "3501454135014541350145413501453fefb02227e449e57cf4d3a3ce05378683", 16, 1),
            Vector("nostr-tools 2.25.2", "nfkc-typed-form", "ncryptsec1qgggze6805h7slevp5evyekxhh42fl2qk8hlla2uej8934l0f65v7xfhd2njmtdnm2ws9grf6tuzeugj59hctn3rg8dp03s8e62vycvynmf9ymcxp9sczvhx7mntl8wm8gujg00vx9rjvxxt4s8nq893", "\u212B\u2126\u1E9B\u0323", "3501454135014541350145413501453fefb02227e449e57cf4d3a3ce05378683", 16, 2),
            Vector("nostr-tools 2.25.2", "emoji", "ncryptsec1qggpnka7pcakst6nszv492xqk53q07ctdjztklevawatwfwcapyw2qvfmm6xuz3kl4sqyefsw8c5g96zpj6p7dhq8kcjkynm2fz7nwy5q7mtx7vf5dwh9mmhj82snl2tnlpcz6rzdqhl7aan7ygz08f8", "\uD83D\uDD11\uD83D\uDD12 key lock \uD83E\uDDE1", "3501454135014541350145413501453fefb02227e449e57cf4d3a3ce05378683", 16, 2),
            Vector("nostr-tools 2.25.2", "spaces", "ncryptsec1qgg0j53e68fs5ty9d6kvup5qwpglml0knavrkrzhu88345kkrgrfju9hpwksnre3uveqys4elvdjtjz3770l2rdm6a7rfepmumgnp9kzl9wjp9sy575m02xm9rgc86npyj8jgspcq97s9lgd7gtz5lg2", "  leading and trailing  ", "3501454135014541350145413501453fefb02227e449e57cf4d3a3ce05378683", 16, 2),
            Vector("nostr-tools 2.25.2", "empty-password", "ncryptsec1qgg0mnjj0x8um28yrku7k0pxhcwg70l8286lqng949qw2xgw3auejrs8g2whr9qavgus9w7etxlxwuxtw9d4t9uzknq558c6d83vxz48ka25xn064df9cxgv7ty820yrej8yll9d9wd0lyfpn5yuny8r", "", "3501454135014541350145413501453fefb02227e449e57cf4d3a3ce05378683", 16, 2),
            Vector("nostr-tools 2.25.2", "all-high-bytes", "ncryptsec1qgg97h3hl9jwjwneecfa8nfycyh8vh9h4v2td75qxpq2y2xyw2epa9xzjgerxwyl73asymlcmusgx9nygg26kxgtrhepn77aqqej6ufgly4hlzxwl9jdxkt24mpl85lgfus0xcge78pg7ytljva58eyu", "high bytes key!", "8080808080808080808080808080808080808080808080808080808080808080", 16, 2),
            Vector("nostr-tools 2.25.2", "logn8", "ncryptsec1qgydpemrwe496lqjlu9hsd8x39yyrv7w0tpt0s5wh2e0amzq9vwshrqs3enjew7hlwnsym8v0jtrzg7r4l7ku8lx9yw9gcy66std9aluchvy8mxpatqkdqlexmdap6hgv2p2l936zlwks3529q8p57vk", "cheap scrypt pw", "3501454135014541350145413501453fefb02227e449e57cf4d3a3ce05378683", 8, 2),
            Vector("rust-nostr 0.45.5", "spec-key-ascii", "ncryptsec1qggv857u8hqfyknh9vntpa7vpuw2mlw9fwu46qkfzfd4w07rz3nfyyptcxac2a3qcuxqy3v80kxxkzryjdryu5dl6ygwvq9tvgaft5aap27s6arz7wdjhw23h2jdsknvfndjmtarcu0w4nc4gc39zeu4", "nostr", "3501454135014541350145413501453fefb02227e449e57cf4d3a3ce05378683", 16, 2),
            Vector("rust-nostr 0.45.5", "ksb0", "ncryptsec1qggw5euqnv6jwzzu4kaqp4rgda8q87ydpyccvujvrvgn4z2mderjhkr6fvyvv85ve0jspp90gpdh9nlqfjj9clvse4xjyd0aq28uwu4t594yxwlx00k7n6dh50l6ag7xksnwy6dulskrgl4rv52pfcv3", "correct horse battery", "3501454135014541350145413501453fefb02227e449e57cf4d3a3ce05378683", 16, 0),
            Vector("rust-nostr 0.45.5", "ksb1", "ncryptsec1qgg2jalwa5khtqhydjdpv7qku64knkcpppr9calc90g3vvryerqgtpvjef8vqjxh934qr68zxp5fdrn3478mhna7n6rk85hjsnnn6zkwqjxl5gm3fnrjv58m85n9x4frdu8u7mdl3elkggcg0s0ajrar", "correct horse battery", "3501454135014541350145413501453fefb02227e449e57cf4d3a3ce05378683", 16, 1),
            Vector("rust-nostr 0.45.5", "nfkc-typed-form", "ncryptsec1qgg23tn24g595ph4rjz3qw6zaha9p8zh6rwauzfknp3wqywqvlsa9lf4yc54yl9gq6rs9yzl3fvpc63dkahjqqmye7wken7jcjkws4h506ul0qpjh8c5skcyhc08tem7n9hr07484ypmpgaf6sgd470q", "\u212B\u2126\u1E9B\u0323", "3501454135014541350145413501453fefb02227e449e57cf4d3a3ce05378683", 16, 2),
            Vector("rust-nostr 0.45.5", "emoji", "ncryptsec1qgg2x6rkclt0vwulrq83cclekghuytcyp8ju5lfuka9jmt0qh8u4mqgc6nqllw86lz2syqs5mt39fyk4tj9f00dn8hx9g2ckjurgnpajwy9as9lgta25x7jxxnpjh62j4hlr2a06j9qnzkukzq0jph3s", "\uD83D\uDD11\uD83D\uDD12 key lock \uD83E\uDDE1", "3501454135014541350145413501453fefb02227e449e57cf4d3a3ce05378683", 16, 2),
            Vector("rust-nostr 0.45.5", "spaces", "ncryptsec1qggzz6tnfts4xrepjkeuy5shg7z5y62levqknlf94tqry8zwefag3uux0qzlyfqh35ms9vzavurvh9adv36v8lhzllw4yf9zdd3nqajlcqje3k8s73jlyzcufpgwsgqu8pcq5la9mr7pg3zp2yegzzk2", "  leading and trailing  ", "3501454135014541350145413501453fefb02227e449e57cf4d3a3ce05378683", 16, 2),
            Vector("rust-nostr 0.45.5", "empty-password", "ncryptsec1qggyfqwg66hfdylpgw8drhuwg4yu3940zjn9u39tvaape267qs072a0qmpf48pcm8cgqyq4v0e4ygzuyf70e5req74htar44za03klj9em447gpfnshha6ez3rxh4eah3w2tj6vntqhewcjfasdwaa2k", "", "3501454135014541350145413501453fefb02227e449e57cf4d3a3ce05378683", 16, 2),
            Vector("rust-nostr 0.45.5", "all-high-bytes", "ncryptsec1qggdxzuj6y3ha9s240p2a4e9yhpatjzq4tjfc764sqc4kvcqhfn9nljhwdtdpspdx6cs95adxsc6gy86j6xkle2cp6fffr2zsv5k4ua0e266l9l2wgl5x0xavwqg8urtx6xe7z4cftdna49axgws6j6a", "high bytes key!", "8080808080808080808080808080808080808080808080808080808080808080", 16, 2),
            Vector("rust-nostr 0.45.5", "logn8", "ncryptsec1qgywmddn2ymrmjkn3df9vcr87g2jzm63u0apcf28vu5u64lydlg438ztpvu9svjuqensysr7088xpp8ufu7kmzgtnal4w5gcrmfwsegst9aj8trhjllx74pew92wts8sh4lvpmu5552dfhg4zgnu8klt", "cheap scrypt pw", "3501454135014541350145413501453fefb02227e449e57cf4d3a3ce05378683", 8, 2),
            Vector("go-nostr 0.52.3", "spec-key-ascii", "ncryptsec1qggtku4x8v2x5wys9kaf885kslc9g69u7squzxf9v7kz733znjymywgcl7j06p5agvzsya685ztkgnl87gtulwa8htl7w8dp9j45qf77dq3lqak46nchd62cm7lfwt5p4326me9gfvedlv3qgg0xn6h5", "nostr", "3501454135014541350145413501453fefb02227e449e57cf4d3a3ce05378683", 16, 2),
            Vector("go-nostr 0.52.3", "ksb0", "ncryptsec1qggrt6nkhufyfk39fh2ectkm8yc4rkpxdzq20nawkn0nef9lcx2f7qu25njy6zw8jscqqd7t9p4yhn2mhflgsaltqm6hecngcfv0ezhftcy586xd4n2xpgu0e3sxc5ht4phe2pkrpz4u9n0u4vgggrjj", "correct horse battery", "3501454135014541350145413501453fefb02227e449e57cf4d3a3ce05378683", 16, 0),
            Vector("go-nostr 0.52.3", "ksb1", "ncryptsec1qggt49pvsll6rd2efefsv5m6klzwr3adr6t9nuta26yjtkzmapr3falp9w7yqa3u5ktqrtj63skj44r3tfud48t739es23a38vdnqwu9dqnkv4gxj0p4guqx2l38m3tveknycj2wqjmc68lpect44efl", "correct horse battery", "3501454135014541350145413501453fefb02227e449e57cf4d3a3ce05378683", 16, 1),
            Vector("go-nostr 0.52.3", "nfkc-typed-form", "ncryptsec1qggp6pz4dk3jsv2k79razl0j0zd35hry3qkd74a4e2qstnsc7h3etcdhwrt2av0a7v5q97vzw7pmnawpxeadt4lxcqj9s25hfheqfudakdkuk8kmczkuq60z8xxz8emm34yu4mkgt6t7jpve0vasa0pm", "\u212B\u2126\u1E9B\u0323", "3501454135014541350145413501453fefb02227e449e57cf4d3a3ce05378683", 16, 2),
            Vector("go-nostr 0.52.3", "emoji", "ncryptsec1qggx7yhdu7smxvxmszep4wzunthhdxpwnp85yjrkx44gq7u63ylvppp4nez95xlkjafq9270j8g5lsj2tlxf3lv5ecemgcrlu2789zse0a2ywjyf7twxzatz5m3ckt3cj0xth5le35msu7y0wywnuhd4", "\uD83D\uDD11\uD83D\uDD12 key lock \uD83E\uDDE1", "3501454135014541350145413501453fefb02227e449e57cf4d3a3ce05378683", 16, 2),
            Vector("go-nostr 0.52.3", "spaces", "ncryptsec1qgggqt59jswdm0ph58z45sqynzdpjak894ndu9ueykm9qdnccptnkwz6rsvfmfkhulwsy9atea9jqdqpenfhpqsmgckjw9vr64p4vqznfsua8ty0f9j0y6enk24xfulqy3n04wvj774ct6sg3yfghqdx", "  leading and trailing  ", "3501454135014541350145413501453fefb02227e449e57cf4d3a3ce05378683", 16, 2),
            Vector("go-nostr 0.52.3", "empty-password", "ncryptsec1qgg2tmxquvf9924env8er4dxlctjglqqrtksjhnnwu0qvapzn6trs36lqmsswnwvy29s9sdrap2m8m6656hytvr99k9364eqqsy9xe4jyd6ntetvvxnvcxl6ktlpt4dw9yjxe5w9mqhl2ywj8guslyp4", "", "3501454135014541350145413501453fefb02227e449e57cf4d3a3ce05378683", 16, 2),
            Vector("go-nostr 0.52.3", "all-high-bytes", "ncryptsec1qggyhcn43gycdv5hftawfywfje8w3hzj5pr4g8v70q9x49vgutfeda8lx0syku8lcras90vzt9hzzdl2t60wnytrkcfhya5t6gkrajkjkdq25t3thlcxmhhklkte7nn6ktfqsys8ynmscwy6cutzpfj2", "high bytes key!", "8080808080808080808080808080808080808080808080808080808080808080", 16, 2),
            Vector("go-nostr 0.52.3", "logn8", "ncryptsec1qgyqqtmjm9k402reqysneeg8fj700l6sd03vmjwlt6g2waxrg4de2hs5q2jrtktt72uqynxqa76hsz2kfm7834pra0rwjlcgwy920r7uhg797zw63d6a0aee64wqxugxxflzynrhzvt2c2zk2s9u7cxl", "cheap scrypt pw", "3501454135014541350145413501453fefb02227e449e57cf4d3a3ce05378683", 8, 2),
        )

    @Test
    fun decryptsEveryForeignVector() {
        val nip49 = Nip49()
        vectors.forEach {
            val label = "${it.producer} / ${it.case}"
            val info = Nip49.EncryptedInfo.decodePayload(it.ncryptsec)!!
            assertEquals(2, info.version.toInt(), label)
            assertEquals(it.logn, info.logn.toInt(), label)
            assertEquals(it.ksb, info.keySecurity.toInt(), label)
            assertEquals(it.secretKeyHex, nip49.decrypt(it.ncryptsec, it.password), label)
        }
    }
}
