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
package com.vitorpamplona.quartz.nip01Core.store.sqlite

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import org.junit.Test

class FilterMatcherTest : BaseDBTest() {
    val id = "98b574c3527f0ffb30b7271084e3f07480733c7289f8de424d29eae82e36c758"
    val pubkey = "46fcbe3065eaf1ae7811465924e48923363ff3f526bd6f73d7c184b16bd8ce4d"
    val createdAt: Long = 1683596206
    val kind = 1

    val pTag1 = "22aa81510ee63fe2b16cae16e0921f78e9ba9882e2868e7e63ad6d08ae9b5954"
    val pTag2 = "3f770d65d3a764a9c5cb503ae123e62ec7598ad035d836e2a810f3877a745b24"
    val pTag3 = "ec4d241c334311b3a304433ee3442be29d0e88e7ec19b85edf2bba29b93565e2"
    val pTag4 = "0fe0b18b4dbf0e0aa40fcd47209b2a49b3431fc453b460efcf45ca0bd16bd6ac"
    val pTag5 = "8c0da4862130283ff9e67d889df264177a508974e2feb96de139804ea66d6168"
    val pTag6 = "63fe6318dc58583cfe16810f86dd09e18bfd76aabc24a0081ce2856f330504ed"
    val pTag7 = "4523be58d395b1b196a9b8c82b038b6895cb02b683d0c253a955068dba1facd0"
    val pTag8 = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"

    val rootETag = "27ac621d7dc4a932e1a79f984308e7d20656dd6fddb2ce9cdfcb6a67b9a7bcc3"
    val replyETag = "be7245af96210a0dd048cab4ad38e52dbd6c09a53ea21a7edb6be8898e5727cc"

    val note =
        Event(
            id,
            pubkey,
            createdAt,
            kind,
            arrayOf(
                arrayOf("e", rootETag, "", "root"),
                arrayOf("e", replyETag, "", "reply"),
                arrayOf("p", pTag1),
                arrayOf("p", pTag1),
                arrayOf("p", pTag2),
                arrayOf("p", pTag3),
                arrayOf("p", pTag4),
                arrayOf("p", pTag5),
                arrayOf("p", pTag6),
                arrayOf("p", pTag7),
                arrayOf("p", pTag8),
            ),
            "Astral:\n\nhttps://void.cat/d/A5Fba5B1bcxwEmeyoD9nBs.webp\n\nIris:\n\nhttps://void.cat/d/44hTcVvhRps6xYYs99QsqA.webp\n\nSnort:\n\nhttps://void.cat/d/4nJD5TRePuQChM5tzteYbU.webp\n\nAmethyst agrees with Astral which I suspect are both wrong. nostr:npub13sx6fp3pxq5rl70x0kyfmunyzaa9pzt5utltjm0p8xqyafndv95q3saapa nostr:npub1v0lxxxxutpvrelsksy8cdhgfux9l6a42hsj2qzquu2zk7vc9qnkszrqj49 nostr:npub1g53mukxnjkcmr94fhryzkqutdz2ukq4ks0gvy5af25rgmwsl4ngq43drvk nostr:npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z ",
            "4aa5264965018fa12a326686ad3d3bd8beae3218dcc83689b19ca1e6baeb791531943c15363aa6707c7c0c8b2d601deca1f20c32078b2872d356cdca03b04cce",
        )

    @Test
    fun matchIds() =
        forEachDB { db ->
            db.insert(note)

            db.assertQuery(note, Filter(ids = listOf(id)))
            db.assertQuery(note, Filter(ids = listOf(id, rootETag)))
            db.assertQuery(null, Filter(ids = listOf(rootETag)))
        }

    @Test
    fun matchPubkeys() =
        forEachDB { db ->
            db.insert(note)

            db.assertQuery(note, Filter(authors = listOf(pubkey)))
            db.assertQuery(note, Filter(authors = listOf(pubkey, rootETag)))
            db.assertQuery(null, Filter(authors = listOf(rootETag)))
        }

    @Test
    fun matchTags() =
        forEachDB { db ->
            db.insert(note)

            db.assertQuery(note, Filter(tags = mapOf("p" to listOf(pTag1))))
            db.assertQuery(note, Filter(tags = mapOf("p" to listOf(pTag1, pTag2, pTag3))))
            db.assertQuery(note, Filter(tags = mapOf("p" to listOf(pTag1, id))))
            db.assertQuery(null, Filter(tags = mapOf("p" to listOf(id))))
        }

    @Test
    fun matchDualTags() =
        forEachDB { db ->
            db.insert(note)

            db.assertQuery(note, Filter(tags = mapOf("p" to listOf(pTag1), "e" to listOf(rootETag))))
            db.assertQuery(note, Filter(tags = mapOf("p" to listOf(pTag1, pTag2, pTag3), "e" to listOf(rootETag, replyETag))))
            db.assertQuery(note, Filter(tags = mapOf("p" to listOf(pTag1, id), "e" to listOf(rootETag, replyETag))))
            db.assertQuery(note, Filter(tags = mapOf("p" to listOf(pTag1, id), "e" to listOf(rootETag, pubkey))))
            db.assertQuery(null, Filter(tags = mapOf("p" to listOf(pTag1, id), "e" to listOf(id, pubkey))))
            db.assertQuery(null, Filter(tags = mapOf("p" to listOf(id), "e" to listOf(rootETag))))
        }

    @Test
    fun matchAllTags() =
        forEachDB { db ->
            db.insert(note)

            db.assertQuery(note, Filter(tagsAll = mapOf("p" to listOf(pTag1))))
            db.assertQuery(note, Filter(tagsAll = mapOf("p" to listOf(pTag1, pTag2, pTag3))))
            db.assertQuery(null, Filter(tagsAll = mapOf("p" to listOf(pTag1, id))))
            db.assertQuery(null, Filter(tagsAll = mapOf("p" to listOf(id))))
        }

    @Test
    fun matchDualAllTags() =
        forEachDB { db ->
            db.insert(note)

            db.assertQuery(note, Filter(tagsAll = mapOf("p" to listOf(pTag1), "e" to listOf(rootETag))))
            db.assertQuery(note, Filter(tagsAll = mapOf("p" to listOf(pTag1, pTag2, pTag3), "e" to listOf(rootETag, replyETag))))
            db.assertQuery(null, Filter(tagsAll = mapOf("p" to listOf(pTag1, id), "e" to listOf(rootETag, replyETag))))
            db.assertQuery(null, Filter(tagsAll = mapOf("p" to listOf(pTag1, id), "e" to listOf(rootETag, pubkey))))
            db.assertQuery(null, Filter(tagsAll = mapOf("p" to listOf(pTag1, id), "e" to listOf(id, pubkey))))
            db.assertQuery(null, Filter(tagsAll = mapOf("p" to listOf(id), "e" to listOf(rootETag))))
        }

    @Test
    fun matchKinds() =
        forEachDB { db ->
            db.insert(note)

            db.assertQuery(note, Filter(kinds = listOf(kind)))
            db.assertQuery(note, Filter(kinds = listOf(kind, 1221)))
            db.assertQuery(null, Filter(kinds = listOf(1221)))
        }

    @Test
    fun matchSince() =
        forEachDB { db ->
            db.insert(note)

            db.assertQuery(note, Filter(since = createdAt))
            db.assertQuery(note, Filter(since = createdAt - 1))
            db.assertQuery(null, Filter(since = createdAt + 1))
        }

    @Test
    fun matchUntil() =
        forEachDB { db ->
            db.insert(note)

            db.assertQuery(note, Filter(until = createdAt))
            db.assertQuery(note, Filter(until = createdAt + 1))
            db.assertQuery(null, Filter(until = createdAt - 1))
        }
}
