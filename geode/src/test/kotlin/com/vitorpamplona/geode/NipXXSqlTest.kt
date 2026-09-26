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
package com.vitorpamplona.geode

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.nipXXSql.NqlCmd
import com.vitorpamplona.quartz.nipXXSql.NqlResultMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
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
 * NIP-FF `NQL` over a real `ws://` connection to [KtorRelay], with the
 * frames built and parsed by quartz's command/message types.
 */
class NipXXSqlTest {
    private val alice = NostrSignerSync()

    private lateinit var dbFile: Path
    private lateinit var relay: RelayEngine
    private lateinit var server: KtorRelay
    private lateinit var ws: WebSocket
    private val frames = Channel<String>(Channel.UNLIMITED)
    private val http = OkHttpClient.Builder().build()

    @BeforeTest
    fun setup() {
        dbFile = Files.createTempFile("geode-sql-", ".db")
        Files.deleteIfExists(dbFile)
        val url = "ws://127.0.0.1:7772/".normalizeRelayUrl()
        val store = EventStore(dbName = dbFile.toAbsolutePath().toString(), relay = url)
        runBlocking {
            repeat(7) { i -> store.insert(alice.sign<Event>(1000L + i, 1, arrayOf(arrayOf("t", if (i % 2 == 0) "even" else "odd")), "note $i")) }
        }
        relay = RelayEngine(url = url, store = store)
        server = KtorRelay(relay, host = "127.0.0.1", port = 0).start()
        ws =
            http.newWebSocket(
                Request.Builder().url(server.url).build(),
                object : WebSocketListener() {
                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String,
                    ) {
                        frames.trySend(text)
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?,
                    ) {
                        frames.close(t)
                    }
                },
            )
    }

    @AfterTest
    fun teardown() {
        ws.close(1000, null)
        server.stop()
        relay.close()
        http.dispatcher.executorService.shutdown()
        listOf("", "-wal", "-shm", "-journal").forEach { Path.of(dbFile.toString() + it).deleteIfExists() }
    }

    private suspend fun next(): Message = Message.fromJson(withTimeout(10_000) { frames.receive() })

    @Test
    fun hashtagCountsThenRowsOverWebSocket() =
        runBlocking<Unit> {
            ws.send(NqlCmd("tags", "SELECT t1, count(*) AS n FROM tags WHERE t0 = 't' GROUP BY t1 ORDER BY t1").toJson())
            val counts = assertIs<NqlResultMessage>(next()).result
            assertEquals(listOf("t1", "n"), counts.columns.map { it.name })
            assertEquals(listOf(listOf("even", 4L), listOf("odd", 3L)), counts.rows)
            assertEquals(false, counts.truncated)

            ws.send(NqlCmd("notes", "SELECT content FROM events WHERE pubkey = ? ORDER BY created_at", params = listOf(alice.pubKey)).toJson())
            assertEquals((0..6).map { listOf("note $it") }, assertIs<NqlResultMessage>(next()).result.rows)
        }

    @Test
    fun badQueriesAreClosedWithAReason() =
        runBlocking<Unit> {
            ws.send(NqlCmd("bad", "SELECT * FROM event_headers").toJson())
            val closed = assertIs<ClosedMessage>(next())
            assertEquals("bad", closed.subId)
            assertTrue(closed.message.startsWith("invalid: no source named event_headers"), closed.message)
        }
}
