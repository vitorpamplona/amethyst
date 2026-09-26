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
 * `NQL` (NIP-FF) through a real [NostrServer] session: every frame is parsed
 * and serialized by the production JSON path, and the replies are decoded
 * back with [Message.fromJson].
 */
class NqlRelayTest {
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
        dbFile = Files.createTempFile("nostr-nql-relay-", ".db")
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

    private suspend fun Client.answer(id: String): NqlResult {
        val msg = assertIs<NqlResultMessage>(next())
        assertEquals(id, msg.queryId)
        return msg.result
    }

    @Test
    fun answersWithTypedColumnsAndRows() =
        runBlocking<Unit> {
            val c = server().client()
            c.send("""["NQL","q1","SELECT content, created_at FROM events WHERE kind = ? ORDER BY created_at LIMIT 2",[1]]""")
            val r = c.answer("q1")
            assertEquals(listOf("content" to NqlType.TEXT, "created_at" to NqlType.INTEGER), r.columns.map { it.name to it.type })
            assertEquals(listOf(listOf("alice 0", 1000L), listOf("alice 1", 1001L)), r.rows)
            assertEquals(false, r.truncated)
            assertEquals(0, c.pending())
        }

    @Test
    fun valuesKeepTheirTypes() =
        runBlocking<Unit> {
            val c = server().client()
            c.send("""["NQL","v","SELECT 1 AS i, 2.0 AS r, 'x' AS t, NULL AS n, 1 < 2 AS b, 1e308 * 10 AS big"]""")
            c.expectClosed("v", "error:")
            c.send("""["NQL","w","SELECT 1 AS i, 2.0 AS r, 'x' AS t, NULL AS n, 1 < 2 AS b, ? AS p",[3.0]]""")
            val r = c.answer("w")
            assertEquals(listOf(NqlType.INTEGER, NqlType.REAL, NqlType.TEXT, NqlType.TEXT, NqlType.BOOLEAN, NqlType.REAL), r.columns.map { it.type })
            assertEquals(listOf(listOf(1L, 2.0, "x", null, true, 3.0)), r.rows)
        }

    @Test
    fun groupsAndParameters() =
        runBlocking<Unit> {
            val c = server().client()
            c.send("""["NQL","agg","SELECT pubkey = ? AS is_alice, count(*) AS n FROM events GROUP BY is_alice ORDER BY n",["${alice.pubKey}"]]""")
            assertEquals(listOf(listOf(false, 4L), listOf(true, 10L)), c.answer("agg").rows)
        }

    @Test
    fun errorsUseNip01Prefixes() =
        runBlocking<Unit> {
            val c = server().client()
            c.send("""["NQL","a","SELEC 1"]""")
            c.expectClosed("a", "invalid:")
            c.send("""["NQL","b","SELECT * FROM event_headers"]""")
            c.expectClosed("b", "invalid: no source named event_headers")
            c.send("""["NQL","c","SELECT 1 AS a; DELETE FROM events"]""")
            c.expectClosed("c", "invalid:")
            c.send("""["NQL","d","SELECT load_extension('x') AS x"]""")
            c.expectClosed("d", "invalid: no function named load_extension")
            c.send("""["NQL","e","SELECT kind / 0 AS x FROM events"]""")
            c.expectClosed("e", "error: division by zero")
            c.send("""["NQL","f","SELECT ? AS x"]""")
            c.expectClosed("f", "invalid:")
        }

    @Test
    fun theRowCapTruncatesInOrder() =
        runBlocking<Unit> {
            val c = server(limits = RelayLimits(maxNqlRows = 3)).client()
            c.send("""["NQL","d","SELECT content FROM events WHERE kind = 1 ORDER BY created_at DESC"]""")
            val r = c.answer("d")
            assertEquals(listOf(listOf("bob 2"), listOf("bob 1"), listOf("bob 0")), r.rows)
            assertTrue(r.truncated)

            c.send("""["NQL","e","SELECT count(*) AS n FROM events"]""")
            assertEquals(false, c.answer("e").truncated)
        }

    @Test
    fun withoutACapEverythingComesBack() =
        runBlocking<Unit> {
            val c = server().client()
            c.send("""["NQL","all","SELECT id FROM events"]""")
            val r = c.answer("all")
            assertEquals(14, r.rows.size)
            assertEquals(false, r.truncated)
        }

    @Test
    fun nqlFollowsTheSamePolicyAsReq() =
        runBlocking<Unit> {
            val c = server(policy = { FullAuthPolicy("wss://nql.test/".normalizeRelayUrl()) }).client()
            assertIs<AuthMessage>(c.next())
            c.send("""["NQL","p","SELECT 1 AS x"]""")
            c.expectClosed("p", "auth-required:")
        }

    @Test
    fun inMemoryStoresAnswerToo() =
        runBlocking<Unit> {
            val memory = EventStore(dbName = null, relay = null)
            memory.insert(alice.sign<Event>(5, 1, arrayOf(arrayOf("t", "x")), "m"))
            val c = server(backingStore = memory).client()
            c.send("""["NQL","u","SELECT count(*) AS n FROM tags WHERE t0 = 't' AND t1 = 'x'"]""")
            assertEquals(listOf(listOf(1L)), c.answer("u").rows)
        }

    @Test
    fun framesRoundTripThroughBothSerializers() {
        val cmds =
            listOf(
                NqlCmd("q", "SELECT ? AS a", params = listOf(1L, 2.5, "s", true, null)),
                NqlCmd("q", "SELECT 1 AS a"),
            )
        cmds.forEach { cmd ->
            val json = cmd.toJson()
            val back = Command.fromJson(json)
            assertEquals(json, back.toJson())
            val viaKotlinx = Json.encodeToString(CommandKSerializer, cmd)
            assertEquals(json, viaKotlinx)
            assertEquals(json, Json.decodeFromString(CommandKSerializer, json).toJson())
        }
        // A REAL parameter keeps a point, so the relay reads it as REAL; an INTEGER stays one.
        val parsed = Command.fromJson("""["NQL","q","SELECT ? AS a, ? AS b, ? AS c",[2.0,2,1e3]]""") as NqlCmd
        assertEquals(listOf<Any?>(2.0, 2L, 1000.0), parsed.params)
        val viaKotlinx = Json.decodeFromString(CommandKSerializer, """["NQL","q","SELECT ? AS a, ? AS b, ? AS c",[2.0,2,1e3]]""") as NqlCmd
        assertEquals(listOf<Any?>(2.0, 2L, 1000.0), viaKotlinx.params)

        val msgs =
            listOf(
                NqlResultMessage(
                    "q",
                    NqlResult(
                        listOf(NqlColumn("a", NqlType.INTEGER), NqlColumn("b", NqlType.REAL), NqlColumn("c", NqlType.TEXT), NqlColumn("d", NqlType.BOOLEAN)),
                        listOf(listOf(1L, 2.5, "x", true), listOf(null, 3.0, null, null)),
                        truncated = true,
                    ),
                ),
                NqlResultMessage("q", NqlResult(emptyList(), emptyList())),
            )
        msgs.forEach { msg ->
            val json = msg.toJson()
            assertEquals(json, Message.fromJson(json).toJson())
            val viaKotlinx = Json.encodeToString(MessageKSerializer, msg)
            assertEquals(json, viaKotlinx)
            assertEquals(json, Json.decodeFromString(MessageKSerializer, json).toJson())
        }
        // A REAL column's whole number reads back as a Double whatever the JSON spelled.
        val r = (Message.fromJson("""["NQL","q",{"columns":[["x","REAL"]],"rows":[[3]],"truncated":false}]""") as NqlResultMessage).result
        assertEquals(listOf(listOf<Any?>(3.0)), r.rows)
    }
}
