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
package com.vitorpamplona.quartz.nip01Core.metadata

import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.KotlinSerializationMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Real kind-0 events collected from relays that used to lose all or part of the
 * profile. Each is checked through **both** wire parsers — Jackson (the
 * JVM/Android [com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper]
 * actual) and kotlinx serialization (the native actual) — because a profile that
 * renders on one platform and not the other is the harder bug to spot.
 */
class MetadataEventWildProfilesTest {
    /** Runs [assertions] against the event as parsed by each of the two mappers. */
    private fun bothMappers(
        json: String,
        assertions: (label: String, event: MetadataEvent) -> Unit,
    ) {
        assertions("jackson", JacksonMapper.fromJson(json) as MetadataEvent)
        assertions("kotlinx", KotlinSerializationMapper.fromJson(json) as MetadataEvent)
    }

    /**
     * Empty content is a valid, empty profile — someone wiping their metadata —
     * so it must decode into a blank [UserMetadata]. Returning null made this a
     * parse "error" that `LocalCache.consume` dropped, leaving the stale profile
     * in place forever even though a newer replaceable event had arrived.
     */
    @Test
    fun emptyContentIsAnEmptyProfile() {
        val json =
            """
            {
                "id": "3d6f3be9e56aaf0f6e7f013d886f123ff72863df6cc103f2a9d2398221a46a73",
                "pubkey": "bed07edf2bb9690324f7898213a7431586f12088e727c2b722ab7c5b2d80368f",
                "created_at": 1785356310,
                "kind": 0,
                "tags": [],
                "content": "",
                "sig": "2bddad095b8a7dc087cf1c030bf48f93280274d4cb01eb352d1540c0f2d2f4dad84fbd52d8a031c148df735a22e706e29c79d2c956b5e5d9edca49346bb2ba5a"
            }
            """.trimIndent()

        bothMappers(json) { label, event ->
            val meta = event.contactMetaData()
            assertIs<UserMetadata>(meta, "$label: empty content must not be an error")
            assertNull(meta.name, label)
            assertNull(meta.displayName, label)
            assertNull(meta.picture, label)
            assertNull(meta.about, label)
            assertNull(meta.nip05, label)

            assertEquals(emptySet(), event.contactMetadataJson()?.keys, "$label: empty content is an empty json object")
        }
    }

    /**
     * `"nip05":["shoreline@decentnewsroom.com"]` — a string field wrapped in a
     * one-element array. The intended value is unambiguous, so it is recovered
     * instead of dropping the user's NIP-05 verification.
     */
    @Test
    fun singleElementArrayNip05IsRecovered() {
        val json =
            """
            {"id":"642168789c75b50a7f67d0dab7bf4723433a70a7694c63c77eeca03a74af636c","pubkey":"689c44a2c362229489eb3fc5273920469ae3fa6ce01467e07f1c3383097a26b9","created_at":1782219566,"kind":0,"tags":[["display_name","Shoreline Poetry"],["name","shoreline"],["about","Poems and fragments. Inner weather. Ceasing, breaking, flowing, and becoming something new."],["picture","https://image.nostr.build/e98b3341636054b33ccf414390d75d02b721511d5e331ffcac63bacfa56fa1c4.jpg"],["banner","https://images.pexels.com/photos/18558371/pexels-photo-18558371.jpeg"],["lud16","purepug2@primal.net"],["website","https://shoreline.decentnewsroom.com"],["nip05","shoreline@decentnewsroom.com"]],"content":"{\"display_name\":\"Shoreline Poetry\",\"name\":\"shoreline\",\"about\":\"Poems and fragments. Inner weather. Ceasing, breaking, flowing, and becoming something new.\",\"banner\":\"https://images.pexels.com/photos/18558371/pexels-photo-18558371.jpeg\",\"website\":\"https://shoreline.decentnewsroom.com\",\"nip05\":[\"shoreline@decentnewsroom.com\"],\"lud16\":\"purepug2@primal.net\",\"picture\":\"https://image.nostr.build/e98b3341636054b33ccf414390d75d02b721511d5e331ffcac63bacfa56fa1c4.jpg\"}","sig":"fe2a682f4cc407e6de07ff7e68309f757b3a63ba41851c21199ea71067aafd0a27dc874fc575104654d908cb65aba25dd060086c31723f496efb9271ee256560"}
            """.trimIndent()

        bothMappers(json) { label, event ->
            val meta = event.contactMetaData()
            assertIs<UserMetadata>(meta, label)
            assertEquals("shoreline", meta.name, label)
            assertEquals("Shoreline Poetry", meta.displayName, label)
            assertEquals("shoreline@decentnewsroom.com", meta.nip05, "$label: the one-element array must be unwrapped")
            assertEquals("purepug2@primal.net", meta.lud16, label)
            assertEquals("https://shoreline.decentnewsroom.com", meta.website, label)
        }
    }

    /**
     * A profile dumped straight out of some client's internal state: `"nip05":{}`
     * and `"custom_data":{}` plus keys that are not NIP-24 fields at all. The
     * malformed nip05 is dropped, everything else survives.
     */
    @Test
    fun objectNip05AndForeignKeysDoNotDropTheProfile() {
        val json =
            """
            {"id":"dde6cd9661490879de9d5c1db12e1025ef6e429e7f6efb1021f1fda7fa1bc99d","pubkey":"85faee02e65460a46a5e8dbb47d46f0b1c02030aba3da437257e5759a00e0885","created_at":1710229789,"kind":0,"tags":[],"content":"{\"name\":\"krit\",\"about\":\"\",\"display_name\":\"krit\",\"picture\":\"\",\"pubkey\":\"npub1shawuqhx23s2g6j73ka504r0pvwqyqc2hg76gde90et4ngqwpzzsua9kwr\",\"pubkeyHex\":\"85faee02e65460a46a5e8dbb47d46f0b1c02030aba3da437257e5759a00e0885\",\"lastUpdatedAt\":0,\"nip05\":{},\"custom_data\":{},\"isLogin\":false}","sig":"df4ef440b80843e53b9b99e0ebbf1d5f22fbeb4cc8ce8993b3df42a16d96e06b74036f10f3cf62348a4f5124b91afc2ff7d82482289879994b30bb73138a92d9"}
            """.trimIndent()

        bothMappers(json) { label, event ->
            val meta = event.contactMetaData()
            assertIs<UserMetadata>(meta, label)
            assertEquals("krit", meta.name, label)
            assertEquals("krit", meta.displayName, label)
            assertNull(meta.nip05, "$label: an object nip05 has no readable value")

            // The empty strings the client wrote are normalized away on consumption.
            meta.cleanBlankNames()
            assertNull(meta.picture, label)
        }
    }

    /**
     * A Ditto profile: `"birthday":"10-24"` (NIP-24 defines an object), a `fields`
     * array of arrays, and assorted client-private keys. Everything the app reads
     * must survive; the birthday stays dropped because a bare `"10-24"` is
     * ambiguous (MM-DD vs DD-MM) — see [BirthdayTolerantSerializer].
     */
    @Test
    fun stringBirthdayAndExtraFieldsDoNotDropTheProfile() {
        val json =
            """
            {"id":"ed269c23907649461da4b0fe109eed689ed1a562d33873b97ed01496dd02b87c","pubkey":"932614571afcbad4d17a191ee281e39eebbb41b93fac8fd87829622aeb112f4d","created_at":1779416127,"kind":0,"tags":[["client","Ditto","31990:781a1527055f74c1f70230f10384609b34548f8ab6a0a6caa74025827f9fdae5:ditto"],["published_at","1779044138"]],"content":"{\"about\":\"Team Soapbox. Freedom advocate.\",\"banner\":\"https://blossom.ditto.pub/ee0d.jpeg\",\"bot\":false,\"display_name\":\"MK Fain\",\"lud16\":\"mkfain@cash.app\",\"name\":\"MK Fain\",\"nip05\":\"mk@ditto.pub\",\"picture\":\"https://blossom.ditto.pub/cf2b.jpeg\",\"displayName\":\"MK Fain\",\"pubkey\":\"932614571afcbad4d17a191ee281e39eebbb41b93fac8fd87829622aeb112f4d\",\"npub\":\"npub1jvnpg4c6ljadf5t6ry0w9q0rnm4mksde87kglkrc993z46c39axsgq89sc\",\"created_at\":1718637715,\"fields\":[[\"Soapbox\",\"https://soapbox.pub\"],[\"Ditto FAQ\",\"https://ditto.pub/help\"]],\"birthday\":\"10-24\",\"showBirthday\":true,\"client\":\"divine.video\",\"shape\":\"x\"}","sig":"a78ed7b638eb240dbc81ea6565d5de4fb67d970fa391ea69b9757e1c6dfb448c54ea7d41f16802027135c1b6bd729e3889eb27065b432d42660cf1fb186fc8a0"}
            """.trimIndent()

        bothMappers(json) { label, event ->
            val meta = event.contactMetaData()
            assertIs<UserMetadata>(meta, label)
            assertEquals("MK Fain", meta.name, label)
            assertEquals("MK Fain", meta.displayName, label)
            assertEquals("mk@ditto.pub", meta.nip05, label)
            assertEquals("mkfain@cash.app", meta.lud16, label)
            assertEquals(false, meta.bot, label)
            assertEquals("Team Soapbox. Freedom advocate.", meta.about, label)
            assertNull(meta.birthday, "$label: an ambiguous string birthday is dropped, not fatal")
        }
    }
}
