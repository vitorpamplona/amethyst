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
package com.vitorpamplona.quartz.concord.cord02Community

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * CORD-02 §8 wire conformance, pinned to bytes produced by the reference client's own serializer
 * (Armada `src/concord/lib/listFrag.ts` @ 7588884, run under Node against the same fixtures).
 * Byte identity is the contract: identical state must fragment and serialize identically across
 * implementations, or the canonical-bytes tie-break flaps between two clients' republishes.
 */
class ConcordListFragmentsTest {
    private val json = Json

    private fun parse(s: String) = json.parseToJsonElement(s).jsonObject

    /** Every §8 rule once: seed omitted after a rename, kept across a refounding with current's cosmetics, a tombstoned entry dropped, a re-join kept, unknown keys sorted and untouched. */
    private val smallInput =
        """{"vendor/flag":{"z":1,"a":[2,{"y":1,"b":2}]},"entries":[{"community_id":"1a960f3f84cfe558f94489b844cb38332ff466891b2e77a543f3e96c62634657","seed":{"co""" +
            """mmunity_id":"1a960f3f84cfe558f94489b844cb38332ff466891b2e77a543f3e96c62634657","owner":"f2047532a0e8f1ca30e2391636330b1f05768679a7d9e78191d66ef6919662""" +
            """3e","owner_salt":"ba76992a6079074311285334177ddda598e8d8f9bae4df70875886d5229c26ac","community_root":"82e42e2dc2fc1a7957f3b625f5ec2cfbafe7c2546d6c92db""" +
            """dc239953c0333bec","root_epoch":0,"control_pk":"840aa4c586fff4d34e8ffb6bcf73acf4d5e033273934bfb980e899f51aa2fd4a","channels":[],"relays":["wss://relay.""" +
            """example.com","wss://r0.example"],"name":"Gone"},"current":{"community_id":"1a960f3f84cfe558f94489b844cb38332ff466891b2e77a543f3e96c62634657","owner":"""" +
            """f2047532a0e8f1ca30e2391636330b1f05768679a7d9e78191d66ef69196623e","owner_salt":"ba76992a6079074311285334177ddda598e8d8f9bae4df70875886d5229c26ac","com""" +
            """munity_root":"82e42e2dc2fc1a7957f3b625f5ec2cfbafe7c2546d6c92dbdc239953c0333bec","root_epoch":0,"control_pk":"840aa4c586fff4d34e8ffb6bcf73acf4d5e033273""" +
            """934bfb980e899f51aa2fd4a","channels":[],"relays":["wss://relay.example.com","wss://r0.example"],"name":"Gone"},"added_at":1719800000002},{"community_id""" +
            """":"5ec89d515d1ab8e34fcadabc030883587429fbb00092d573b0d7f8c3679dc705","seed":{"community_id":"5ec89d515d1ab8e34fcadabc030883587429fbb00092d573b0d7f8c36""" +
            """79dc705","owner":"00155506969316836e9c4649c39eb76e244c720731a5ea708709ff3bcb3212a8","owner_salt":"51e0ed5c000f3205a88bca9498b39922f08def28b2da77f063cf""" +
            """88b9708607a0","community_root":"aea3ef4992ed46d0190f47392d69a647b28e1b57260698c3462afa76f53fd428","root_epoch":0,"control_pk":"ee8bb460e7251e4194e7b5c""" +
            """8a56a7eaeb9e3f7e7aabc834bf2f53a081868fd28","channels":[],"relays":["wss://relay.example.com","wss://r1.example"],"name":"Back"},"current":{"community_""" +
            """id":"5ec89d515d1ab8e34fcadabc030883587429fbb00092d573b0d7f8c3679dc705","owner":"00155506969316836e9c4649c39eb76e244c720731a5ea708709ff3bcb3212a8","own""" +
            """er_salt":"51e0ed5c000f3205a88bca9498b39922f08def28b2da77f063cf88b9708607a0","community_root":"aea3ef4992ed46d0190f47392d69a647b28e1b57260698c3462afa76""" +
            """f53fd428","root_epoch":0,"control_pk":"ee8bb460e7251e4194e7b5c8a56a7eaeb9e3f7e7aabc834bf2f53a081868fd28","channels":[],"relays":["wss://relay.example.""" +
            """com","wss://r1.example"],"name":"Back"},"added_at":1722500000000},{"community_id":"5ef38335d3bbd0a5d0241fdcdd02d600170507cf47b2249d7de9e74b119623f4","""" +
            """seed":{"community_id":"5ef38335d3bbd0a5d0241fdcdd02d600170507cf47b2249d7de9e74b119623f4","owner":"713a34a3c313be426d15b5c62ca9c115a1fa6a3a1b51a8f4c6e3""" +
            """74b001a45310","owner_salt":"dbc4579ae2b3ab293213f42bb852706ea995c3b5c3987f8aa9faae5004acb3cf","community_root":"6f432a684ce828f64eee716b22121f3c6eccda""" +
            """6752dd1c2e5413c9272d12d714","root_epoch":1,"control_pk":"bb30490c32fa152d1d7ed3135f7106626a0cf6b8c78d0664f762a25287a04f8c","channels":[{"id":"e83a3324""" +
            """ddbbbcecac7111372041ea2744331b22301a97b834c8db26f9493c71","key":"f9ee91030abe276e839e1e790bbcc68777610df7f4200f75dd5d3a01a4ab2f89","epoch":1,"name":"s""" +
            """taff-Stale"}],"relays":["wss://relay.example.com","wss://r2.example"],"name":"Stale"},"current":{"community_id":"5ef38335d3bbd0a5d0241fdcdd02d60017050""" +
            """7cf47b2249d7de9e74b119623f4","owner":"713a34a3c313be426d15b5c62ca9c115a1fa6a3a1b51a8f4c6e374b001a45310","owner_salt":"dbc4579ae2b3ab293213f42bb852706e""" +
            """a995c3b5c3987f8aa9faae5004acb3cf","community_root":"4e62eea03e8bc82ee7871fc927b8a0768e39116a2b9ad8cc9ab0c1e2c40c15a4","root_epoch":3,"control_pk":"b22""" +
            """cb88bb1cfbb8c4f8697fb982e79c22065e4c5e8afc9336b8da44c550f03a6","channels":[{"id":"e83a3324ddbbbcecac7111372041ea2744331b22301a97b834c8db26f9493c71","k""" +
            """ey":"e21fda697ca9afaf39201db8e25ae77f4bd32c69b4a7db8e5214a349f3e404df","epoch":3,"name":"staff-Fresh"}],"relays":["wss://relay.example.com","wss://r2.""" +
            """example"],"name":"Fresh","control_root":"170ef9cce8cce1cacb0fa729f43db38756632e3b5e134408f85bb7d3163e41fd"},"added_at":1719800000001,"held_roots":[{"e""" +
            """poch":1,"key":"82f3e9c695dc6b8d1b11818d5701919e286de8d47f7c3eb3100c485f79e57828"}]},{"community_id":"a8e2754881acda66e47dc5a810d0f16a384742ff5b0022825""" +
            """b5cd915382bdf65","seed":{"community_id":"a8e2754881acda66e47dc5a810d0f16a384742ff5b0022825b5cd915382bdf65","owner":"1557f949eb074ee1de813d3598b394ea65""" +
            """4e9066ba7dd5ffe290848c31a8c9c8","owner_salt":"dc90cf07de907ccc64636ceddb38e552a1a0d984743b1f36a447b73877012c39","community_root":"e6e642c51a2df47d476e""" +
            """8961b0a9a9db2bef4116761960ac74c19cb7c46fb397","root_epoch":0,"control_pk":"e412e33f694277b150dbdd801f378cc1886769de7a398f2b06e3713616c5133e","channels""" +
            """":[{"id":"797e5887b3e390bc65acb5a81c47d1fe418c9bc160a4adb71fb31fa18981bc18","key":"b76205818bb9254a3af1e9900cdb486617b60265c4e21e600d6bbcd979ac3389","""" +
            """epoch":0,"name":"staff-Old Name"}],"relays":["wss://relay.example.com","wss://r1.example"],"name":"Old Name"},"current":{"community_id":"a8e2754881acd""" +
            """a66e47dc5a810d0f16a384742ff5b0022825b5cd915382bdf65","owner":"1557f949eb074ee1de813d3598b394ea654e9066ba7dd5ffe290848c31a8c9c8","owner_salt":"dc90cf07""" +
            """de907ccc64636ceddb38e552a1a0d984743b1f36a447b73877012c39","community_root":"e6e642c51a2df47d476e8961b0a9a9db2bef4116761960ac74c19cb7c46fb397","root_ep""" +
            """och":0,"control_pk":"e412e33f694277b150dbdd801f378cc1886769de7a398f2b06e3713616c5133e","channels":[{"id":"797e5887b3e390bc65acb5a81c47d1fe418c9bc160a4""" +
            """adb71fb31fa18981bc18","key":"b76205818bb9254a3af1e9900cdb486617b60265c4e21e600d6bbcd979ac3389","epoch":0,"name":"staff-New Name"}],"relays":["wss://re""" +
            """lay.example.com","wss://r1.example"],"name":"New Name"},"added_at":1719800000000,"invite_ref":"naddr1xyz#frag"}],"tombstones":[{"community_id":"1a960f""" +
            """3f84cfe558f94489b844cb38332ff466891b2e77a543f3e96c62634657","removed_at":1722400000000,"vendor/why":"left"},{"community_id":"5ec89d515d1ab8e34fcadabc0""" +
            """30883587429fbb00092d573b0d7f8c3679dc705","removed_at":1722400000000}]}"""

    private val smallExpected =
        """{"frags":1,"entries":[{"community_id":"XsidUV0auONPytq8AwiDWHQp-7AAktVzsNf4w2edxwU","current":{"owner":"ABVVBpaTFoNunEZJw563biRMcgcxpepwhwn_O8syEqg","""" +
            """owner_salt":"UeDtXAAPMgWoi8qUmLOZIvCN7yiy2nfwY8-IuXCGB6A","community_root":"rqPvSZLtRtAZD0c5LWmmR7KOG1cmBpjDRir6dvU_1Cg","root_epoch":0,"control_pk":"""" +
            """7ou0YOclHkGU57XIpWp-rrnj9-eqvINL8vU6CBho_Sg","relays":["wss://relay.example.com","wss://r1.example"],"name":"Back"},"added_at":1722500000000},{"commun""" +
            """ity_id":"XvODNdO70KXQJB_c3QLWABcFB89HsiSdfennSxGWI_Q","seed":{"owner":"cTo0o8MTvkJtFbXGLKnBFaH6ajobUaj0xuN0sAGkUxA","owner_salt":"28RXmuKzqykyE_QruFJw""" +
            """bqmVw7XDmH-KqfquUASss88","community_root":"b0MqaEzoKPZO7nFrIhIfPG7M2mdS3RwuVBPJJy0S1xQ","root_epoch":1,"control_pk":"uzBJDDL6FS0dftMTX3EGYmoM9rjHjQZk9""" +
            """2KiUoegT4w","channels":[{"id":"6DozJN27vOyscRE3IEHqJ0QzGyIwGpe4NMjbJvlJPHE","key":"-e6RAwq-J26Dnh55C7zGh3dhDff0IA913V06AaSrL4k","epoch":1,"name":"staf""" +
            """f-Fresh"}],"relays":["wss://relay.example.com","wss://r2.example"],"name":"Fresh"},"current":{"owner":"cTo0o8MTvkJtFbXGLKnBFaH6ajobUaj0xuN0sAGkUxA","o""" +
            """wner_salt":"28RXmuKzqykyE_QruFJwbqmVw7XDmH-KqfquUASss88","community_root":"TmLuoD6LyC7nhx_JJ7igdo45EWormtjMmrDB4sQMFaQ","root_epoch":3,"control_pk":"s""" +
            """iy4i7HPu4xPhpf7mC55wiBl5MXor8kza42kTFUPA6Y","control_root":"Fw75zOjM4crLD6cp9D2zh1ZjLjteE0QI-Fu30xY-Qf0","channels":[{"id":"6DozJN27vOyscRE3IEHqJ0QzGy""" +
            """IwGpe4NMjbJvlJPHE","key":"4h_aaXypr685IB244lrnf0vTLGm0p9uOUhSjSfPkBN8","epoch":3,"name":"staff-Fresh"}],"relays":["wss://relay.example.com","wss://r2.""" +
            """example"],"name":"Fresh"},"added_at":1719800000001,"held_roots":[{"epoch":1,"key":"82f3e9c695dc6b8d1b11818d5701919e286de8d47f7c3eb3100c485f79e57828"}]""" +
            """},{"community_id":"qOJ1SIGs2mbkfcWoENDxajhHQv9bACKCW1zZFTgr32U","current":{"owner":"FVf5SesHTuHegT01mLOU6mVOkGa6fdX_4pCEjDGoycg","owner_salt":"3JDPB96""" +
            """QfMxkY2zt2zjlUqGg2YR0Ox82pEe3OHcBLDk","community_root":"5uZCxRot9H1HbolhsKmp2yvvQRZ2GWCsdMGct8Rvs5c","root_epoch":0,"control_pk":"5BLjP2lCd7FQ292AHzeM""" +
            """wYhnad56OY8rBuNxNhbFEz4","channels":[{"id":"eX5Yh7PjkLxlrLWoHEfR_kGMm8FgpK23H7MfoYmBvBg","key":"t2IFgYu5JUo68emQDNtIZhe2AmXE4h5gDWu82XmsM4k","epoch":0""" +
            ""","name":"staff-New Name"}],"relays":["wss://relay.example.com","wss://r1.example"],"name":"New Name"},"added_at":1719800000000,"invite_ref":"naddr1xyz""" +
            """#frag"}],"tombstones":[{"community_id":"GpYPP4TP5Vj5RIm4RMs4My_0ZokbLnelQ_PpbGJjRlc","removed_at":1722400000000,"vendor/why":"left"},{"community_id":"""" +
            """XsidUV0auONPytq8AwiDWHQp-7AAktVzsNf4w2edxwU","removed_at":1722400000000}],"vendor/flag":{"a":[2,{"b":2,"y":1}],"z":1}}"""

    @Test
    fun smallFixtureMatchesTheReferenceBytes() {
        assertEquals(listOf(smallExpected), ConcordListFragments.pack(parse(smallInput)))
    }

    @Test
    fun decodingTheReferenceBytesAndRepackingIsStable() {
        val decoded = ConcordListFragments.decodeFragment(smallExpected)
        assertEquals(1, decoded.frags)
        assertEquals(listOf(smallExpected), ConcordListFragments.pack(decoded.doc))
    }

    @Test
    fun decodeRestoresHexAndInheritsTheCommunityId() {
        val doc = ConcordListFragments.decodeFragment(smallExpected).doc
        val first = doc["entries"]!!.jsonArray[0].jsonObject
        val cid = first["community_id"]!!.jsonPrimitive.content
        assertEquals(64, cid.length)
        val current = first["current"]!!.jsonObject
        assertEquals(cid, current["community_id"]!!.jsonPrimitive.content)
        assertEquals(64, current["owner"]!!.jsonPrimitive.content.length)
        // An absent seed reads as equal to current.
        assertEquals(current, first["seed"])
    }

    @Test
    fun unknownFieldsKeepTheirAuthorsSpelling() {
        // held_roots is not a field §8 names, so its hex key stays hex on the wire.
        assertTrue(smallExpected.contains("\"held_roots\":[{\"epoch\":1,\"key\":\"82f3e9c695dc6b8d1b11818d5701919e286de8d47f7c3eb3100c485f79e57828\"}]"))
        val out = ConcordListFragments.pack(ConcordListFragments.decodeFragment(smallExpected).doc).single()
        assertTrue(out.contains("82f3e9c695dc6b8d1b11818d5701919e286de8d47f7c3eb3100c485f79e57828"))
    }

    @Test
    fun base64urlIsUnpaddedAndCaseSensitive() {
        val hex = "a8e2754881acda66e47dc5a810d0f16a384742ff5b0022825b5cd915382bdf65"
        val b64 = ConcordListFragments.hexToB64(hex)
        assertEquals(43, b64.length)
        assertFalse(b64.contains('='))
        assertEquals(hex, ConcordListFragments.b64ToHex(b64))
        // Not 32 bytes either way: passes through untouched.
        assertEquals("wss://relay", ConcordListFragments.hexToB64("wss://relay"))
        assertEquals("short", ConcordListFragments.b64ToHex("short"))
    }

    private fun h(s: String) = sha256(s.encodeToByteArray()).toHexKey()

    private fun mat(
        cid: String,
        i: Int,
        epoch: Int,
        name: String,
        withChan: Boolean,
    ): JsonObject =
        buildJsonObject {
            put("community_id", cid)
            put("owner", h("owner$i"))
            put("owner_salt", h("salt$i"))
            put("community_root", h("root$i:$epoch"))
            put("root_epoch", epoch)
            put("control_pk", h("cpk$i:$epoch"))
            put(
                "channels",
                buildJsonArray {
                    if (withChan) {
                        add(
                            buildJsonObject {
                                put("id", h("chan$i"))
                                put("key", h("key$i:$epoch"))
                                put("epoch", epoch)
                                put("name", "staff-$name")
                            },
                        )
                    }
                },
            )
            put(
                "relays",
                buildJsonArray {
                    add(JsonPrimitive("wss://relay.example.com"))
                    add(JsonPrimitive("wss://r${i % 3}.example"))
                },
            )
            put("name", name)
        }

    private fun bigList(): JsonObject {
        val entries =
            (0 until 300).map { i ->
                val cid = h("big$i")
                buildJsonObject {
                    put("community_id", cid)
                    put("seed", mat(cid, i, 0, "Community $i", i % 4 == 0))
                    put("current", mat(cid, i, i % 3, "Community $i", i % 4 == 0))
                    put("added_at", 1719800000000L + i)
                }
            }
        val tombs =
            (0 until 40).map { i ->
                buildJsonObject {
                    put("community_id", h("gone$i"))
                    put("removed_at", 1722400000000L + i)
                }
            }
        return buildJsonObject {
            put("entries", JsonArray(entries))
            put("tombstones", JsonArray(tombs))
        }
    }

    @Test
    fun largeListFragmentsExactlyLikeTheReference() {
        val frags = ConcordListFragments.pack(bigList())
        assertEquals(
            listOf(
                "f25fb53425472f4249e5801192d05a4fa589d58e8fa420a2ea0489c99c0f3acc",
                "9470fb19cf3fa850b737280bd11be99e599ea9670932a5cf5fd6bb305f9738c4",
                "f73480bbce3a92ee651a04373197bf70116f6cf1db6d99686ba3596b1030f942",
                "c3f6318681a9a1fbcd8e50129b7678b0df5e7cf59ef6527d5bd6b485435d865e",
                "d66d7973f215a3dd2b44b7c6760cd21955baaa6687e02accaa05f708660e0698",
                "35d3b110eb57dd2aa116d0faee8d3858a4608d7a1d5d681775a077671eae13ac",
            ),
            frags.map { sha256(it.encodeToByteArray()).toHexKey() },
        )
        for (f in frags) {
            assertTrue(ConcordListFragments.projectedEventBytes(f.encodeToByteArray().size) <= ConcordListFragments.PACK_TARGET_BYTES)
            assertEquals(6, ConcordListFragments.decodeFragment(f).frags)
        }
    }

    // ---- merges ----------------------------------------------------------------------------

    private fun entry(
        cid: String,
        seed: JsonObject,
        current: JsonObject,
        addedAt: Long,
    ) = buildJsonObject {
        put("community_id", cid)
        put("seed", seed)
        put("current", current)
        put("added_at", addedAt)
    }

    private fun doc(
        entries: List<JsonObject> = emptyList(),
        tombstones: List<JsonObject> = emptyList(),
    ) = JsonObject(mapOf("entries" to JsonArray(entries), "tombstones" to JsonArray(tombstones)))

    private fun tomb(
        cid: String,
        at: Long,
    ) = buildJsonObject {
        put("community_id", cid)
        put("removed_at", at)
    }

    @Test
    fun seedMovesBackwardAndCurrentForward() {
        val cid = h("m")
        val a = doc(listOf(entry(cid, mat(cid, 1, 1, "A", false), mat(cid, 1, 2, "A", false), 10)))
        val b = doc(listOf(entry(cid, mat(cid, 1, 0, "B", false), mat(cid, 1, 3, "B", false), 20)))
        for (merged in listOf(ConcordListFragments.mergeDocs(a, b), ConcordListFragments.mergeDocs(b, a))) {
            val e = merged["entries"]!!.jsonArray.single().jsonObject
            assertEquals("0", e["seed"]!!.jsonObject["root_epoch"]!!.jsonPrimitive.content)
            assertEquals("3", e["current"]!!.jsonObject["root_epoch"]!!.jsonPrimitive.content)
            assertEquals("20", e["added_at"]!!.jsonPrimitive.content)
        }
    }

    @Test
    fun oneTombstonePerCommunityTheLaterWins() {
        val cid = h("t")
        val merged = ConcordListFragments.mergeDocs(doc(tombstones = listOf(tomb(cid, 5))), doc(tombstones = listOf(tomb(cid, 9))))
        assertEquals(
            "9",
            merged["tombstones"]!!
                .jsonArray
                .single()
                .jsonObject["removed_at"]!!
                .jsonPrimitive.content,
        )
    }

    @Test
    fun aStaleFragmentCannotResurrectALeave() {
        val cid = h("x")
        val joined = doc(listOf(entry(cid, mat(cid, 1, 0, "X", false), mat(cid, 1, 0, "X", false), 100)))
        val left = doc(tombstones = listOf(tomb(cid, 200)))
        val merged = ConcordListFragments.mergeDocs(left, joined)
        assertEquals(emptyList(), ConcordCommunityList.decodeDocument(merged).entries)
        // And the packed List carries the tombstone alone.
        val packed = ConcordListFragments.decodeFragment(ConcordListFragments.pack(merged).single()).doc
        assertEquals(0, packed["entries"]!!.jsonArray.size)
        assertEquals(1, packed["tombstones"]!!.jsonArray.size)
    }

    // ---- fragment sets ---------------------------------------------------------------------

    private fun frag(
        frags: Int,
        entries: List<JsonObject> = emptyList(),
        tombstones: List<JsonObject> = emptyList(),
    ) = ConcordListFragments.serializeFragment(
        frags,
        entries.map { ConcordListFragments.entryToWire(it) },
        tombstones.mapNotNull { ConcordListFragments.tombstoneToWire(it) },
    )

    private fun membership(i: Int): JsonObject {
        val cid = h("member$i")
        return entry(cid, mat(cid, i, 0, "M$i", false), mat(cid, i, 0, "M$i", false), 1000L + i)
    }

    @Test
    fun theNewestFragmentDeclaresTheCountAndATieGoesLarger() {
        val set =
            ConcordListFragmentSet.of(
                listOf(
                    ConcordListFragmentSet.Copy(0, 100, "a", frag(2, listOf(membership(0)))),
                    ConcordListFragmentSet.Copy(1, 100, "b", frag(3, listOf(membership(1)))),
                ),
            )
        assertEquals(3, set.declared)
        assertFalse(set.complete, "index 2 is unseen")
        assertEquals(2, ConcordCommunityList.decodeDocument(set.doc).entries.size)
    }

    @Test
    fun anUnreadableHeadIsAMissingIndexNotAnOlderCopy() {
        val set =
            ConcordListFragmentSet.of(
                listOf(
                    ConcordListFragmentSet.Copy(0, 100, "old", frag(1, listOf(membership(0)))),
                    ConcordListFragmentSet.Copy(0, 200, "new", null),
                ),
            )
        assertTrue(0 in set.unreadable)
        assertTrue(set.held.isEmpty())
    }

    @Test
    fun anUnreadableOnlyFragmentIsNeverOverwritten() {
        // The one fragment on the wire didn't decrypt (a timed-out signer): not "no List".
        val set = ConcordListFragmentSet.of(listOf(ConcordListFragmentSet.Copy(0, 100, "a", null)))
        assertFalse(set.complete)
        assertFalse(set.isEmpty)
        assertFailsWith<ConcordListIncompleteException> { set.planWrites(doc(listOf(membership(0))), now = 1000) }
    }

    @Test
    fun anUnreadableNewerFragmentBlocksARepackThatWouldShrinkTheCount() {
        // Fragment 1 may declare more fragments than readable fragment 0 does; a repack to 1
        // fragment would push its memberships out of range.
        val set =
            ConcordListFragmentSet.of(
                listOf(
                    ConcordListFragmentSet.Copy(0, 100, "a", frag(1, listOf(membership(0)))),
                    ConcordListFragmentSet.Copy(1, 200, "b", null),
                ),
            )
        assertFalse(set.complete)
        val writes = set.planWrites(ConcordListFragments.mergeDocs(set.doc, doc(listOf(membership(5)))), now = 1000)
        assertEquals(listOf(0), writes.map { it.index }, "only a scoped write into the readable fragment")
        assertEquals(1, ConcordListFragments.decodeFragment(writes.single().plaintext).frags)
    }

    @Test
    fun anEntryWithUnencodableMaterialIsSkippedNotFatal() {
        val broken =
            buildJsonObject {
                put("community_id", h("broken"))
                put("current", buildJsonObject { put("name", "no keys") })
                put("added_at", 1)
            }
        val packed = ConcordListFragments.pack(doc(listOf(membership(0), broken))).single()
        assertEquals(
            1,
            ConcordListFragments
                .decodeFragment(packed)
                .doc["entries"]!!
                .jsonArray.size,
        )
    }

    @Test
    fun fragmentsPastTheCountStayDormant() {
        val set =
            ConcordListFragmentSet.of(
                listOf(
                    ConcordListFragmentSet.Copy(0, 300, "a", frag(1, listOf(membership(0)))),
                    ConcordListFragmentSet.Copy(1, 100, "b", frag(2, listOf(membership(1)))),
                ),
            )
        assertEquals(1, set.declared)
        assertTrue(set.complete)
        assertEquals(listOf(h("member0")), ConcordCommunityList.decodeDocument(set.doc).entries.map { it.id })
    }

    @Test
    fun aRepackThatShrinksEmptiesTheDroppedIndices() {
        val set =
            ConcordListFragmentSet.of(
                listOf(
                    ConcordListFragmentSet.Copy(0, 100, "a", frag(2, listOf(membership(0)))),
                    ConcordListFragmentSet.Copy(1, 100, "b", frag(2, listOf(membership(1)))),
                ),
            )
        assertTrue(set.complete)
        val writes = set.planWrites(set.doc, now = 50)
        // Both memberships fit one fragment: 0 is rewritten with frags=1, 1 is emptied — never abandoned.
        assertEquals(listOf(0, 1), writes.map { it.index })
        assertEquals(
            2,
            ConcordListFragments
                .decodeFragment(writes[0].plaintext)
                .doc["entries"]!!
                .jsonArray.size,
        )
        assertEquals(ConcordListFragments.emptyFragment(1), writes[1].plaintext)
        // created_at always climbs past the index's previous copy, even with a clock behind it.
        assertTrue(writes.all { it.createdAt == 101L })
    }

    @Test
    fun anIncompleteListOnlyRewritesTheFragmentHoldingTheChange() {
        val held0 = frag(3, listOf(membership(0)))
        val set =
            ConcordListFragmentSet.of(
                listOf(
                    ConcordListFragmentSet.Copy(0, 100, "a", held0),
                    ConcordListFragmentSet.Copy(2, 100, "c", frag(3, listOf(membership(2)))),
                ),
            )
        assertFalse(set.complete)
        // Leave membership 2: a tombstone lands in fragment 2 only; fragment 1 (unseen) is untouched.
        val next = ConcordListFragments.mergeDocs(set.doc, doc(tombstones = listOf(tomb(h("member2"), 5000))))
        val writes = set.planWrites(next, now = 1000)
        assertEquals(listOf(2), writes.map { it.index })
        val written = ConcordListFragments.decodeFragment(writes.single().plaintext)
        assertEquals(3, written.frags, "a scoped write never changes the count")
        assertEquals(0, written.doc["entries"]!!.jsonArray.size)
        assertEquals(1, written.doc["tombstones"]!!.jsonArray.size)
    }

    @Test
    fun anIncompleteListPutsANewMembershipInTheLowestHeldFragment() {
        val set =
            ConcordListFragmentSet.of(
                listOf(ConcordListFragmentSet.Copy(1, 100, "b", frag(2, listOf(membership(1))))),
            )
        val next = ConcordListFragments.mergeDocs(set.doc, doc(listOf(membership(9))))
        val writes = set.planWrites(next, now = 1000)
        assertEquals(listOf(1), writes.map { it.index })
        assertEquals(
            2,
            ConcordListFragments
                .decodeFragment(writes.single().plaintext)
                .doc["entries"]!!
                .jsonArray.size,
        )
    }

    @Test
    fun anUnchangedListWritesNothing() {
        val set = ConcordListFragmentSet.of(listOf(ConcordListFragmentSet.Copy(0, 100, "a", ConcordListFragments.pack(doc(listOf(membership(0)))).single())))
        assertEquals(emptyList(), set.planWrites(set.doc, now = 1000).map { it.index })
    }

    @Test
    fun theTypedLayerTombstonesOnLeave() {
        val entry = ConcordCommunityList.decodeDocument(doc(listOf(membership(0)))).entries.single()
        val residue = ConcordListResidue.EMPTY.withTombstone(entry.id, 5000)
        val internal = ConcordCommunityList.encodeInternal(emptyList(), residue)
        val back = ConcordCommunityList.decodeDocument(internal)
        assertEquals(emptyList(), back.entries)
        assertEquals(
            "5000",
            back.residue.tombstones
                .single()["removed_at"]!!
                .jsonPrimitive.content,
        )
        // An earlier removal never lowers the tombstone.
        assertEquals(residue, residue.withTombstone(entry.id, 10))
    }
}
