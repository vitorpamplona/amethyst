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
package com.vitorpamplona.quartz.nip01Core

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasherSerializer
import com.vitorpamplona.quartz.nip01Core.crypto.checkSignature
import com.vitorpamplona.quartz.nip01Core.crypto.verifyId
import com.vitorpamplona.quartz.nip01Core.crypto.verifySignature
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.utils.EventFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EventSigCheck {
    val payload1 =
        "[\"EVENT\",\"40b9\",{\"id\":\"48a72b485d38338627ec9d427583551f9af4f016c739b8ec0d6313540a8b12cf\"," +
            "\"kind\":1,\"pubkey\":\"3d842afecd5e293f28b6627933704a3fb8ce153aa91d790ab11f6a752d44a42d\"," +
            "\"created_at\":1677940007,\"content\":" +
            "\"I got asked about follower count again today. Why does my follower count go down when " +
            "I delete public relays (in our list) and replace them with filter.nostr.wine? \\n\\nI’ll " +
            "give you one final explanation to rule them all. First, let’s go over how clients calculate " +
            "your follower count.\\n\\n1. Your client sends a request to all your connected relays asking " +
            "for accounts who follow you\\n2. Relays answer back with the events requested\\n3. The client " +
            "aggregates the event total and displays it\\n\\nEach relay has a set limit on how many stored " +
            "events it will return per request. For some relays it’s 500, others 1000, some as high as 5000. " +
            "Let’s say for simplicity that all your public relays use 500 as their limit. If you ask 10 " +
            "relays for your followers the max possible answer you can get is 5000. That won’t change if " +
            "you have 20,000 followers or 100,000. You may get back a “different” 5000 each time, but you’ll " +
            "still cap out at 5000 because that is the most events your client will receive.\u2028\u2028Our " +
            "limit on filter.nostr.wine is 2000 events. If you replace 10 public relays with only " +
            "filter.nostr.wine, the MOST followers you will ever get back from our filter relay is 2000. " +
            "That doesn’t mean you only have 2000 followers or that your reach is reduced in any way.\\n\\nAs " +
            "long as you are writing to and reading from the same public relays, neither your reach nor any " +
            "content was lost. That concludes my TED talk. I hope you all have a fantastic day and weekend.\"," +
            "\"tags\":[],\"sig\":\"dcaf8ab98bb9179017b35bd814092850d1062b26c263dff89fb1ae8c019a324139d1729012d" +
            "9d05ff0a517f76b1117d869b2cc7d36bea8aa5f4b94c5e2548aa8\"}]"

    @Test
    fun testUnicode2028and2029ShouldNotBeEscaped() {
        val msg = OptimizedJsonMapper.fromJsonToMessage(payload1) as EventMessage

        // Should pass
        msg.event.checkSignature()
    }

    @Test
    fun checkSerializationWithEmojis() {
        val event =
            EventFactory.create<ReactionEvent>(
                id = "ac2fb0c9b72a6fefe60262fbce6eb8740380b7f964200cb8efdd2e72fcb1ddb0",
                pubKey = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c",
                createdAt = 1690288395,
                kind = 7,
                tags =
                    arrayOf(
                        arrayOf("e", "2cc5b6e012c5a64fcf580fc1b53bed25ed0d7f785fd896744524b1a114dcc86e"),
                        arrayOf("p", "c80b5248fbe8f392bc3ba45091fb4e6e2b5872387601bf90f53992366b30d720"),
                    ),
                content = "🚀",
                sig = "6df6aeef98c21a0508a788cbe6a2a6825e5cc69e57dd843dc6e6d4ce2c28dd042947723fda3d8b24489305d86b1e81f9f66b390d6f7dabf9742cd3775e17e53f",
            )

        val old = EventHasherSerializer.makeJsonForId(event.pubKey, event.createdAt, event.kind, event.tags, event.content)
        val new = EventHasherSerializer.fastMakeJsonForId(event.pubKey, event.createdAt, event.kind, event.tags, event.content)

        assertEquals(old.toByteArray().joinToString(), new.joinToString())
        assertTrue(event.verifySignature())
        assertTrue(event.verifyId())
    }

    @Test
    fun checkSerializationWithUnicode() {
        val event =
            EventFactory.create<ClassifiedsEvent>(
                id = "e395a1a4cad5b9cf930533c48c2dd175a40331a1d33af8f11b776a0970bc1446",
                pubKey = "3fa2504f693c7f0fe71bded634339ae5383a8b14b4164d7c4935830b048dce12",
                createdAt = 1707335824,
                kind = 30402,
                tags =
                    arrayOf(
                        arrayOf("d", "b5dd96da-eb92-463a-9cfd-70f1c5ca7e64"),
                        arrayOf("title", "Google Pixel 7A (NUOVO)\n8GB RAM/128GB MEMORIA"),
                        arrayOf("summary", "📱Google Pixel 7A (NUOVO)\n👉 8GB RAM/128GB MEMORIA \n\nRete : 5G\nColore : Grigio antracite\n\n👤Sistema operativo : GrapheneOS \n\n🔥Riacquista la tua privacy🔥\n\nℹ️Spacchettato solo per modifica ROM.\n\nNessuna app google e servizi google\n\nSolo app opensource \n\nSet-up privacy oriented incluso dedicato in base a vostre esigenze con Profili dedicati personale/lavoro, Vpn e Tor\n\n🔸️Android 14\n🔸️GrapheneOS  ROM !!!!!!!\n🔸️Adattatore incluso Usb c / Usb per passaggio dati\n🔸️Ricarica con connettore USB Type-C \n🔸Dual SIM (nano SIM singola ed eSIM)\n🔸️Schermo da 6.1 pollici\n🔸️Fotocamera da 64 megapixel\n🔸️8 gb memoria ram\n🔸️128 gb memoria interna\n🔸Dimensioni: 155 x 152 x 72.9 mm\n🔸Sblocco con l'impronta tramite sensore di impronte digitali integrato nel display\n🔸️Batteria da 4385 mAh (Ricarica veloce e Ricarica wireless)\n🔸Materiali: Rivestimento in vetro Corning Gorilla Glass 3 resistente ai graffi\n🔸Resistenza all'acqua e alla polvere di grado IP67 \n\n✅️Cover in slicone in omaggio🔥\n\n💶PREZZO💶 \n\n💰380 € ( SOLO IN BITCOIN) \n\n🚚spedizione privacy oriented da Punto di ritiro a Punto di ritiro (SOLO ITALIA) inclusa, solo scrivendo allo Zio il ref: \"Ziophone21\" \n\n🚚Spedizione Full Privacy ( PREMIUM) \nNessun dato da parte utente, per chi necessitasse di questa sped, la differenza si paga a parte.\n\nAccettati pagamenti:\nBTC 🔗\nBTC ⚡️\nBTC💧\nhttps://image.nostr.build/87c79c56f270ca04607bc6d72b21786837f81344a960a3787820dc0c482c6660.jpg#m=image%2Fjpeg&dim=1254x1280&blurhash=%7CWHB--yGi%5E4mR3IVV%40RhIT%25LIpf5t6RjofafofWBMcMxbH%25MbJofofogt7j%5Bt7azWBoeWVj%5BayayD%24RikCt8t8off7j%5DogWBoej%5BWCofayazayoeROV%40j%5DogfloebHa%7DkCj%3Fayj%5Bj%5Bj%5Bayj%5BWVj%40e-fPa%7Dj%5BWVj%40j%5Ba%23j%5B&x=17b7ff98875a6e6d6e6bddee30e0a1c18ccf4281db940ee8b5bcc0736d2137c6"),
                        arrayOf("price", "1000", "SATS"),
                        arrayOf("t", "Electronics"),
                        arrayOf("location", "Brescia Italia"),
                        arrayOf("publishedAt", "1707335824"),
                        arrayOf("condition", "like new"),
                        arrayOf("image", "https://image.nostr.build/87c79c56f270ca04607bc6d72b21786837f81344a960a3787820dc0c482c6660.jpg#m=image%2Fjpeg&dim=1254x1280&blurhash=%7CWHB--yGi%5E4mR3IVV%40RhIT%25LIpf5t6RjofafofWBMcMxbH%25MbJofofogt7j%5Bt7azWBoeWVj%5BayayD%24RikCt8t8off7j%5DogWBoej%5BWCofayazayoeROV%40j%5DogfloebHa%7DkCj%3Fayj%5Bj%5Bj%5Bayj%5BWVj%40e-fPa%7Dj%5BWVj%40j%5Ba%23j%5B&x=17b7ff98875a6e6d6e6bddee30e0a1c18ccf4281db940ee8b5bcc0736d2137c6"),
                        arrayOf("r", "https://image.nostr.build/87c79c56f270ca04607bc6d72b21786837f81344a960a3787820dc0c482c6660.jpg#m=image%2Fjpeg&dim=1254x1280&blurhash=%7CWHB--yGi%5E4mR3IVV%40RhIT%25LIpf5t6RjofafofWBMcMxbH%25MbJofofogt7j%5Bt7azWBoeWVj%5BayayD%24RikCt8t8off7j%5DogWBoej%5BWCofayazayoeROV%40j%5DogfloebHa%7DkCj%3Fayj%5Bj%5Bj%5Bayj%5BWVj%40e-fPa%7Dj%5BWVj%40j%5Ba%23j%5B&x=17b7ff98875a6e6d6e6bddee30e0a1c18ccf4281db940ee8b5bcc0736d2137c6"),
                        arrayOf("alt", "Classifieds listing"),
                    ),
                content = "📱Google Pixel 7A (NUOVO)\n👉 8GB RAM/128GB MEMORIA \n\nRete : 5G\nColore : Grigio antracite\n\n👤Sistema operativo : GrapheneOS \n\n🔥Riacquista la tua privacy🔥\n\nℹ️Spacchettato solo per modifica ROM.\n\nNessuna app google e servizi google\n\nSolo app opensource \n\nSet-up privacy oriented incluso dedicato in base a vostre esigenze con Profili dedicati personale/lavoro, Vpn e Tor\n\n🔸️Android 14\n🔸️GrapheneOS  ROM !!!!!!!\n🔸️Adattatore incluso Usb c / Usb per passaggio dati\n🔸️Ricarica con connettore USB Type-C \n🔸Dual SIM (nano SIM singola ed eSIM)\n🔸️Schermo da 6.1 pollici\n🔸️Fotocamera da 64 megapixel\n🔸️8 gb memoria ram\n🔸️128 gb memoria interna\n🔸Dimensioni: 155 x 152 x 72.9 mm\n🔸Sblocco con l'impronta tramite sensore di impronte digitali integrato nel display\n🔸️Batteria da 4385 mAh (Ricarica veloce e Ricarica wireless)\n🔸Materiali: Rivestimento in vetro Corning Gorilla Glass 3 resistente ai graffi\n🔸Resistenza all'acqua e alla polvere di grado IP67 \n\n✅️Cover in slicone in omaggio🔥\n\n💶PREZZO💶 \n\n💰380 € ( SOLO IN BITCOIN) \n\n🚚spedizione privacy oriented da Punto di ritiro a Punto di ritiro (SOLO ITALIA) inclusa, solo scrivendo allo Zio il ref: \"Ziophone21\" \n\n🚚Spedizione Full Privacy ( PREMIUM) \nNessun dato da parte utente, per chi necessitasse di questa sped, la differenza si paga a parte.\n\nAccettati pagamenti:\nBTC 🔗\nBTC ⚡️\nBTC💧\nhttps://image.nostr.build/87c79c56f270ca04607bc6d72b21786837f81344a960a3787820dc0c482c6660.jpg#m=image%2Fjpeg&dim=1254x1280&blurhash=%7CWHB--yGi%5E4mR3IVV%40RhIT%25LIpf5t6RjofafofWBMcMxbH%25MbJofofogt7j%5Bt7azWBoeWVj%5BayayD%24RikCt8t8off7j%5DogWBoej%5BWCofayazayoeROV%40j%5DogfloebHa%7DkCj%3Fayj%5Bj%5Bj%5Bayj%5BWVj%40e-fPa%7Dj%5BWVj%40j%5Ba%23j%5B&x=17b7ff98875a6e6d6e6bddee30e0a1c18ccf4281db940ee8b5bcc0736d2137c6",
                sig = "e03c81ec2543e777583bfcf93c0d82d3055a47a541b1a961db010ed2390e56ddc59e4f891505672757b7ac0d52e891854fdd61f1a4e13b4e6aa563e94cb9368e",
            )

        val old = EventHasherSerializer.makeJsonForId(event.pubKey, event.createdAt, event.kind, event.tags, event.content)
        val new = EventHasherSerializer.fastMakeJsonForId(event.pubKey, event.createdAt, event.kind, event.tags, event.content)

        println(old)
        println(String(new))

        assertEquals(old, String(new))
        assertTrue(event.verifySignature())
        assertTrue(event.verifyId())
    }

    @Test
    fun checkSerializationLargeZap() {
        val event =
            EventFactory.create<LnZapEvent>(
                id = "e7f09fddf39fc6cb604708b6af7b4d4adbb07b412847ebb004064040fe8c4b1e",
                pubKey = "79f00d3f5a19ec806189fcab03c1be4ff81d18ee4f653c88fac41fe03570f432",
                createdAt = 1728921077,
                kind = 9735,
                tags =
                    arrayOf(
                        arrayOf("p", "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"),
                        arrayOf("e", "58f22b06a219f92fb535c71f7098e076751cf2798208acabbbdefbcb950585f5"),
                        arrayOf("P", "21335073401a310cc9179fe3a77e9666710cfdf630dfd840f972c183a244b1ad"),
                        arrayOf("bolt11", "lnbc420n1pns600jdqjfah8wctjvss0p8at5ynp4qtfc238rdkzsj26waa3l8zgag9damzltzsqcrlscj9gvpc7ch2qs2pp5ks03qwm8laa78hnh0xy0p78l6wmyuj3zfqas3gzc2a52lj2zrmtqsp539528j3tvvfzpk8n5v966zccvj3pq2l3etxqxh5qsp8emh29yw3q9qyysgqcqpcxqyz5vqrzjqvdnqyc82a9maxu6c7mee0shqr33u4z9z04wpdwhf96gxzpln8jcrapyqqqqqqp2rcqqqqlgqqqqqzsq2qrzjqw9fu4j39mycmg440ztkraa03u5qhtuc5zfgydsv6ml38qd4azymlapyqqqqqqqp9sqqqqlgqqqq86qqjqrzjq26922n6s5n5undqrf78rjjhgpcczafws45tx8237y7pzx3fg8wwxrgayyqq2mgqqqqqqqqqqqqqqqqq2quc90y7tgfxuauh0vjfvhxjgektaycfesne76jcuk4u9mt6a9l39pddzk3muwy03sjvfk0w8390xxnpzu2656jf2l73ya59ye2yx9aaqpdgxmaq"),
                        arrayOf("preimage", "0718bf6ba9f503917a2568dac7fe7a5b9361eeb708cd5f57b700060a173ed652"),
                        arrayOf("description", "{\"id\":\"1110d1216fff3200ed562439556be508a42e4202f66b55f09c9abeed06307d57\",\"pubkey\":\"21335073401a310cc9179fe3a77e9666710cfdf630dfd840f972c183a244b1ad\",\"created_at\":1728921073,\"kind\":9734,\"tags\":[[\"p\",\"460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c\"],[\"e\",\"58f22b06a219f92fb535c71f7098e076751cf2798208acabbbdefbcb950585f5\"],[\"amount\",\"42000\"],[\"relays\",\"wss://christpill.nostr1.com\",\"wss://relay.nostr.bg\",\"wss://relay.noderunners.network\",\"wss://relay.nostrplebs.com\",\"wss://relay.utxo.one\",\"wss://filter.nostr.wine\",\"wss://pyramid.fiatjaf.com\",\"wss://wot.utxo.one\",\"wss://relay.mostr.pub\",\"wss://relay.nostr.band\",\"wss://relay.primal.net\",\"wss://relay.snort.social\",\"wss://purplepag.es\",\"wss://wot.nostr.party\",\"wss://catstrr.swarmstr.com\",\"wss://nostr-relay.derekross.me\",\"wss://relay.damus.io\",\"wss://nos.lol\",\"wss://nostr.wine\",\"wss://relay.bitcoinpark.com\",\"wss://sendit.nosflare.com\",\"wss://nostrelites.org\",\"wss://relay.momostr.pink\",\"ws://localhost:4869\"]],\"content\":\"Onward 🫡\",\"sig\":\"82333a70103b6541f125ac64379a32ad0e9a51eb2769cf899c88df8406bdfa339693d530e640d1c701f4d9d7d847806e1ee2118655b7ba3f9795ce56747eff1b\"}"),
                    ),
                content = "Onward \uD83E\uDEE1",
                sig = "81d08765524bd8b774585a57c76d25b1d2c09eaa23efcb075226ba8b64f42e3d9d9403e5edf888e0e58702a24abc084259e21b97e24a275021c5e36186c65f0c",
            )

        val old = EventHasherSerializer.makeJsonForId(event.pubKey, event.createdAt, event.kind, event.tags, event.content)
        val new = EventHasherSerializer.fastMakeJsonForId(event.pubKey, event.createdAt, event.kind, event.tags, event.content)

        println(old)
        println(String(new))

        assertEquals(old.toByteArray().joinToString(), new.joinToString())
        assertTrue(event.verifySignature())
        assertTrue(event.verifyId())
    }
}
