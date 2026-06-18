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

import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip52Calendar.calendar.CalendarEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.test.Test

class SearchTest : BaseDBTest() {
    val signer = NostrSignerSync()

    companion object {
        val profile =
            MetadataEvent(
                id = "490d7439e530423f2540d4f2bdb73a0a2935f3df9e1f2a6f699a140c7db311fe",
                pubKey = "70a9b3c312a6b83e476739bd29d60ca700da1d5b982cbca87b5f3d27d4038d67",
                createdAt = 1740669816,
                tags =
                    arrayOf(
                        arrayOf("alt", "User profile for Vitor"),
                        arrayOf("name", "Vitor"),
                    ),
                content = "{\"name\":\"Vitor\"}",
                sig = "977a6152199f17d103d8d56736ed1b7767054464cf9423d017c01c8cdd2344698f0a5e13da8dff98d01bb1f798837e3b6271e1fd1cac861bb90686f622ae6ef4",
            )

        val comment =
            CommentEvent(
                id = "fecb2ecf61a1433d417a784d10bd1e8ec19a916170a53ca8fb3a15fc666a6592",
                pubKey = "f8ff11c7a7d3478355d3b4d174e5a473797a906ea4aa61aa9b6bc0652c1ea17a",
                createdAt = 1747753115,
                tags =
                    arrayOf(
                        arrayOf("alt", "Reply to geo:drt3n"),
                        arrayOf("I", "geo:drt3n"),
                        arrayOf("I", "geo:drt3"),
                        arrayOf("I", "geo:drt"),
                        arrayOf("I", "geo:dr"),
                        arrayOf("I", "geo:d"),
                        arrayOf("K", "geo"),
                        arrayOf("i", "geo:drt3n"),
                        arrayOf("i", "geo:drt3"),
                        arrayOf("i", "geo:drt"),
                        arrayOf("i", "geo:dr"),
                        arrayOf("i", "geo:d"),
                        arrayOf("k", "geo"),
                    ),
                content = "testing",
                sig = "12070e663272f1227c639fb834eb2122fc7bb995f4c49e55ebb1dfe2135ef7347d44810bacd2e64fd26b8826fd47d2800ce6c3d3b579bb3afe39088ffd4faa60",
            )
    }

    @Test
    fun testTagWithSearch() =
        forEachDB { db ->
            db.store.insertEvent(comment)
            db.store.insertEvent(profile)

            db.assertQuery(null, Filter(search = "testing1"))
            db.assertQuery(comment, Filter(search = "testing"))
            db.assertQuery(comment, Filter(kinds = listOf(CommentEvent.KIND), search = "testing"))
            db.assertQuery(null, Filter(kinds = listOf(TextNoteEvent.KIND), search = "testing"))

            db.store.delete(comment.id)

            db.assertQuery(null, Filter(search = "testing"))
            db.assertQuery(null, Filter(kinds = listOf(CommentEvent.KIND), search = "testing"))

            db.store.insertEvent(comment)

            db.assertQuery(comment, Filter(search = "testing"))
            db.assertQuery(comment, Filter(kinds = listOf(CommentEvent.KIND), search = "testing"))
        }

    @Test
    fun testFtsCleanedUpAfterReplaceableRotation() =
        forEachDB { db ->
            // The fts_foreign_key trigger fires on event_headers DELETE, so
            // when an addressable is superseded its FTS row should also be
            // removed. Otherwise stale content would keep matching searches.
            val time = TimeUtils.now()
            val v1 =
                signer.sign(
                    LongTextNoteEvent.build(
                        "first version uniqalpha",
                        title = "blog title",
                        dTag = "fts-rotation",
                        createdAt = time,
                    ),
                )
            val v2 =
                signer.sign(
                    LongTextNoteEvent.build(
                        "second version uniqbeta",
                        title = "blog title",
                        dTag = "fts-rotation",
                        createdAt = time + 1,
                    ),
                )

            db.store.insertEvent(v1)
            db.assertQuery(v1, Filter(search = "uniqalpha"))

            db.store.insertEvent(v2)
            // v1's FTS row must be gone; v2's content takes over.
            db.assertQuery(null, Filter(search = "uniqalpha"))
            db.assertQuery(v2, Filter(search = "uniqbeta"))
        }

    @Test
    fun testNewlySearchableKinds() =
        forEachDB { db ->
            // CalendarEvent indexes the title tag plus the free-text content.
            val cal =
                signer.sign(
                    CalendarEvent.build(
                        title = "uniqtitle Meetup",
                        content = "annual uniqbody gathering",
                    ),
                )
            // GitRepositoryEvent has an empty content field — its searchable
            // text comes entirely from the name and description tags.
            val repo =
                signer.sign(
                    GitRepositoryEvent.build(
                        name = "uniqname",
                        description = "uniqdesc nostr client",
                    ),
                )

            db.store.insertEvent(cal)
            db.store.insertEvent(repo)

            // Title tag and content are both indexed for the calendar event.
            db.assertQuery(cal, Filter(search = "uniqtitle"))
            db.assertQuery(cal, Filter(search = "uniqbody"))

            // Name and description tags are indexed even with empty content.
            db.assertQuery(repo, Filter(search = "uniqname"))
            db.assertQuery(repo, Filter(search = "uniqdesc"))
            db.assertQuery(repo, Filter(kinds = listOf(GitRepositoryEvent.KIND), search = "uniqdesc"))
        }

    @Test
    fun testProfileJsonFieldsAreSearchable() =
        forEachDB { db ->
            // Kind-0 content is JSON; we parse it and index names, bio, and the
            // addresses people search by (nip05 email, lightning address, URLs).
            val p =
                signer.sign(
                    MetadataEvent.createNew(
                        name = "uniqalice",
                        about = "loves uniqbio and coffee",
                        nip05 = "alice@uniqmail.example",
                        lnAddress = "alice@uniqln.example",
                        website = "https://uniqsite.example",
                    ),
                )
            db.store.insertEvent(p)

            db.assertQuery(p, Filter(search = "uniqalice")) // name
            db.assertQuery(p, Filter(search = "uniqbio")) // about
            db.assertQuery(p, Filter(search = "uniqmail")) // nip05 email
            db.assertQuery(p, Filter(search = "uniqln")) // lightning address
            db.assertQuery(p, Filter(search = "uniqsite")) // website URL
            db.assertQuery(p, Filter(kinds = listOf(MetadataEvent.KIND), search = "uniqalice"))
        }

    @Test
    fun testReindexFullTextSearchRebuildsTheWholeIndex() =
        forEachDB { db ->
            // A calendar event (title tag + content) and a plain note. Both
            // are searchable kinds, but we'll erase the FTS index to mimic
            // events that were stored before their kind became searchable.
            val cal =
                signer.sign(
                    CalendarEvent.build(
                        title = "uniqtitle Meetup",
                        content = "annual uniqbody gathering",
                    ),
                )
            val note = signer.sign(TextNoteEvent.build("reindex uniqnote please", createdAt = TimeUtils.now()))

            db.store.insertEvent(cal)
            db.store.insertEvent(note)

            // Wipe the FTS rows but keep the canonical event rows — this is
            // the state a store ends up in after an upgrade adds search
            // support for a kind that was inserted under the old code.
            db.store.pool.useWriter { db.store.fullTextSearchModule.deleteAll(it) }
            db.store.assertQuery(null, Filter(search = "uniqbody"))
            db.store.assertQuery(null, Filter(search = "uniqnote"))

            // Rebuilding from storage brings every searchable field back.
            db.store.reindexFullTextSearch()
            db.store.assertQuery(cal, Filter(search = "uniqtitle"))
            db.store.assertQuery(cal, Filter(search = "uniqbody"))
            db.store.assertQuery(note, Filter(search = "uniqnote"))

            // Running it again must not duplicate rows (assertQuery expects
            // exactly one match), proving the rebuild starts from a clean slate.
            db.store.reindexFullTextSearch()
            db.store.assertQuery(cal, Filter(search = "uniqbody"))
            db.store.assertQuery(note, Filter(search = "uniqnote"))
        }

    @Test
    fun testChannelJsonFieldsAreSearchable() =
        forEachDB { db ->
            val chan =
                signer.sign(
                    ChannelCreateEvent.build(
                        name = "uniqchan",
                        about = "a uniqtopic discussion",
                        picture = "https://uniqpic.example/c.jpg",
                        relays = null,
                    ),
                )
            db.store.insertEvent(chan)

            db.assertQuery(chan, Filter(search = "uniqchan")) // name
            db.assertQuery(chan, Filter(search = "uniqtopic")) // about
            db.assertQuery(chan, Filter(search = "uniqpic")) // picture URL
        }
}
