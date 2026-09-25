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
package com.vitorpamplona.quartz.nipXXSql

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.CommandKSerializer
import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.MessageKSerializer
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.AuthMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.server.NostrServer
import com.vitorpamplona.quartz.nip01Core.relay.server.RelaySession
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.EmptyPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.FullAuthPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.IRelayPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.RelayLimits
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * SQL / FETCH / SQL-CLOSE through a real [NostrServer] session: every
 * frame is parsed and serialized by the production JSON path, and the
 * replies are decoded back with [Message.fromJson].
 */
class SqlRelayTest {
    private val alice = NostrSignerSync()
    private val bob = NostrSignerSync()

    private lateinit var dbFile: Path
    private lateinit var store: EventStore
    private val servers = ArrayList<NostrServer>()

    private class Client(
        val session: RelaySession,
        val frames: Channel<String>,
    ) {
        suspend fun send(json: String) = session.receive(json)

        suspend fun next(): Message = Message.fromJson(withTimeout(5_000) { frames.receive() })

        fun pending(): Int {
            var n = 0
            while (frames.tryReceive().isSuccess) n++
            return n
        }
    }

    @BeforeTest
    fun setup() {
        dbFile = Files.createTempFile("nostr-sql-relay-", ".db")
        Files.deleteIfExists(dbFile)
        store = EventStore(dbName = dbFile.toAbsolutePath().toString(), relay = null)
        runBlocking<Unit> {
            repeat(10) { i -> store.insert(alice.sign<Event>(1000L + i, 1, arrayOf(arrayOf("t", "nostr")), "alice $i")) }
            repeat(3) { i -> store.insert(bob.sign<Event>(2000L + i, 1, emptyArray(), "bob $i")) }
            store.insert(bob.sign<Event>(3000L, 1059, arrayOf(arrayOf("p", alice.pubKey)), "sealed"))
        }
    }

    @AfterTest
    fun tearDown() {
        servers.forEach { it.close() }
        listOf("", "-wal", "-shm", "-journal").forEach { Path.of(dbFile.toString() + it).deleteIfExists() }
    }

    private fun server(
        policy: () -> IRelayPolicy = { EmptyPolicy },
        limits: RelayLimits? = null,
        backingStore: IEventStore = store,
    ): NostrServer = NostrServer(store = backingStore, policyBuilder = policy, limits = limits).also { servers.add(it) }

    private fun NostrServer.client(): Client {
        val frames = Channel<String>(Channel.UNLIMITED)
        return Client(connect { frames.trySend(it) }, frames)
    }

    private suspend fun Client.expectClosed(
        id: String,
        prefix: String,
    ): String {
        val msg = assertIs<ClosedMessage>(next())
        assertEquals(id, msg.subId)
        assertTrue(msg.message.startsWith(prefix), msg.message)
        return msg.message
    }

    @Test
    fun pagesThroughAResultWithFetch() =
        runBlocking<Unit> {
            val c = server().client()
            c.send("""["SQL","q1","SELECT content, created_at FROM events WHERE kind = ? ORDER BY created_at",{"params":[1],"page":5}]""")

            val cols = assertIs<SqlColsMessage>(c.next())
            assertEquals(listOf("content", "created_at"), cols.columns)

            val first = assertIs<SqlRowsMessage>(c.next())
            assertEquals("q1", first.queryId)
            assertEquals(listOf("alice 0", 1000L), first.rows[0])
            assertEquals(5, first.rows.size)
            assertEquals(false, first.done)

            c.send("""["FETCH","q1",5]""")
            val second = assertIs<SqlRowsMessage>(c.next())
            assertEquals(5, second.rows.size)
            assertEquals(false, second.done)

            c.send("""["FETCH","q1",100]""")
            val last = assertIs<SqlRowsMessage>(c.next())
            assertEquals(listOf(listOf("bob 0", 2000L), listOf("bob 1", 2001L), listOf("bob 2", 2002L)), last.rows)
            assertTrue(last.done)

            // Exhausted cursors are gone.
            c.send("""["FETCH","q1",1]""")
            c.expectClosed("q1", "error: no such cursor")
        }

    @Test
    fun smallResultsFinishInOneFrame() =
        runBlocking<Unit> {
            val c = server().client()
            c.send("""["SQL","agg","SELECT pubkey = :a AS is_alice, count(*) AS n FROM events GROUP BY 1 ORDER BY 1",{"params":{"a":"${alice.pubKey}"}}]""")
            assertEquals(listOf("is_alice", "n"), assertIs<SqlColsMessage>(c.next()).columns)
            val rows = assertIs<SqlRowsMessage>(c.next())
            assertEquals(listOf(listOf(0L, 4L), listOf(1L, 10L)), rows.rows)
            assertTrue(rows.done)
            assertEquals(0, c.pending())
        }

    @Test
    fun valuesKeepTheirJsonTypes() =
        runBlocking<Unit> {
            val c = server().client()
            c.send("""["SQL","v","SELECT 1, 2.5, 'x', NULL, 1e999, -1e999"]""")
            c.next()
            assertEquals(listOf(listOf(1L, 2.5, "x", null, "Inf", "-Inf")), assertIs<SqlRowsMessage>(c.next()).rows)
        }

    @Test
    fun errorsUseNip01Prefixes() =
        runBlocking<Unit> {
            val c = server().client()
            c.send("""["SQL","a","SELEC 1"]""")
            c.expectClosed("a", "unsupported:")
            c.send("""["SQL","b","SELECT * FROM event_headers"]""")
            c.expectClosed("b", "invalid: no such table")
            c.send("""["SQL","c","SELECT 1; DELETE FROM events"]""")
            c.expectClosed("c", "unsupported:")
            c.send("""["SQL","d","SELECT load_extension('x')"]""")
            c.expectClosed("d", "unsupported: function")
            c.send("""["SQL","e","SELECT nope FROM events"]""")
            c.expectClosed("e", "error:")
            c.send("""["SQL","f","SELECT ?"]""")
            c.expectClosed("f", "invalid: no value for parameter")
        }

    @Test
    fun firstPageDefaultsToTheRelayDefaultLimit() =
        runBlocking<Unit> {
            val c = server(limits = RelayLimits(defaultLimit = 3)).client()
            c.send("""["SQL","d","SELECT id FROM events"]""")
            c.next()
            val page = assertIs<SqlRowsMessage>(c.next())
            assertEquals(3, page.rows.size)
            assertEquals(false, page.done)

            // An explicit page size wins over the default.
            c.send("""["SQL","e","SELECT id FROM events",{"page":5}]""")
            c.next()
            assertEquals(5, assertIs<SqlRowsMessage>(c.next()).rows.size)
        }

    @Test
    fun withoutADefaultLimitTheFirstPageHasEverything() =
        runBlocking<Unit> {
            val c = server().client()
            c.send("""["SQL","all","SELECT id FROM events"]""")
            c.next()
            val page = assertIs<SqlRowsMessage>(c.next())
            assertEquals(14, page.rows.size)
            assertTrue(page.done)
        }

    @Test
    fun sqlFollowsTheSamePolicyAsReq() =
        runBlocking<Unit> {
            val c = server(policy = { FullAuthPolicy("wss://sql.test/".normalizeRelayUrl()) }).client()
            assertIs<AuthMessage>(c.next())
            c.send("""["SQL","p","SELECT 1"]""")
            c.expectClosed("p", "auth-required:")
        }

    @Test
    fun inMemoryStoresAnswerThroughPushdown() =
        runBlocking<Unit> {
            val memory = EventStore(dbName = null, relay = null)
            memory.insert(alice.sign<Event>(5, 1, arrayOf(arrayOf("t", "x")), "m"))
            val c = server(backingStore = memory).client()
            c.send("""["SQL","u","SELECT count(*) FROM tags WHERE name = 't' AND value = 'x'"]""")
            assertEquals(listOf("count(*)"), assertIs<SqlColsMessage>(c.next()).columns)
            assertEquals(listOf(listOf(1L)), assertIs<SqlRowsMessage>(c.next()).rows)
        }

    @Test
    fun reusingAnIdReplacesTheCursor() =
        runBlocking<Unit> {
            val c = server().client()
            c.send("""["SQL","x","SELECT id FROM events",{"page":1}]""")
            c.next()
            assertEquals(false, assertIs<SqlRowsMessage>(c.next()).done)

            c.send("""["SQL","x","SELECT 42"]""")
            c.next()
            assertEquals(listOf(listOf(42L)), assertIs<SqlRowsMessage>(c.next()).rows)
            c.send("""["FETCH","x",1]""")
            c.expectClosed("x", "error: no such cursor")
        }

    @Test
    fun closeAndDisconnectReleaseCursors() =
        runBlocking<Unit> {
            val c = server().client()
            c.send("""["SQL","a","SELECT id FROM events",{"page":1}]""")
            c.next()
            c.next()
            c.send("""["SQL-CLOSE","a"]""")
            c.send("""["FETCH","a",1]""")
            c.expectClosed("a", "error: no such cursor")

            c.send("""["SQL","b","SELECT id FROM events",{"page":1}]""")
            c.next()
            c.next()
            c.session.close()
            assertEquals(0, c.pending())
        }

    @Test
    fun framesRoundTripThroughBothSerializers() {
        val cmds =
            listOf(
                SqlCmd("q", "SELECT ?", params = listOf(1L, 2.5, "s", true, null), pageSize = 7),
                SqlCmd("q", "SELECT :a", named = mapOf("a" to "b")),
                SqlCmd("q", "SELECT 1"),
                FetchCmd("q", 50),
                SqlCloseCmd("q"),
            )
        cmds.forEach { cmd ->
            val json = cmd.toJson()
            val back = Command.fromJson(json)
            assertEquals(json, back.toJson())
            val viaKotlinx = Json.encodeToString(CommandKSerializer, cmd)
            assertEquals(json, viaKotlinx)
            assertEquals(json, Json.decodeFromString(CommandKSerializer, json).toJson())
        }
        val msgs =
            listOf(
                SqlColsMessage("q", listOf("a", "count(*)")),
                SqlRowsMessage("q", listOf(listOf(1L, 2.5, "x", null), listOf(0L, 0.0, "", null)), done = false),
                SqlRowsMessage("q", emptyList(), done = true),
            )
        msgs.forEach { msg ->
            val json = msg.toJson()
            assertEquals(json, Message.fromJson(json).toJson())
            val viaKotlinx = Json.encodeToString(MessageKSerializer, msg)
            assertEquals(json, viaKotlinx)
            assertEquals(json, Json.decodeFromString(MessageKSerializer, json).toJson())
        }
    }
}
